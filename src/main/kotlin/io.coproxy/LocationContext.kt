package io.coproxy

class LocationContext(
    val unmatchedUri: String,
    val groups: Array<String>
) {
    suspend fun location(
        prefix: String = "",
        regex: String = "",
        block: suspend LocationContext.() -> Unit
    ) {
        if (!unmatchedUri.startsWith(prefix)) {
            return
        }

        val prefixLeft = unmatchedUri.substring(prefix.length)
        if (!regex.isEmpty()) {
            val result = regex.toRegex().find(prefixLeft) ?: return

            val regexLeft = unmatchedUri.substring(result.range.last)
            val newGroups = result.groupValues.toTypedArray()

            LocationContext(regexLeft, groups + newGroups).block()
        } else {
            LocationContext(prefixLeft, groups).block()
        }
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
