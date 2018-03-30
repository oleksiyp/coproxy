package io.coproxy

import io.netty.handler.codec.http.HttpResponseStatus
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors


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
                .map { async(coroutineContext) { simpleHttpGet("http://localhost:8082/$it") } }
                .map { it.await().throwIfError() }
                .joinToString(" ") {
                    it.release {
                        it.content().toString(h.utf8)
                    }
                }

            replyOk(msg)
        }
        (1..10).forEach {
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
        h.coProxy(8080, clientIdleTimeoutMs = 2000) { forward("http://localhost:8081") }
        h.coProxy(8082) {
            delay(1500)
            replyOk("PART${request.uri()}")
        }
        h.coProxy(8081, simpleHttpIdleTimeoutMs = 1000) {
            val msg = (1..5)
                .map { async(coroutineContext) { simpleHttpGet("http://localhost:8082/$it") } }
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
            n = 15
        )
    }

    @Test
    fun locationPrefix() {
        val n = AtomicInteger()
        h.coProxy(8080) {
            val uri = when (n.getAndIncrement() % 4) {
                0 -> "/p1/l1"
                1 -> "/p2/l1"
                2 -> "/p1/l2"
                3 -> "/p2/l2"
                else -> throw IllegalStateException()
            }

            forward("http://localhost:8081$uri")
        }

        h.coProxy(8081) {
            location("/p1") {
                location("/l1") { replyOk("r1") }
                location("/l2") { replyOk("r2") }
            }
            location("/p2") {
                location("/l1") { replyOk("r3") }
                location("/l2") { replyOk("r4") }
            }
        }

        runBlocking {
            h.spawnRequests("http://localhost:8080/", 100)
                .onEach { (status, _) -> Assertions.assertEquals(200, status) }
                .map { it.second }
                .stream()
                .collect(
                    Collectors.toMap(
                        { key: String -> key },
                        { 1 },
                        { a: Int, b: Int -> a + b }
                    )
                ).forEach { (_, count) ->
                    Assertions.assertEquals(25, count)
                }
        }
    }

    @Test
    fun locationRegex() {
        val n = AtomicInteger()
        h.coProxy(8080) {
            forward("http://localhost:8081/${n.getAndIncrement() % 10}")
        }

        h.coProxy(8081) {
            location(regex = "/(.+)") { replyOk("r$g1") }
        }

        runBlocking {
            h.spawnRequests("http://localhost:8080/", 100)
                .onEach { (status, _) -> Assertions.assertEquals(200, status) }
                .map { it.second }
                .stream()
                .collect(
                    Collectors.toMap(
                        { key: String -> key },
                        { 1 },
                        { a: Int, b: Int -> a + b }
                    )
                ).forEach { (_, count) ->
                    Assertions.assertEquals(10, count)
                }
        }
    }
}
