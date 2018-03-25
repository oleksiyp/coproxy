package io.coproxy

import io.netty.handler.codec.http.HttpResponseStatus
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assertions
import java.nio.charset.Charset

class CoProxyTestHelper() {
    val proxies = mutableListOf<CoProxy>()
    var client = OkHttpClient()
    val utf8: Charset = Charset.forName("utf-8")

    fun coProxy(
        port: Int,
        clientIdleTimeoutMs: Long = 2000,
        serverIdleTimeoutMs: Long = 10000,
        handler: suspend ProxyContext.() -> Unit
    ) {
        val proxy = CoProxy(
            handler = handler,
            config = CoProxyConfig(
                port,
                serverThreads = 1,
                clientIdleTimeoutMs = clientIdleTimeoutMs,
                serverIdleTimeoutMs = serverIdleTimeoutMs,
                maxPendingAcquires = 5
            )
        )
        proxy.listen()
        proxies.add(proxy)
    }

    fun checkHttpGetOk(url: String, msg: String) {
        checkHttpGet(url, HttpResponseStatus.OK, msg)
    }

    fun checkHttpGet(
        url: String,
        statusCode: HttpResponseStatus,
        msg: String? = null
    ) {

        val request = Request.Builder()
            .url(url)
            .build()

        val response = client.newCall(request).execute()
        Assertions.assertEquals(statusCode.code(), response.code(), "Status code")

        val body = response.body()?.string() ?: ""
        msg?.let { Assertions.assertEquals(it, body, "Body content") }
    }

    fun shutdown() {
        proxies.forEach { it.stop() }
    }
}