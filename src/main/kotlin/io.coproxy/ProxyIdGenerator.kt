package io.coproxy

import java.util.concurrent.atomic.AtomicInteger

class ProxyIdGenerator {
    private val seq = AtomicInteger()

    fun next(): String = " RR#${seq.getAndIncrement()} C"
}