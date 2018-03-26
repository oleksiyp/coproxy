package io.coproxy

import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.handler.codec.http.HttpResponse
import io.netty.util.ReferenceCountUtil.release
import io.netty.util.ReferenceCounted
import io.netty.util.concurrent.Future
import kotlinx.coroutines.experimental.cancelFutureOnCompletion
import kotlinx.coroutines.experimental.suspendCancellableCoroutine
import java.io.FileNotFoundException
import java.io.IOException

suspend fun ChannelFuture.wait(): Channel {
    if (isDone) {
        if (!isSuccess) {
            throw cause()
        }
        return channel()
    }

    return suspendCancellableCoroutine { cont ->
        val future = this
        cont.cancelFutureOnCompletion(future)
        future.addListener {
            val channel = future.channel()
            if (future.isSuccess) {
                cont.resume(channel)
            } else {
                cont.resumeWithException(future.cause())
            }
        }
    }
}

suspend fun <T> Future<T>.wait(): T {
    if (isDone) {
        if (!isSuccess) {
            throw cause()
        }
        return get()
    }
    return suspendCancellableCoroutine { cont ->
        val future = this
        cont.cancelFutureOnCompletion(future)
        future.addListener {
            if (future.isSuccess) {
                cont.resume(future.get())
            } else {
                cont.resumeWithException(future.cause())
            }
        }
    }
}

fun <T : HttpResponse> T.throwIfError(release: Boolean = true): T {
    val code = status().code()
    if (code < 400) return this

    if (release) {
        release(this)
    }

    if (code == 404 || code == 410) {
        throw FileNotFoundException()
    }

    throw IOException("Server returned HTTP response code: $code")
}

fun <F : ReferenceCounted, T> F.release(block: F.() -> T): T {
    val res = block()
    release()
    return res
}