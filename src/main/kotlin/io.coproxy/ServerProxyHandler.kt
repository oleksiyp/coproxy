package io.coproxy

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.timeout.IdleStateEvent
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.experimental.launch
import org.slf4j.LoggerFactory
import java.nio.charset.Charset
import java.util.concurrent.TimeoutException

class ServerProxyHandler(
    val config: CoProxyConfig,
    private val handler: CoProxyHandler,
    private val idGen: ProxyIdGenerator
) : SimpleChannelInboundHandler<HttpObject>() {

    override fun channelUnregistered(ctx: ChannelHandlerContext) {
        val reqResponse = ctx.requestResponseNullable ?: return
        ctx.requestResponseNullable = null
        launch(reqResponse.coroutineContext) {
            reqResponse.closeServer()
        }
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
        if (msg is HttpRequest) {
            if (!canHandleNextRequest(ctx)) {
                ctx.requestResponse.serverExceptionHappened(
                    RuntimeException(
                        "Got one more request without finishing previous. " +
                                "HTTP pipelining is not supported and generally considered a bad practice." +
                                "Most Browsers disable this mode"
                    )
                )
                return
            }

            val client = ctx.channel().attr(HttpClient.attributeKey).get()
            val newReqResp = ProxyHttpRequestResponse(config, ctx.channel(), ctx.alloc(), idGen)
            ctx.requestResponse = newReqResp

            ReferenceCountUtil.retain(msg)
            newReqResp.processRequest(msg, client.poolMap, handler)
        } else {
            ctx.requestResponse.sendClient(msg)
        }
    }

    private fun canHandleNextRequest(
        ctx: ChannelHandlerContext
    ): Boolean {
        val reqResp = ctx.requestResponseNullable ?: return true

        return reqResp.canHandleNextRequest()
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        ctx.requestResponse.flowControl()
        super.channelWritabilityChanged(ctx)
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.requestResponseNullable?.flushClient()
        super.channelReadComplete(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        val requestResponse = ctx.requestResponseNullable
        if (requestResponse == null) {
            log.error("Server-side channel error. Bad state", cause)
        } else {
            requestResponse.serverExceptionHappened(cause)
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is IdleStateEvent) {
            throw TimeoutException("Channel idle more than timeout")
        }
        super.userEventTriggered(ctx, evt)
    }

    companion object {
        val log = LoggerFactory.getLogger(ServerProxyHandler::class.java)

        val utf8 = Charset.forName("UTF-8")
    }
}