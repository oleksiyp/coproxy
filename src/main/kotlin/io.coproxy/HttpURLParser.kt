package io.coproxy

import java.net.URI

class HttpURLParser(uri: URI) {
    val scheme = if (uri.scheme == null) "http" else uri.scheme
    val host = if (uri.host == null) "127.0.0.1" else uri.host
    var port = if (uri.port == -1) {
        if (schemaIs("http")) {
            80
        } else if (schemaIs("https")) {
            443
        } else {
            throw RuntimeException("Only HTTP(S) is supported.")
        }
    } else {
        uri.port
    }

    init {
        if (!schemaIs("http") && !schemaIs("https")) {
            throw RuntimeException("Only HTTP(S) is supported.")
        }
    }

    private fun schemaIs(value: String) = value.equals(scheme, ignoreCase = true)
}