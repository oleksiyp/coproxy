package io.coproxy

import io.netty.channel.Channel
import io.netty.channel.pool.AbstractChannelPoolHandler
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpContentDecompressor
import io.netty.handler.codec.http.HttpObjectAggregator
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

        p.addLast(ChannelMDCReporter("clientChannel"))
        if (poolKey.ssl) {
            val sslCtx = ch.attr(HttpClient.sslKeyAttribute).get()
            p.addLast(sslCtx.newHandler(ch.alloc()))
        }

        config.trafficLogging?.let { p.addLast(LoggingHandler(it)) }
        p.addLast(HttpClientCodec())
        p.addLast(HttpContentDecompressor())
        p.addLast(WritableExceptionHandler())
        if (poolKey.simpleHttp) {
            p.addLast(
                IdleStateHandler(
                    0,
                    0,
                    config.simpleHttpIdleTimeoutMs,
                    TimeUnit.MILLISECONDS
                )
            )
            p.addLast(HttpObjectAggregator(config.simpleHttpMaxContentLength))
            p.addLast(SimpleClientHandler())
        } else {
            p.addLast(
                IdleStateHandler(
                    0,
                    0,
                    config.clientIdleTimeoutMs,
                    TimeUnit.MILLISECONDS
                )
            )
            p.addLast(ClientProxyHandler(config))
        }
    }

    companion object {
        val log = LoggerFactory.getLogger(ProxyPoolChannelInitializer::class.java)
    }
}