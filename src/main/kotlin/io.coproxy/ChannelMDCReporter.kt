package io.coproxy

import io.netty.channel.Channel
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import org.slf4j.MDC
import java.net.SocketAddress

class ChannelMDCReporter(val key: String) : ChannelDuplexHandler() {
    override fun channelInactive(ctx: ChannelHandlerContext) {
        withMDC(ctx.channel()) {
            super.channelInactive(ctx)
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any?) {
        withMDC(ctx.channel()) {
            super.userEventTriggered(ctx, evt)
        }
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any?, promise: ChannelPromise?) {
        withMDC(ctx.channel()) {
            super.write(ctx, msg, promise)
        }
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any?) {
        withMDC(ctx.channel()) {
            super.channelRead(ctx, msg)
        }
    }

    override fun flush(ctx: ChannelHandlerContext) {
        withMDC(ctx.channel()) {
            super.flush(ctx)
        }
    }

    override fun connect(
        ctx: ChannelHandlerContext,
        remoteAddress: SocketAddress?,
        localAddress: SocketAddress?,
        promise: ChannelPromise?
    ) {
        withMDC(ctx.channel()) {
            super.connect(ctx, remoteAddress, localAddress, promise)
        }
    }

    override fun channelUnregistered(ctx: ChannelHandlerContext) {
        withMDC(ctx.channel()) {
            super.channelUnregistered(ctx)
        }
    }

    override fun channelRegistered(ctx: ChannelHandlerContext) {
        withMDC(ctx.channel()) {
            super.channelRegistered(ctx)
        }
    }

    override fun close(ctx: ChannelHandlerContext, promise: ChannelPromise?) {
        withMDC(ctx.channel()) {
            super.close(ctx, promise)
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
        withMDC(ctx.channel()) {
            super.exceptionCaught(ctx, cause)
        }
    }

    override fun read(ctx: ChannelHandlerContext) {
        withMDC(ctx.channel()) {
            super.read(ctx)
        }
    }

    override fun deregister(ctx: ChannelHandlerContext, promise: ChannelPromise?) {
        withMDC(ctx.channel()) {
            super.deregister(ctx, promise)
        }
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        withMDC(ctx.channel()) {
            super.channelWritabilityChanged(ctx)
        }
    }

    override fun disconnect(ctx: ChannelHandlerContext, promise: ChannelPromise?) {
        withMDC(ctx.channel()) {
            super.disconnect(ctx, promise)
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        withMDC(ctx.channel()) {
            super.channelActive(ctx)
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        withMDC(ctx.channel()) {
            super.channelReadComplete(ctx)
        }
    }

    override fun bind(ctx: ChannelHandlerContext, localAddress: SocketAddress?, promise: ChannelPromise?) {
        withMDC(ctx.channel()) {
            super.bind(ctx, localAddress, promise)
        }
    }

    private fun withMDC(channel: Channel, block: () -> Unit) {
        MDC.put(key, channel.id().toString())
        try {
            block()
        } finally {
            MDC.remove(key)
        }
    }
}