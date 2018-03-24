package io.coproxy

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.timeout.IdleStateEvent
import io.netty.util.AttributeKey
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.Promise

class SimpleClientHandler : SimpleChannelInboundHandler<FullHttpResponse>() {
    override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse) {
        ReferenceCountUtil.retain(msg)
        ctx.channel().responsePromise.setSuccess(msg)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.channel().responsePromise.setFailure(cause)
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is IdleStateEvent) {
            throw TimeoutException("Channel idle more than timeout")
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

    }
}