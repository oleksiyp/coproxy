package io.coproxy

import io.netty.channel.ChannelException

class TimeoutException(msg: String) : ChannelException(msg)