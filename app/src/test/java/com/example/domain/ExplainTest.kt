package com.example.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExplainTest {
    private val evidence = mapOf("avgSleep" to 5.5, "nights" to 10)
    private val plain = "Averaging 5.5h over 10 nights."

    private fun answer(nights: String) = ModelAnswer(
        observed = listOf("You averaged 5.5h across $nights nights."),
        meaning = "Short nights, consistently.",
        suggestion = "Try lights off 45 minutes earlier.",
    )

    @Test
    fun `an answer whose numbers hold up is shown, first attempt`() = runBlocking {
        var calls = 0
        val r = Explain.explainDecision(evidence, plain) { _, feedback ->
            calls++
            assertNull(feedback)
            answer("10")
        }
        assertEquals(1, calls)
        assertEquals(ExplainStatus.OK, r.status)
        assertEquals(1, r.attempts)
        assertTrue(r.display.contains("5.5h across 10 nights"))
    }

    @Test
    fun `one retry, told what was wrong and nothing else`() = runBlocking {
        var calls = 0
        val r = Explain.explainDecision(evidence, plain) { _, feedback ->
            calls++
            if (calls == 1) {
                assertNull(feedback)
                answer("14") // fabricated
            } else {
                assertNotNull(feedback)
                assertTrue(feedback!!.contains("14"))
                answer("10")
            }
        }
        assertEquals(2, calls)
        assertEquals(ExplainStatus.OK, r.status)
        assertEquals(2, r.attempts)
    }

    @Test
    fun `twice unsupported discards the answer entirely and shows the app's own line`() = runBlocking {
        var calls = 0
        val r = Explain.explainDecision(evidence, plain) { _, _ ->
            calls++
            answer("14")
        }
        assertEquals(Validate.MAX_ATTEMPTS, calls)
        assertEquals(ExplainStatus.UNVERIFIED, r.status)
        // Not trimmed, not partially rendered — gone.
        assertNull(r.answer)
        assertEquals(plain, r.display)
        assertEquals(listOf("14"), r.unsupported.map { it.raw })
    }

    @Test
    fun `no answer at all costs the person nothing`() = runBlocking {
        val r = Explain.explainDecision(evidence, plain) { _, _ -> null }
        assertEquals(ExplainStatus.UNAVAILABLE, r.status)
        assertEquals(plain, r.display)
    }

    @Test
    fun `a thrown call is an unavailable answer, not a crash`() = runBlocking {
        val r = Explain.explainDecision(evidence, plain) { _, _ ->
            throw java.io.IOException("no network")
        }
        assertEquals(ExplainStatus.UNAVAILABLE, r.status)
        assertEquals(plain, r.display)
    }

    @Test
    fun `nothing recorded is not explained, and the model is never asked`() = runBlocking {
        var calls = 0
        val r = Explain.explainDecision(null, null) { _, _ -> calls++; answer("10") }
        assertEquals(0, calls)
        assertEquals(ExplainStatus.NO_EVIDENCE, r.status)
        assertEquals(0, r.attempts)
    }

    @Test
    fun `the deterministic line is what shows when there is no model answer`() = runBlocking {
        val r = Explain.explainDecision(evidence, null) { _, _ -> null }
        assertEquals(Explain.fallbackExplanation(), r.display)
    }
}
