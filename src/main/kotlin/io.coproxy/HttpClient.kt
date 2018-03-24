package io.coproxy

import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoop
import io.netty.channel.pool.AbstractChannelPoolMap
import io.netty.channel.pool.ChannelPool
import io.netty.channel.pool.SimpleChannelPool
import io.netty.handler.ssl.SslContext
import io.netty.util.AbstractReferenceCounted
import io.netty.util.AttributeKey
import org.slf4j.LoggerFactory

class HttpClient(
    private val eventLoop: EventLoop,
    private val sslCtx: SslContext,
    private val config: CoProxyConfig
) : AbstractReferenceCounted() {

    private val clientBootstrap: Bootstrap = createClientBootstrap()
    val poolMap = createPoolMap()

    private fun createClientBootstrap(): Bootstrap {
        log.info("Creating client bootstrap")
        val bootstrap = Bootstrap()
        bootstrap.attr(sslKeyAttribute, sslCtx)
        bootstrap.option(ChannelOption.TCP_NODELAY, true)

        bootstrap
            .group(eventLoop)
            .channel(config.socketChannel())

        return bootstrap
    }

    private fun createPoolMap(): AbstractChannelPoolMap<HttpClientPoolKey, ChannelPool> {
        return object : AbstractChannelPoolMap<HttpClientPoolKey, ChannelPool>() {
            override fun newPool(poolKey: HttpClientPoolKey): SimpleChannelPool {
                return HttpClientPool(
                    clientBootstrap,
                    config,
                    poolKey.address,
                    ProxyPoolChannelInitializer(poolKey, config)
                )
            }
        }
    }

    override fun touch(hint: Any) = this

    override fun deallocate() {
        log.info("Shutting down client pool")
        poolMap.forEach { it.value.close() }
    }

    companion object {
        val attributeKey = AttributeKey.newInstance<HttpClient>("client")
        val sslKeyAttribute =
            AttributeKey.newInstance<SslContext>("clientSslContext")
        val log = LoggerFactory.getLogger(HttpClientAttributeInitializer::class.java)
    }

}