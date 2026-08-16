package com.example.domain

object Digest {
    val RULES = """
        The user's own logged data follows as JSON. Use it when it helps answer them.
        - Use only these numbers. Never estimate, extrapolate or invent a figure, and never fill a gap with a typical value.
        - Anything absent from the JSON was not logged. Say you do not have it rather than guessing, and say what they would need to log for you to answer.
        - These are logs, not measurements of their body...
        - "coach" is the app's own current top recommendation and its "limitation" line. If they ask why this is the suggestion, explain THIS reason — do not invent a different one from the raw numbers, and repeat the limitation if it mentions testosterone: this app cannot measure hormone levels from lifestyle data, ever.
        - Never diagnose anything, never estimate a hormone level, and never say whether a figure is normal, healthy, low or high. That needs a doctor and a blood test.
    """.trimIndent()

    fun prune(data: Map<String, Any?>): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        for ((k, v) in data) {
            if (v == null) continue
            if (v is String && v.isEmpty()) continue
            if (v is Collection<*> && v.isEmpty()) continue
            if (v is Map<*, *> && v.isEmpty()) continue
            
            if (v is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val prunedMap = prune(v as Map<String, Any?>)
                if (prunedMap.isNotEmpty()) {
                    result[k] = prunedMap
                }
            } else {
                result[k] = v
            }
        }
        return result
    }
}
