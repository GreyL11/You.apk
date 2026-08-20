package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class ClaimsTest {
    @Test
    fun `a decimal point is not a sentence boundary`() {
        // The trap this guards: splitting on "." turns 0.38 into "0" and "38" — one true claim
        // becomes two fabricated ones, and both then fail validation.
        val s = Claims.sentences("Corrections ran at 0.38 per rep. The limit is 0.3.")
        assertEquals(2, s.size)
        val claims = Claims.extract(s[0].text, Claimed.OBSERVED)
        assertEquals(listOf("0.38"), claims.map { it.raw })
    }

    @Test
    fun `precision is read off how the number was written`() {
        val claims = Claims.extract("60 kg, 62.5 kg, 0.333 per rep", Claimed.OBSERVED)
        assertEquals(listOf(0, 1, 3), claims.map { it.decimals })
        assertEquals(listOf(60.0, 62.5, 0.333), claims.map { it.value })
    }

    @Test
    fun `a trailing percent sign changes what may support the claim`() {
        val pct = Claims.extract("38% of your sets", Claimed.OBSERVED).first()
        assertEquals(ClaimKind.PERCENTAGE, pct.kind)
        assertEquals("38%", pct.raw)
        assertEquals(ClaimKind.NUMBER, Claims.extract("38 sets", Claimed.OBSERVED).first().kind)
    }

    @Test
    fun `negatives survive extraction`() {
        assertEquals(-1.5, Claims.extract("down -1.5 kg", Claimed.OBSERVED).first().value, 0.001)
    }

    @Test
    fun `prose marks advice sentences exempt and reports strict`() {
        val claims = Claims.extractProse("You hit 12 reps. Try 3 sets next time.")
        assertEquals(Claimed.INFERENCE, claims.first { it.raw == "12" }.claimed)
        assertEquals(Claimed.RECOMMENDATION, claims.first { it.raw == "3" }.claimed)
    }

    @Test
    fun `silence is not permission - a sentence with no cue is treated as a report`() {
        val claims = Claims.extractProse("It happened in 3 sets.")
        assertEquals(Claimed.INFERENCE, claims.first().claimed)
    }
}
