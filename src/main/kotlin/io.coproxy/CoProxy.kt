package io.coproxy

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelOption
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslProvider
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.util.concurrent.DefaultThreadFactory
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

typealias CoProxyHandler = suspend ProxyContext.() -> Unit

class CoProxy(
    val config: CoProxyConfig = CoProxyConfig(trafficLogging = null),
    val handler: CoProxyHandler
) {
    private val serverGroup = config.eventLoopGroup(
        config.selectorThreads,
        DefaultThreadFactory("selector:" + config.port)
    )
    private val serverWorkerGroup = config.eventLoopGroup(
        config.serverThreads,
        DefaultThreadFactory("worker:" + config.port)
    )

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
            .sync()
        serverWorkerGroup.shutdownGracefully()
            .sync()
    }

    fun stopFast() {
        listOf(
            serverGroup.shutdownGracefully(100, 500, TimeUnit.MILLISECONDS),
            serverWorkerGroup.shutdownGracefully(100, 500, TimeUnit.MILLISECONDS)
        ).forEach { it.sync() }
    }

    private fun configureClientSSL(): SslContext {
        return SslContextBuilder.forClient()
            .sslProvider(SslProvider.OPENSSL)
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .build()
    }


    private fun configureSSL(): SslContext? {
        if (!config.ssl) {
            return null
        }
        val ssc = SelfSignedCertificate()
        return SslContextBuilder
            .forServer(ssc.certificate(), ssc.privateKey())
            .sslProvider(SslProvider.OPENSSL)
            .build()
    }

    private fun createServerBootstrap(): ServerBootstrap {
        val bootstrap = ServerBootstrap()
        bootstrap.option(ChannelOption.SO_BACKLOG, 1024)
        bootstrap.childOption(ChannelOption.TCP_NODELAY, true)
        bootstrap.group(serverGroup, serverWorkerGroup)
            .channel(config.serverSocketChannel())
            .childHandler(ServerProxyChannelInitializer(clientInitializer, sslCtx, config, handler, idGen))
        return bootstrap
    }

    companion object {
        private val idGen = ProxyIdGenerator()
        val log = LoggerFactory.getLogger(CoProxyConfig::class.java)
    }
}


