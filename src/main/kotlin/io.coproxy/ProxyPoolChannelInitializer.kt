package io.coproxy

import io.netty.channel.Channel
import io.netty.channel.pool.AbstractChannelPoolHandler
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpContentDecompressor
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.timeout.IdleStateHandler
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class ProxyPoolChannelInitializer(
    val poolKey: HttpClientPoolKey,
    val config: CoProxyConfig
) : AbstractChannelPoolHandler() {

    override fun channelAcquired(ch: Channel) {
        log.info("Acquired {}", ch.id())
    }

    override fun channelReleased(ch: Channel) {
        log.info("Released {}", ch.id())
    }

    override fun channelCreated(ch: Channel) {
        log.info("New channel {}", ch.id())
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
        config.trafficLogging?.let { p.addLast(LoggingHandler(it)) }
        if (poolKey.simpleHttp) {
            p.addLast(HttpObjectAggregator(config.simpleHttpMaxContentLength))
            p.addLast(SimpleClientHandler())
        } else {
            p.addLast(ProxyClientHandler())
        }
    }

    companion object {
        val log = LoggerFactory.getLogger(ProxyPoolChannelInitializer::class.java)
    }
}