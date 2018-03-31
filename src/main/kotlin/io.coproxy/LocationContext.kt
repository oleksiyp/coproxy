package io.coproxy

import kotlin.math.min

data class LocationContext(
    val unmatchedUri: String,
    val hostInHeader: String?,
    val portInHeader: Int,
    val ssl: Boolean,
    val groups: Array<String>
) {
    suspend fun location(
        prefix: String = "",
        regex: String?,
        host: String?,
        block: suspend LocationContext.() -> Unit
    ) {
        if (!unmatchedUri.startsWith(prefix)) {
            return
        }

        if (host != null) {
            val parser = HostHeaderParser(host, ssl, true)
            if (parser.port != -1 && parser.port != portInHeader) {
                return
            }

            if (!matchPattern(hostInHeader ?: "", parser.host)) {
                return
            }
        }

        val prefixLeft = unmatchedUri.substring(prefix.length)
        if (regex != null) {
            val result = regex.toRegex().find(prefixLeft) ?: return

            val regexLeft = unmatchedUri.substring(result.range.last)
            val newGroups = result.groupValues.toTypedArray()

            copy(
                unmatchedUri = regexLeft,
                groups = groups + newGroups
            ).block()
        } else {
            copy(unmatchedUri = prefixLeft).block()
        }
    }

    private fun matchPattern(value: String, pattern: String): Boolean {
        if (pattern.isEmpty()) return value.isEmpty()

        val starPos = pattern.indexOf('*')
        if (starPos == -1) {
            return value == pattern
        } else {
            return value.substring(0, min(starPos, value.length)) == pattern.substring(0, starPos)
                    && matchStar(starPos, value, pattern)
        }
    }

    private fun matchStar(
        starPos: Int,
        value: String,
        pattern: String
    ): Boolean {
        val newPattern = pattern.substring(starPos + 1)
        var j = starPos
        if (j >= value.length - 1) return matchPattern("", newPattern)
        while (j < value.length) {
            val newValue = value.substring(j)
            if (matchPattern(newValue, newPattern)) {
                return true
            }
            j++
        }
        return false
    }

    val g1: String
        get() = groups[1]
    val g2: String
        get() = groups[2]
    val g3: String
        get() = groups[3]
    val g4: String
        get() = groups[4]
    val g5: String
        get() = groups[5]
    val g6: String
        get() = groups[6]
    val g7: String
        get() = groups[7]
    val g8: String
        get() = groups[8]
    val g9: String
        get() = groups[9]
    val g10: String
        get() = groups[10]
}
