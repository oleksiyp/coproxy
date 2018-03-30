package io.coproxy

import io.netty.buffer.ByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.pool.ChannelPool
import io.netty.channel.pool.ChannelPoolMap
import io.netty.handler.codec.http.*
import io.netty.util.AttributeKey
import kotlinx.coroutines.experimental.*
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.*
import java.util.concurrent.TimeoutException

class ProxyHttpRequestResponse(
    val config: CoProxyConfig,
    val server: Channel,
    val alloc: ByteBufAllocator,
    idGen: ProxyIdGenerator
) : RequestNotifier {

    private var handlerJob: Job? = null

    private var shouldCloseClient = false
    private var shouldCloseServer = false

    private var requestStarted = false
    private var requestSent = false

    var responseStarted = false
    var responseSent = false

    val coroutineContext = server.eventLoop().asCoroutineDispatcher() + CoroutineName(idGen.next())

    private var clientHandler: RequestResponseHandler? = null

    companion object {
        val attributeKey =
            AttributeKey.newInstance<ProxyHttpRequestResponse>("requestResponse")
        val log = LoggerFactory.getLogger(ProxyHttpRequestResponse::class.java)
    }

    private val clientQueue = ArrayDeque<HttpObject>(2)

    private var finishOk: Boolean = false

    fun sendServer(msg: HttpObject): ChannelFuture {
        if (msg is HttpResponse) {
            responseStarted = true
            if (!HttpUtil.isKeepAlive(msg)) {
                shouldCloseClient = true
            }
            HttpUtil.setKeepAlive(msg, !shouldCloseServer)
        }

        val future = clientHandler?.sendServer(msg)
                ?: server.write(msg)

        if (msg is LastHttpContent) {
            flushServer()
            future.addListener {
                responseSent = true
                finishIfSent()
            }
        }
        return future
    }


    fun sendClient(msg: HttpObject): ChannelFuture {
        val handler = clientHandler
        return if (handler == null) {
            clientQueue.add(msg)
            server.newSucceededFuture()
        } else {
            drainClientQueue(handler)
            handler.sendClient(msg)
        }
    }

    private fun drainClientQueue(handler: RequestResponseHandler) {
        val doFlush = clientQueue.isNotEmpty()
        while (clientQueue.isNotEmpty()) {
            handler.sendClient(clientQueue.remove())
        }
        if (doFlush) {
            handler.flushClient()
        }
    }

    fun flushServer() {
        clientHandler?.flushServer()
                ?: server.flush()
    }

    fun flushClient() {
        clientHandler?.flushClient()
    }

    fun processRequest(
        request: HttpRequest,
        poolMap: ChannelPoolMap<HttpClientPoolKey, ChannelPool>,
        handler: CoProxyHandler
    ) {
        flowControl()
        handlerJob = launch(coroutineContext) {
            try {
                val context = ProxyContextImpl(request, poolMap, this)
                context.handler()
                if (!finishOk) {
                    server.write(RuntimeException("No action"))
                }
            } catch (ex: JobCancellationException) {
                log.debug("Request/response handler job cancelled")
                if (!finishOk) {
                    server.write(RuntimeException("Job canceled"))
                }
            } catch (ex: Throwable) {
                server.write(ex)
            }
        }
    }

    fun flowControl() {
        server.config().isAutoRead = clientHandler?.isClientWritable() ?: true
        clientHandler?.isClientAutoRead = server.isWritable
    }


    suspend fun finish(closeClient: Boolean = false) {
        finishOk = responseSent && requestSent
        if (!finishOk) {
            handlerJob?.cancel()
            handlerJob?.join()
        }

        val handler = clientHandler
        clientHandler = null

        if (handler != null) {
            handler.finish(shouldCloseClient or closeClient)
        }

        server.flush()

        server.attr(ProxyHttpRequestResponse.attributeKey).set(null)

        if (shouldCloseServer) {
            server.halfCloseOutput(config.halfCloseTimeoutMs).wait()
        }
    }

    fun finishIfSent() {
        if (responseSent && requestSent) {
            launch(coroutineContext) {
                finish()
            }
        }
    }

    override fun notifyRequestStarted() {
        requestStarted = true
    }

    override fun notifyRequestSent() {
        requestSent = true
        if (responseSent && requestSent) {
            launch(coroutineContext) {
                finish()
            }
        }
    }

    fun clientExceptionHappened(cause: Throwable, channel: Channel) {
        launch(coroutineContext) {
            if (responseStarted) {
                log.error(
                    "Client-side channel {} {}: {}. Response sent. Closing connections",
                    channel.id(),
                    cause::class.java.simpleName,
                    cause.message,
                    cause
                )
                closeServer()
                return@launch
            }

            log.error(
                "Client-side channel {} {}: {}. Sending error response",
                channel.id(),
                cause::class.java.simpleName,
                cause.message,
                cause
            )

            val response = errorResponse(cause)
            sendServer(response).wait()
            flushServer()

            // try recover from error without closing connections
            val fakeRequest =
                if (requestStarted)
                    null
                else
                    DefaultFullHttpRequest(
                        HttpVersion.HTTP_1_1,
                        HttpMethod.GET,
                        ""
                    )

            become(
                SkipHttpHandler(server, this@ProxyHttpRequestResponse),
                fakeRequest
            )
        }
    }

    fun serverExceptionHappened(cause: Throwable) {
        launch(coroutineContext) {
            if (responseStarted) {
                log.error(
                    "Server-side channel {} {}: {}. Response sent. Closing connections",
                    server.id(),
                    cause::class.java.simpleName,
                    cause.message,
                    cause
                )
                closeServer()
                return@launch
            }

            log.error(
                "Server-side channel {} {}: {}. Sending error response",
                server.id(),
                cause::class.java.simpleName,
                cause.message,
                cause
            )
            val response = errorResponse(cause)
            shouldCloseServer = true
            sendServer(response).wait()
            closeServer()
        }
    }

    private fun errorResponse(cause: Throwable): DefaultFullHttpResponse {
        val buffer = alloc.buffer()
        buffer.writeCharSequence(cause.message, ServerProxyHandler.utf8)

        val code = when (cause) {
            is TimeoutException -> HttpResponseStatus.GATEWAY_TIMEOUT
            else -> HttpResponseStatus.INTERNAL_SERVER_ERROR
        }

        val response = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            code,
            buffer
        )

        response.headers().set(
            HttpHeaderNames.CONTENT_LENGTH,
            buffer.writerIndex() - buffer.readerIndex()
        )
        response.headers().set(
            HttpHeaderNames.CONTENT_TYPE,
            "text/plain; charset=utf-8"
        )
        return response
    }


    suspend fun closeServer() {
        shouldCloseServer = true
        finish(true)
    }

    fun canHandleNextRequest() = finishOk && !shouldCloseServer

    private fun become(
        handler: RequestResponseHandler,
        request: HttpRequest? = null
    ): ChannelFuture {
        clientHandler = handler

        flowControl()

        request?.let { handler.sendClient(it) }
        handler.flushClient()

        drainClientQueue(handler)

        return handler.finishFuture
    }


    inner class ProxyContextImpl(
        override val request: HttpRequest,
        private val poolMap: ChannelPoolMap<HttpClientPoolKey, ChannelPool>,
        private val scope: CoroutineScope
    ) : ProxyContext {
        override suspend fun location(
            prefix: String,
            regex: String,
            block: suspend LocationContext.() -> Unit
        ) {
            LocationContext(request.uri(), arrayOf()).location(prefix, regex, block);
        }

        override val decoder by lazy { QueryStringDecoder(request.uri()) }

        override suspend fun replyOk(msg: String, contentType: String) {
            val buffer = alloc.buffer()
            buffer.writeCharSequence(msg, ServerProxyHandler.utf8)

            log.debug("--> OK $msg START")

            val response = DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                buffer
            )

            response.headers().set(
                HttpHeaderNames.CONTENT_LENGTH,
                buffer.writerIndex() - buffer.readerIndex()
            )
            response.headers().set(
                HttpHeaderNames.CONTENT_TYPE,
                "$contentType; charset=utf-8"
            )
            sendServer(response)

            become(
                SkipHttpHandler(server, this@ProxyHttpRequestResponse),
                request
            ).wait()

            log.debug("--> OK $msg END")
        }

        override suspend fun forward(url: String) {
            val parser = ProxyRewriteParser(uri = URI(url))
            val poolKey = HttpClientPoolKey(parser.addr, parser.secure)
            val pool = poolMap[poolKey]
            val channel = pool.acquire().wait()

            channel.attr(ProxyHttpRequestResponse.attributeKey).set(this@ProxyHttpRequestResponse)

            if (!HttpUtil.isKeepAlive(request)) {
                shouldCloseServer = true
            }
            HttpUtil.setKeepAlive(request, !shouldCloseClient)

            request.headers().set(HttpHeaderNames.HOST, parser.hostHeader)
            request.uri = parser.queryString

            log.info("==> $url START")
            try {
                become(
                    HttpProxyTransferHandler(
                        channel,
                        server,
                        pool,
                        this@ProxyHttpRequestResponse,
                        config
                    ),
                    request
                ).wait()
            } finally {
                log.info("==> $url END")
            }
        }

        override suspend fun simpleHttp(request: FullHttpRequest): FullHttpResponse {
            val uri = URI(request.uri())
            val decoder = QueryStringDecoder(uri)

            request.uri = decoder.uri()

            log.info("??? ${request.method()} ${request.uri()} START")

            val parser = ProxyRewriteParser(uri)
            val poolKey = HttpClientPoolKey(parser.addr, parser.secure, true)
            val pool = poolMap[poolKey]
            val channel = pool.acquire().wait()

            var status = "-"
            try {
                val responsePromise = channel.eventLoop().newPromise<FullHttpResponse>()

                with(SimpleClientHandler) {
                    channel.responsePromise = responsePromise
                }

                channel.writeAndFlush(request)
                val response = responsePromise.wait()
                status = response.status().toString()
                return response
            } catch (ex: Throwable) {
                channel.halfCloseOutput(config.halfCloseTimeoutMs).wait()
                log.info("??? {} CLOSE", ex::class.java.simpleName)
                throw ex
            } finally {
                log.info("??? $status END")
                pool.release(channel)
            }
        }

        override val coroutineContext = scope.coroutineContext

        override val context = scope.coroutineContext

        override val job: Job
            get() = coroutineContext[Job]!!

        override val isActive
            get() = scope.isActive
    }
}

var ChannelHandlerContext.requestResponseNullable: ProxyHttpRequestResponse?
    get () = channel().attr(ProxyHttpRequestResponse.attributeKey).get()
    set(value) = channel().attr(ProxyHttpRequestResponse.attributeKey).set(value)

var ChannelHandlerContext.requestResponse: ProxyHttpRequestResponse
    get () = channel().attr(ProxyHttpRequestResponse.attributeKey).get()
            ?: throw RuntimeException("bad state")
    set(value) = channel().attr(ProxyHttpRequestResponse.attributeKey).set(value)

