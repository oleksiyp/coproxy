package io.coproxy

import io.netty.bootstrap.Bootstrap
import io.netty.channel.pool.ChannelHealthChecker
import io.netty.channel.pool.ChannelPoolHandler
import io.netty.channel.pool.FixedChannelPool
import java.net.InetSocketAddress

class HttpClientPool(
    bootstrap: Bootstrap,
    config: CoProxyConfig,
    val address: InetSocketAddress,
    poolHandler: ChannelPoolHandler
) :
    FixedChannelPool(
        bootstrap,
        poolHandler,
        ChannelHealthChecker.ACTIVE,
        AcquireTimeoutAction.FAIL,
        config.accquireTimeoutMs,
        config.connectionsPerDestintation,
        config.maxPendingAcquires,
        true,
        config.lruPool
    ) {

    override fun connectChannel(bs: Bootstrap) = bs.connect(address)
}