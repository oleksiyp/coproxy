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
    val server: Channel,
    val alloc: ByteBufAllocator
) : RequestNotifier {

    private var handlerJob: Job? = null

    private var shouldCloseClient = false
    private var shouldCloseServer = false

    private var requestStarted = false
    private var requestSent = false

    var responseStarted = false
    var responseSent = false

    val dispatcher = server.eventLoop().asCoroutineDispatcher()

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
                HttpUtil.setKeepAlive(msg, true)
                shouldCloseClient = true
            }
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
        val ctx = dispatcher + CoroutineName("coProxyHandler")
        handlerJob = launch(ctx) {
            try {
                val context = ProxyContextImpl(request, poolMap)
                context.handler()
                if (!finishOk) {
                    server.write(RuntimeException("No action"))
                }
            } catch (ex: JobCancellationException) {
                log.debug("Job request/response handler job cancelled")
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

        if (!responseSent) {

        }

        val handler = clientHandler
        clientHandler = null

        if (handler != null) {
            handler.finish(shouldCloseClient or closeClient)
        }

        server.flush()

        if (shouldCloseServer) {
            server.close()
        }
    }

    fun finishIfSent() {
        if (responseSent && requestSent) {
            val ctx = dispatcher + CoroutineName("finishJob")
            launch(ctx) {
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
            val ctx = dispatcher + CoroutineName("finishJob")
            launch(ctx) {
                finish()
            }
        }
    }

    fun clientExceptionHappened(cause: Throwable) {
        val ctx = dispatcher + CoroutineName("clientExceptionJob")
        launch(ctx) {
            if (responseStarted) {
                log.error("Client-side channel error. Response sent. Closing connections", cause)
                closeServer()
                return@launch
            }

            log.error("Client-side channel error. Sending error response", cause)

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
        val ctx = dispatcher + CoroutineName("serverExceptionJob")
        launch(ctx) {
            if (responseStarted) {
                log.error("Server-side channel error. Response sent. Closing connections", cause)
                closeServer()
                return@launch
            }

            log.error("Server-side channel error. Sending error response", cause)
            val response = errorResponse(cause)
            sendServer(response).wait()
            closeServer()
        }
    }

    private fun errorResponse(cause: Throwable): DefaultFullHttpResponse {
        val buffer = alloc.buffer()
        buffer.writeCharSequence(cause.message, ProxyServerHandler.utf8)

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
        finish(true)
        server.close()
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
        private val poolMap: ChannelPoolMap<HttpClientPoolKey, ChannelPool>
    ) : ProxyContext {
        override val decoder by lazy { QueryStringDecoder(request.uri()) }

        override suspend fun replyOk(msg: String, contentType: String) {
            val buffer = alloc.buffer()
            buffer.writeCharSequence(msg, ProxyServerHandler.utf8)

            log.debug("Sending OK response: $msg")

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

            log.debug("Done sending response: $msg")
        }

        override suspend fun forward(url: String) {
            val parser = ProxyRewriteParser(uri = URI(url))
            val poolKey = HttpClientPoolKey(parser.addr, parser.secure)
            val pool = poolMap[poolKey]
            val channel = pool.acquire().wait()

            channel.attr(ProxyHttpRequestResponse.attributeKey).set(this@ProxyHttpRequestResponse)

            if (!HttpUtil.isKeepAlive(request)) {
                HttpUtil.setKeepAlive(request, true)
                shouldCloseServer = true
            }

            request.headers().set(HttpHeaderNames.HOST, parser.hostHeader)

            log.debug("Starting proxy transfer to $url")
            become(
                HttpProxyTransferHandler(
                    channel,
                    server,
                    pool,
                    this@ProxyHttpRequestResponse
                ),
                request
            ).wait()

            log.debug("Done proxy transfer")
        }

        override suspend fun simpleHttp(request: FullHttpRequest): FullHttpResponse {
            val uri = URI(request.uri())
            val decoder = QueryStringDecoder(uri)

            request.uri = decoder.uri()

            val parser = ProxyRewriteParser(uri)
            val poolKey = HttpClientPoolKey(parser.addr, parser.secure, true)
            val pool = poolMap[poolKey]
            val channel = pool.acquire().wait()

            try {
                val responsePromise = channel.eventLoop().newPromise<FullHttpResponse>()

                with(SimpleClientHandler) {
                    channel.responsePromise = responsePromise
                }

                channel.writeAndFlush(request)
                return responsePromise.wait()
            } finally {
                pool.release(channel)
            }
        }
    }
}

var ChannelHandlerContext.requestResponseNullable: ProxyHttpRequestResponse?
    get () = channel().attr(ProxyHttpRequestResponse.attributeKey).get()
    set(value) = channel().attr(ProxyHttpRequestResponse.attributeKey).set(value)

var ChannelHandlerContext.requestResponse: ProxyHttpRequestResponse
    get () = channel().attr(ProxyHttpRequestResponse.attributeKey).get()
            ?: throw RuntimeException("bad state")
    set(value) = channel().attr(ProxyHttpRequestResponse.attributeKey).set(value)
