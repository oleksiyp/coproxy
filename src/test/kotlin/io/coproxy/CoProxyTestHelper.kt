package io.coproxy

import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.logging.LogLevel
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import okhttp3.*
import org.junit.jupiter.api.Assertions
import java.io.IOException
import java.nio.charset.Charset
import kotlin.coroutines.experimental.suspendCoroutine

class CoProxyTestHelper() {
    val proxies = mutableListOf<CoProxy>()
    var client = OkHttpClient()
    val utf8: Charset = Charset.forName("utf-8")

    fun coProxy(
        port: Int,
        clientIdleTimeoutMs: Long = 2000,
        serverIdleTimeoutMs: Long = 10000,
        simpleHttpIdleTimeoutMs: Long = 2000,
        handler: suspend ProxyContext.() -> Unit
    ) {
        val proxy = CoProxy(
            handler = handler,
            config = CoProxyConfig(
                port,
                serverThreads = 1,
                clientIdleTimeoutMs = clientIdleTimeoutMs,
                serverIdleTimeoutMs = serverIdleTimeoutMs,
                simpleHttpIdleTimeoutMs = simpleHttpIdleTimeoutMs,
                maxPendingAcquires = 5,
                trafficLogging = LogLevel.DEBUG
            )
        )
        proxy.listen()
        proxies.add(proxy)
    }

    fun checkHttpGet(
        url: String,
        statusCode: HttpResponseStatus,
        msg: String? = null,
        n: Int = 1
    ) {
        runBlocking {
            (1..n)
                .map {
                    val request = Request.Builder()
                        .url(url)
                        .build()

                    async(coroutineContext) { client.newCall(request).coExecute() }
                }
                .map { it.await() }
                .map { Pair(it.code(), it.body()?.string() ?: "") }
                .forEach { (status, body) ->
                    Assertions.assertEquals(statusCode.code(), status, "Bad status code. Body: $body")
                    msg?.let { Assertions.assertEquals(it, body, "Body content") }
                }
        }
    }

    fun shutdown() {
        proxies.forEach { it.stopFast() }
    }

}

private suspend fun Call.coExecute(): Response {
    return suspendCoroutine {
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                it.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                it.resume(response)
            }

        })
    }
}

