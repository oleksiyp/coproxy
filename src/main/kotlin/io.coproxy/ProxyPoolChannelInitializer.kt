package io.coproxy

import io.netty.channel.Channel
import io.netty.channel.pool.AbstractChannelPoolHandler
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpContentDecompressor
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.timeout.IdleStateHandler
import java.util.concurrent.TimeUnit

class ProxyPoolChannelInitializer(
    val poolKey: HttpClientPoolKey,
    val config: CoProxyConfig
) : AbstractChannelPoolHandler() {

    override fun channelCreated(ch: Channel) {
        val p = ch.pipeline()
        if (poolKey.ssl) {
            val sslCtx = ch.attr(HttpClient.sslKeyAttribute).get()
            p.addLast(sslCtx.newHandler(ch.alloc()))
        }

        p.addLast(HttpClientCodec())
        p.addLast(HttpContentDecompressor())
        p.addLast(
            IdleStateHandler(
                0,
                0,
                config.clientIdleTimeoutMs,
                TimeUnit.MILLISECONDS
            )
        )
        p.addLast(WritableExceptionHandler())
        if (poolKey.simpleHttp) {
            p.addLast(HttpObjectAggregator(config.simpleHttpMaxContentLength))
            p.addLast(SimpleClientHandler())
        } else {
            p.addLast(ProxyClientHandler())
        }
    }
}