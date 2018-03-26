package io.coproxy

import io.netty.handler.codec.http.*
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Job

interface ProxyContext : CoroutineScope {
    val request: HttpRequest

    val decoder: QueryStringDecoder

    val job: Job

    suspend fun forward(url: String)

    suspend fun replyOk(msg: String, contentType: String = "text/plain")

    suspend fun simpleHttp(request: FullHttpRequest): FullHttpResponse

    suspend fun simpleHttpGet(url: String): FullHttpResponse {
        val request = DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.GET,
            url
        )

        return simpleHttp(request)
    }
}