package io.coproxy

class HostHeaderParser(
    val expr: String,
    val ssl: Boolean,
    val allowMaskPort: Boolean = false
) {
    val host = expr.host()
    val port = expr.port() ?: defaultPort()

    private fun defaultPort() = if (ssl) 443 else 80

    private fun String.host(): String {
        val idx = indexOf(':')
        if (idx == -1) return this
        return substring(0, idx)
    }

    private fun String.port(): Int? {
        val idx = indexOf(':')
        if (idx == -1) return null
        val part = substring(idx + 1)
        if (allowMaskPort) {
            if (part == "*") return -1
        }
        return part.toInt()
    }

}