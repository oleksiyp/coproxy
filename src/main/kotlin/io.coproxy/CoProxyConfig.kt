package io.coproxy

import io.netty.channel.ServerChannel
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.epoll.EpollSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import java.util.concurrent.ThreadFactory

data class CoProxyConfig(
    val port: Int = 8080,
    val ssl: Boolean = false,
    val serverThreads: Int = 8,
    val selectorThreads: Int = 1,
    val connectionsPerDestintation: Int = 2,
    val accquireTimeoutMs: Long = 1000,
    val maxPendingAcquires: Int = 2,
    val lruPool: Boolean = true,
    val epoll: Boolean = Epoll.isAvailable(),
    val serverIdleTimeoutMs: Long = 100000,
    val clientIdleTimeoutMs: Long = 10000,
    val simpleHttpMaxContentLength: Int = 100 * 1024
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

