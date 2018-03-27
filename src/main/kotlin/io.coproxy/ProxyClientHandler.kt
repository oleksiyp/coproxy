package io.coproxy

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.timeout.IdleStateEvent
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.experimental.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeoutException

class ProxyClientHandler : SimpleChannelInboundHandler<HttpObject>() {
    override fun channelInactive(ctx: ChannelHandlerContext) {
        val reqResponse = ctx.requestResponseNullable ?: return
        ctx.requestResponseNullable = null
        launch(reqResponse.coroutineContext) {
            if (!reqResponse.responseStarted) {
                reqResponse.clientExceptionHappened(RuntimeException("Channel unexpectedly closed"), ctx.channel())
            } else {
                if (!reqResponse.responseSent) {
                    log.error(
                        "Client-side channel {} unexpectedly closed. Response partly sent",
                        ctx.channel().id()
                    )
                }
                reqResponse.finish()
            }
        }
    }

    public override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
        ReferenceCountUtil.retain(msg)
        ctx.requestResponse.sendServer(msg)
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        ctx.requestResponse.flowControl()
        super.channelWritabilityChanged(ctx)
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.requestResponseNullable?.flushServer()
        super.channelReadComplete(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        val requestResponse = ctx.requestResponseNullable
        if (requestResponse == null) {
            log.error(
                "Client-side channel {} {}: {}. Bad state",
                ctx.channel().id(),
                cause::class.java.simpleName,
                cause.message,
                cause
            )
        } else {
            requestResponse.clientExceptionHappened(cause, ctx.channel())
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is IdleStateEvent) {
            val requestResponse = ctx.requestResponseNullable
            if (requestResponse != null) {
                throw TimeoutException("Channel idle more than timeout")
            }
        }
        super.userEventTriggered(ctx, evt)
    }

    companion object {
        val log = LoggerFactory.getLogger(ProxyClientHandler::class.java)
    }
}