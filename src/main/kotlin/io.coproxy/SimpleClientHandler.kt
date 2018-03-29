package io.coproxy

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.internal.ChannelUtils
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.timeout.IdleStateEvent
import io.netty.util.AttributeKey
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.Promise
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeoutException

class SimpleClientHandler : SimpleChannelInboundHandler<FullHttpResponse>() {
    override fun channelInactive(ctx: ChannelHandlerContext?) {
        super.channelInactive(ctx)
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse) {
        ReferenceCountUtil.retain(msg)
        val responsePromise = ctx.channel().responsePromise
        ctx.channel().responsePromiseNullable = null
        responsePromise.setSuccess(msg)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        val promise = ctx.channel().responsePromiseNullable
        if (promise != null) {
            promise.setFailure(cause)
        } else {
            log.error("Simple client error. No promise attached. Just logging", cause)
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is IdleStateEvent) {
            if (ctx.channel().responsePromiseNullable != null) {
                throw TimeoutException("Channel idle more than timeout")
            }
        }
        super.userEventTriggered(ctx, evt)
    }

    companion object {
        val attributeKey =
            AttributeKey.newInstance<Promise<FullHttpResponse>>(
                "responsePromise"
            )

        var Channel.responsePromise: Promise<FullHttpResponse>
            get () = attr(attributeKey).get()
                    ?: throw RuntimeException("bad state")
            set(value) = attr(attributeKey).set(value)

        var Channel.responsePromiseNullable: Promise<FullHttpResponse>?
            get () = attr(attributeKey).get()
            set(value) = attr(attributeKey).set(value)

        val log = LoggerFactory.getLogger(SimpleClientHandler::class.java)
    }
}