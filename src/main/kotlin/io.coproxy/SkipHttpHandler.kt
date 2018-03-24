package io.coproxy

import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.LastHttpContent

class SkipHttpHandler(val server: Channel, val notifier: RequestNotifier) : RequestResponseHandler() {
    override val finishFuture = server.newPromise()

    override var isClientAutoRead: Boolean
        get() = true
        set(value) {}

    override fun isClientWritable() = true

    override fun sendClient(msg: Any): ChannelFuture {
        if (msg is HttpRequest) {
            notifier.notifyRequestStarted()
        }
        if (msg is LastHttpContent) {
            notifier.notifyRequestSent()
        }

        return server.newSucceededFuture()
    }

    override fun sendServer(msg: Any): ChannelFuture {
        return server.newSucceededFuture()
    }

    override fun flushClient() {}

    override fun flushServer() {}

    override suspend fun finish(closeClient: Boolean) {
        finishFuture.setSuccess()
    }
}