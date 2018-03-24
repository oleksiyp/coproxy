package io.coproxy

import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.util.concurrent.Future
import kotlinx.coroutines.experimental.cancelFutureOnCompletion
import kotlinx.coroutines.experimental.suspendCancellableCoroutine

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

