package io.coproxy

import io.netty.handler.codec.http.HttpResponseStatus
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.charset.Charset


class ProxyTest {
    val h = Helper()

    @AfterEach
    fun shutdown() = h.shutdown()

    @Test
    fun simpleMessageProxied() {
        h.coProxy(8080) { forward("http://localhost:8081") }
        h.coProxy(8081) { replyOk("SEE_THIS_IF_PROXIED") }
        h.checkHttpGetOk("http://localhost:8080/", "SEE_THIS_IF_PROXIED")
    }

    @Test
    fun timeoutOnProxy() {
        h.coProxy(8080) { forward("http://localhost:8081") }
        h.coProxy(8081, clientIdleTimeoutMs = 5000) {
            delay(3000);
            replyOk("SEE_THIS_IF_PROXIED")
        }
        h.checkHttpGet("http://localhost:8080/", HttpResponseStatus.GATEWAY_TIMEOUT)
    }

    @Test
    fun simpleHttp() {
        h.coProxy(8080) { forward("http://localhost:8081") }
        h.coProxy(8082) {
            replyOk("PART${request.uri()}")
        }
        h.coProxy(8081) {
            val msg = (1..5).map {
                async { simpleHttpGet("http://localhost:8082/$it") }
            }.map {
                it.await()
                    .content()
                    .toString(Charset.forName("utf-8"))
            }.joinToString(" ")

            replyOk(msg)
        }
        h.checkHttpGetOk("http://localhost:8080/", "PART/1 PART/2 PART/3 PART/4 PART/5")
    }

}

class Helper {
    val proxies = mutableListOf<CoProxy>()
    var client = OkHttpClient()

    fun coProxy(
        port: Int,
        clientIdleTimeoutMs: Long = 1000,
        handler: suspend ProxyContext.() -> Unit
    ) {
        val proxy = CoProxy(
            handler = handler,
            config = CoProxyConfig(
                port,
                serverThreads = 1,
                clientIdleTimeoutMs = clientIdleTimeoutMs
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
        assertEquals(statusCode.code(), response.code(), "Status code")

        val body = response.body()?.string() ?: ""
        msg?.let { assertEquals(it, body, "Body content") }
    }

    fun shutdown() {
        proxies.forEach { it.stop() }
    }
}