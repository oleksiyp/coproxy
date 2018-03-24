package io.coproxy

import java.net.InetSocketAddress
import java.net.URI

class ProxyRewriteParser(val uri: URI) {
    val parser = HttpURLParser(uri)
    val port = parser.port
    val addr = InetSocketAddress(parser.host, port)
    val secure = uri.scheme.equals("https", ignoreCase = true)

    override fun toString() = uri.toString()
}