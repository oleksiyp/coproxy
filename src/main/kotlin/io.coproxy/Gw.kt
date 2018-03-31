package io.coproxy

import io.netty.handler.codec.http.HttpHeaderNames
import org.xbill.DNS.ARecord
import org.xbill.DNS.Lookup
import org.xbill.DNS.SimpleResolver

fun main(args: Array<String>) {
    val resolver = SimpleResolver("8.8.8.8")

    fun ProxyContext.resolve(resolver: SimpleResolver, ssl: Boolean): Pair<String, String> {
        val hostAndPort = request.headers().get(HttpHeaderNames.HOST)

        val parser = HostHeaderParser(hostAndPort ?: "", ssl)

        val host = parser.host
        val port = parser.port

        val lookup = Lookup(host)
        lookup.setResolver(resolver)
        lookup.run()
        val record = lookup.answers.firstOrNull() as ARecord?
                ?: throw RuntimeException("Failed to resolve $host")
        val ip = record.address.hostAddress

        val schema = if (ssl) "https" else "http"
        val defaultPort = if (ssl) 443 else 80
        val portSuffix = if (port != defaultPort) ":$port" else ""
        val url = "$schema://$ip$portSuffix${request.uri()}"
        return Pair(host, url)
    }

    CoProxy(CoProxyConfig(443, true)) {
        val (host, url) = resolve(resolver, true)
        forward(url, host)
    }.listen()

    CoProxy(CoProxyConfig(80, false)) {
        val (host, url) = resolve(resolver, false)
        forward(url, host)
    }.listen()
}
