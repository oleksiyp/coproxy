package io.coproxy

import io.netty.handler.codec.http.HttpResponseStatus
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test


class CoProxyTest {
    val h = CoProxyTestHelper()

    @AfterEach
    fun shutdown() = h.shutdown()

    @Test
    fun simpleMessageProxied() {
        h.coProxy(8080) { forward("http://localhost:8081") }
        h.coProxy(8081) { replyOk("SEE_THIS_IF_PROXIED") }
        h.checkHttpGet("http://localhost:8080/", HttpResponseStatus.OK, "SEE_THIS_IF_PROXIED")
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
    fun timeoutOnServer() {
        h.coProxy(8080, clientIdleTimeoutMs = 5000) { forward("http://localhost:8081") }
        h.coProxy(8081, serverIdleTimeoutMs = 1000) {
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
            val msg = (1..5)
                .map { async { simpleHttpGet("http://localhost:8082/$it") } }
                .map { it.await().throwIfError() }
                .joinToString(" ") {
                    it.release {
                        it.content().toString(h.utf8)
                    }
                }

            replyOk(msg)
        }
        (1..100).forEach {
            println(it)
            h.checkHttpGet(
                "http://localhost:8080/",
                HttpResponseStatus.OK,
                "PART/1 PART/2 PART/3 PART/4 PART/5",
                n = 10
            )
        }
    }

    @Test
    fun simpleHttpTimeout() {
        h.coProxy(8080) { forward("http://localhost:8081") }
        h.coProxy(8082) {
            delay(1500)
            replyOk("PART${request.uri()}")
        }
        h.coProxy(8081, clientIdleTimeoutMs = 1300) {
            val msg = (1..5)
                .map { async { simpleHttpGet("http://localhost:8082/$it") } }
                .map { it.await().throwIfError() }
                .joinToString(" ") {
                    it.release {
                        it.content().toString(h.utf8)
                    }
                }

            replyOk(msg)
        }

        h.checkHttpGet(
            "http://localhost:8080/",
            HttpResponseStatus.GATEWAY_TIMEOUT,
            null,
            n = 20
        )
    }
}
