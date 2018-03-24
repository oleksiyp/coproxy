package io.coproxy

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.util.concurrent.DefaultThreadFactory
import io.netty.util.concurrent.Future
import kotlinx.coroutines.experimental.*
import org.slf4j.LoggerFactory

typealias CoProxyHandler = suspend ProxyContext.() -> Unit

class CoProxy(
    val config: CoProxyConfig = CoProxyConfig(),
    val handler: CoProxyHandler
) {
    private val serverGroup = config.eventLoopGroup(config.selectorThreads, DefaultThreadFactory("selector"))
    private val serverWorkerGroup = config.eventLoopGroup(config.serverThreads, DefaultThreadFactory("worker"))
    private val sslCtx: SslContext? = configureSSL()
    private val clientSslCtx = configureClientSSL()
    private val clientInitializer = HttpClientAttributeInitializer(clientSslCtx, config)
    private val serverBootstrap = createServerBootstrap()

    fun listen(): Channel = serverBootstrap
        .bind(config.port)
        .addListener { log.info("Listening ${config.port} ${if (config.ssl) "https" else "http"}") }
        .sync()
        .channel()

    fun stop() {
        serverGroup.shutdownGracefully()
        serverWorkerGroup.shutdownGracefully()
    }

    private fun configureClientSSL(): SslContext {
        return SslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE).build()
    }


    private fun configureSSL(): SslContext? {
        if (!config.ssl) {
            return null
        }
        val ssc = SelfSignedCertificate()
        return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build()
    }

    private fun createServerBootstrap(): ServerBootstrap {
        val bootstrap = ServerBootstrap()
        bootstrap.option(ChannelOption.SO_BACKLOG, 1024)
        bootstrap.childOption(ChannelOption.TCP_NODELAY, true)
        bootstrap.group(serverGroup, serverWorkerGroup)
            .channel(config.serverSocketChannel())
            .childHandler(ProxyServerChannelInitializer(clientInitializer, sslCtx, config, handler))
        return bootstrap
    }

    companion object {
        val log = LoggerFactory.getLogger(CoProxyConfig::class.java)
    }
}


