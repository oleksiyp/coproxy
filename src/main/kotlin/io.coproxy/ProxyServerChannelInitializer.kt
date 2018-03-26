package io.coproxy

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpServerExpectContinueHandler
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslContext
import io.netty.handler.timeout.IdleStateHandler
import java.util.concurrent.TimeUnit

class ProxyServerChannelInitializer(
    val clientAttributeInitializer: HttpClientAttributeInitializer,
    val sslCtx: SslContext?,
    val config: CoProxyConfig,
    val handler: CoProxyHandler,
    val idGen: ProxyIdGenerator
) : ChannelInitializer<SocketChannel>() {

    public override fun initChannel(ch: SocketChannel) {
        val p = ch.pipeline()
        p.addLast(clientAttributeInitializer)
        sslCtx?.let { p.addLast(it.newHandler(ch.alloc())) }
        p.addLast(HttpServerCodec())
        p.addLast(HttpServerExpectContinueHandler())
        p.addLast(WritableExceptionHandler())
        p.addLast(
            IdleStateHandler(
                0,
                0,
                config.serverIdleTimeoutMs,
                TimeUnit.MILLISECONDS
            )
        )
        config.trafficLogging?.let { p.addLast(LoggingHandler(it)) }
        p.addLast(ProxyServerHandler(handler, idGen))
    }
}