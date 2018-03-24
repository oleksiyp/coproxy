package io.coproxy

import io.netty.handler.codec.http.*

interface ProxyContext {
    val request: HttpRequest

    val decoder: QueryStringDecoder

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