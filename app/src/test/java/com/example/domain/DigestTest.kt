package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class DigestTest {
    @Test
    fun `RULES string contains critical instructions verbatim`() {
        assertTrue(Digest.RULES.contains("The user's own logged data follows as JSON."))
        assertTrue(Digest.RULES.contains("Never diagnose anything, never estimate a hormone level, and never say whether a figure is normal, healthy, low or high."))
        assertTrue(Digest.RULES.contains("this app cannot measure hormone levels from lifestyle data, ever."))
    }

    @Test
    fun `prune removes nulls and empty collections but leaves zero`() {
        val data = mapOf(
            "valid" to 1,
            "zero" to 0,
            "zeroDouble" to 0.0,
            "emptyString" to "",
            "nullVal" to null,
            "emptyList" to emptyList<Any>(),
            "emptyMap" to emptyMap<String, Any>(),
            "nested" to mapOf(
                "validInner" to "hi",
                "emptyInner" to ""
            ),
            "nestedEmpty" to mapOf(
                "emptyInner" to ""
            )
        )

        val pruned = Digest.prune(data)

        assertTrue(pruned.containsKey("valid"))
        assertTrue(pruned.containsKey("zero")) // Zero must not be pruned
        assertTrue(pruned.containsKey("zeroDouble"))
        
        assertFalse(pruned.containsKey("emptyString"))
        assertFalse(pruned.containsKey("nullVal"))
        assertFalse(pruned.containsKey("emptyList"))
        assertFalse(pruned.containsKey("emptyMap"))
        assertFalse(pruned.containsKey("nestedEmpty")) // Becomes empty and removed
        
        val nested = pruned["nested"] as Map<*, *>
        assertTrue(nested.containsKey("validInner"))
        assertFalse(nested.containsKey("emptyInner"))
    }
}
