package io.coproxy

import java.net.InetSocketAddress

data class HttpClientPoolKey(
    val address: InetSocketAddress,
    val ssl: Boolean,
    val simpleHttp: Boolean = false
)