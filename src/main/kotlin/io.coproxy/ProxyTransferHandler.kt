package io.coproxy

import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.pool.ChannelPool

abstract class ProxyTransferHandler(
    val client: Channel,
    val server: Channel,
    val clientPool: ChannelPool? = null
) : RequestResponseHandler() {

    override val finishFuture = client.newPromise()

    override var isClientAutoRead: Boolean
        get() = client.config().isAutoRead
        set(value) {
            client.config().isAutoRead = value
        }

    override fun isClientWritable() = client.isWritable

    override fun sendClient(msg: Any) = client.write(msg)

    override fun sendServer(msg: Any): ChannelFuture {
        return server.write(msg)
    }

    override fun flushClient() {
        client.flush()
    }

    override fun flushServer() {
        server.flush()
    }

}