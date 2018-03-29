package io.coproxy

import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.pool.ChannelPool
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.LastHttpContent

class HttpProxyTransferHandler(
    client: Channel,
    server: Channel,
    clientPool: ChannelPool? = null,
    val requestNotifier: RequestNotifier? = null,
    val config: CoProxyConfig
) : ProxyTransferHandler(client, server, clientPool) {

    override fun sendClient(msg: Any): ChannelFuture {
        if (msg is HttpRequest) {
            requestNotifier?.notifyRequestStarted()
        }

        val future = super.sendClient(msg)

        if (msg is LastHttpContent) {
            future.addListener {
                if (!it.isSuccess) {
                    throw it.cause()
                }
                requestNotifier?.notifyRequestSent()
            }
        }
        return future
    }

    override suspend fun finish(closeClient: Boolean) {
        client.config().isAutoRead = true
        client.attr(ProxyHttpRequestResponse.attributeKey).set(null)
        if (closeClient) {
            client.halfCloseOutput(config.halfCloseTimeoutMs).wait()
        }
        clientPool?.release(client)?.wait()
        finishFuture.setSuccess()
    }
}