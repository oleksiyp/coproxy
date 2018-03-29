package io.coproxy

import io.netty.channel.ServerChannel
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.epoll.EpollSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.logging.LogLevel
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS

data class CoProxyConfig(
    val port: Int = 8080,
    val ssl: Boolean = false,
    val serverThreads: Int = 8,
    val selectorThreads: Int = 1,
    val connectionsPerDestintation: Int = 2000,
    val accquireTimeoutMs: Long = SECONDS.toMillis(30),
    val maxPendingAcquires: Int = 2000,
    val lruPool: Boolean = true,
    val epoll: Boolean = Epoll.isAvailable() and false,
    val serverIdleTimeoutMs: Long = MINUTES.toMillis(5),
    val clientIdleTimeoutMs: Long = SECONDS.toMillis(30),
    val simpleHttpIdleTimeoutMs: Long = SECONDS.toMillis(45),
    val simpleHttpMaxContentLength: Int = 100 * 1024,
    val trafficLogging: LogLevel? = null,
    val halfCloseTimeoutMs: Long = SECONDS.toMillis(3)
)

fun CoProxyConfig.eventLoopGroup(n: Int, threadFactory: ThreadFactory) =
    if (epoll)
        EpollEventLoopGroup(n, threadFactory)
    else
        NioEventLoopGroup(n, threadFactory)

fun CoProxyConfig.serverSocketChannel(): Class<out ServerChannel> =
    if (epoll)
        EpollServerSocketChannel::class.java
    else
        NioServerSocketChannel::class.java

fun CoProxyConfig.socketChannel() =
    if (epoll)
        EpollSocketChannel::class.java
    else
        NioSocketChannel::class.java

