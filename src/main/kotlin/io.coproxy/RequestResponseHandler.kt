package io.coproxy

import io.netty.channel.ChannelFuture

abstract class RequestResponseHandler {
    abstract val finishFuture: ChannelFuture

    abstract var isClientAutoRead: Boolean

    abstract fun isClientWritable(): Boolean

    abstract fun sendClient(msg: Any): ChannelFuture

    abstract fun sendServer(msg: Any): ChannelFuture

    abstract fun flushClient()

    abstract fun flushServer()

    abstract suspend fun finish(closeClient: Boolean)
}