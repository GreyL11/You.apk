package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class EvidenceTest {
    @Test
    fun `evidence status vocabulary is exact`() {
        assertEquals("ok", Evidence.OK)
        assertEquals("no_evidence", Evidence.NO_EVIDENCE)
        assertEquals("insufficient_evidence", Evidence.INSUFFICIENT_EVIDENCE)
        assertEquals("unknown_exercise", Evidence.UNKNOWN_EXERCISE)
    }
}
