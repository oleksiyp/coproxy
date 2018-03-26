package io.coproxy

import java.net.InetSocketAddress
import java.net.URI

class ProxyRewriteParser(val uri: URI) {
    val parser = HttpURLParser(uri)
    val port = parser.port
    val addr = InetSocketAddress(parser.host, port)
    val secure = uri.scheme.equals("https", ignoreCase = true)
    val hostHeader = parser.host + portIfNeeded()

    private fun defaultPort(): Int = if (secure) 443 else 80
    private fun portIfNeeded(): String = if (defaultPort() != port) ":$port" else ""
    override fun toString() = uri.toString()
}