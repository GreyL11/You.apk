package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class ValidateTest {
    private fun index(evidence: Map<String, Any?>) = Validate.provenance(evidence)

    @Test
    fun `retry limit is exactly two`() {
        assertEquals(2, Validate.MAX_ATTEMPTS)
    }

    @Test
    fun `numbers written into a sentence are the ones that get checked`() {
        // The whole point: a claim arrives as prose, not as a bare figure.
        val evidence = mapOf("avgSleep" to 5.5, "nights" to 10)
        val answer = ModelAnswer(
            observed = listOf("You averaged 5.5h across 10 nights."),
            meaning = "That is short of 7.",
        )
        val v = Validate.checkAnswer(answer, index(evidence))
        assertFalse(v.checked.isEmpty())
        assertFalse(v.ok) // "7" is a target the model brought, not a number in the evidence
        assertEquals(listOf("7"), v.unsupported.map { it.claim.raw })
    }

    @Test
    fun `a fabricated figure is caught and named in the feedback`() {
        val evidence = mapOf("avgSleep" to 5.5, "nights" to 10)
        val answer = ModelAnswer(
            observed = listOf("You averaged 5.5h across 14 nights."),
            meaning = "",
        )
        val v = Validate.checkAnswer(answer, index(evidence))
        assertFalse(v.ok)
        assertEquals(1, v.unsupported.size)
        assertEquals("14", v.unsupported[0].claim.raw)
        assertTrue(v.feedback!!.contains("14"))
        // Never hand back a corrected number — that is inventing evidence to patch invented evidence.
        assertFalse(v.feedback!!.contains("Use 10"))
    }

    @Test
    fun `an answer whose every number is in the evidence passes`() {
        val evidence = mapOf("avgSleep" to 5.5, "nights" to 10, "target" to 7)
        val answer = ModelAnswer(
            observed = listOf("You averaged 5.5h across 10 nights."),
            meaning = "That is short of 7.",
            suggestion = "Aim for 8 hours tonight.", // a proposal, exempt
        )
        assertTrue(Validate.checkAnswer(answer, index(evidence)).ok)
    }

    @Test
    fun `an integer must be quoted exactly`() {
        val i = index(mapOf("sets" to 6))
        assertEquals(ClaimStatus.SUPPORTED, check("6", i).status)
        assertEquals(ClaimStatus.UNSUPPORTED, check("7", i).status)
        assertEquals(ClaimStatus.UNSUPPORTED, check("6.5", i).status)
    }

    @Test
    fun `a decimal may be quoted rounder, but never at zero decimals`() {
        val i = index(mapOf("faultsPerRep" to 0.38))
        assertEquals(ClaimStatus.SUPPORTED, check("0.38", i).status)
        assertEquals(ClaimStatus.SUPPORTED, check("0.4", i).status)
        // "0 faults per rep" is a different statement about their form, not a rounding of 0.38.
        assertEquals(ClaimStatus.UNSUPPORTED, check("0", i).status)
    }

    @Test
    fun `a percentage is checked at the precision it was written to`() {
        val i = index(mapOf("share" to 0.38))
        assertEquals(ClaimStatus.SUPPORTED, check("38%", i).status)
        assertEquals(ClaimStatus.UNSUPPORTED, check("40%", i).status)
    }

    @Test
    fun `a suggested number is exempt, a reported one is not`() {
        val i = index(mapOf("reps" to 10))
        val suggestion = Claims.extract("Try 3 sets next time.", Claimed.RECOMMENDATION)
        assertEquals(ClaimStatus.EXEMPT, Validate.checkClaim(suggestion[0], i).status)
        val observed = Claims.extract("You did 3 sets.", Claimed.OBSERVED)
        assertEquals(ClaimStatus.UNSUPPORTED, Validate.checkClaim(observed[0], i).status)
    }

    @Test
    fun `a load change is checkable, a load that did not move is not`() {
        val moved = index(mapOf("decision" to mapOf("from" to 40, "to" to 45)))
        assertEquals(ClaimStatus.SUPPORTED, check("5", moved).status)
        // A hold has from == to. Admitting that zero would support any "0" claim in the answer.
        val held = index(mapOf("decision" to mapOf("from" to 40, "to" to 40)))
        assertEquals(ClaimStatus.UNSUPPORTED, check("0", held).status)
    }

    @Test
    fun `provenance walks nested lists and objects`() {
        val i = index(
            mapOf(
                "sessions" to listOf(mapOf("reps" to 12), mapOf("reps" to 8)),
                "bodyweight" to "82.4",
            ),
        )
        assertEquals(ClaimStatus.SUPPORTED, check("12", i).status)
        assertEquals(ClaimStatus.SUPPORTED, check("8", i).status)
        assertEquals(ClaimStatus.SUPPORTED, check("82.4", i).status)
        assertEquals(ClaimStatus.UNSUPPORTED, check("9", i).status)
    }

    @Test
    fun `prose is classified a sentence at a time`() {
        val i = index(mapOf("reps" to 10))
        // The advice sentence proposes 3; the report sentence claims 12 and has to hold up.
        val v = Validate.checkProse("You managed 12 reps. Try 3 sets next time.", i)
        assertFalse(v.ok)
        assertEquals(listOf("12"), v.unsupported.map { it.claim.raw })
    }

    private fun check(written: String, i: List<ProvenanceEntry>) =
        Validate.checkClaim(Claims.extract(written, Claimed.OBSERVED).first(), i)
}
