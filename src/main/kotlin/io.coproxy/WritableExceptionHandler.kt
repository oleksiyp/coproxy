package io.coproxy

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise

class WritableExceptionHandler : ChannelOutboundHandlerAdapter() {
    override fun write(ctx: ChannelHandlerContext, msg: Any?, promise: ChannelPromise) {
        if (msg is Throwable) {
            ctx.fireExceptionCaught(msg)
            promise.setSuccess(null)
        } else {
            super.write(ctx, msg, promise)
        }
    }
}