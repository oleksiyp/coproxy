package io.coproxy

interface RequestNotifier {
    fun notifyRequestStarted()

    fun notifyRequestSent()
}