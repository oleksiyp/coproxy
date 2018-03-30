package io.coproxy

import java.net.InetSocketAddress
import java.net.URI

class ProxyRewriteParser(val uri: URI) {
    val parser = HttpURLParser(uri)
    val port = parser.port
    val addr = InetSocketAddress(parser.host, port)
    val secure = uri.scheme.equals("https", ignoreCase = true)
    val hostHeader = parser.host + portIfNeeded()
    val queryString = queryString()


    private fun defaultPort(): Int = if (secure) 443 else 80
    private fun portIfNeeded(): String = if (defaultPort() != port) ":$port" else ""
    private fun queryString(): String? {
        val sb = StringBuilder()
        if (uri.rawPath == null) {
            return null
        }
        sb.append(uri.rawPath)
        if (uri.rawQuery != null) {
            sb.append('?')
            sb.append(uri.rawQuery)
        }
        if (uri.rawFragment != null) {
            sb.append('#')
            sb.append(uri.rawFragment)
        }
        return sb.toString()
    }


    override fun toString() = uri.toString()
}