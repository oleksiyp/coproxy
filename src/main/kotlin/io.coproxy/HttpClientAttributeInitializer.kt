package io.coproxy

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.EventLoop
import io.netty.handler.ssl.SslContext
import java.util.HashMap

@ChannelHandler.Sharable
class HttpClientAttributeInitializer(
    val sslCtx: SslContext,
    val clientConfig: CoProxyConfig
) : ChannelInboundHandlerAdapter() {
    val clients = HashMap<EventLoop, HttpClient>()

    override fun channelRegistered(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()
        val eventLoop = channel.eventLoop()
        val client = synchronized(clients) { getOrCreateClient(eventLoop) }
        channel.attr(HttpClient.attributeKey).set(client)
        super.channelRegistered(ctx)
    }

    private fun getOrCreateClient(eventLoop: EventLoop): HttpClient? {
        var client = clients[eventLoop]
        if (client == null) {
            client = HttpClient(eventLoop, sslCtx, clientConfig)
            clients[eventLoop] = client
        } else {
            client.retain()
        }
        return client
    }

    override fun channelUnregistered(ctx: ChannelHandlerContext) {
        super.channelUnregistered(ctx)
        val client = ctx.channel().attr(HttpClient.attributeKey).getAndSet(null)
        synchronized(clients) {
            if (client.release()) {
                clients.remove(ctx.channel().eventLoop())
            }
        }
    }
}