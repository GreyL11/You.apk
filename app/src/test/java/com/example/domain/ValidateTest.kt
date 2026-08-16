package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class ValidateTest {
    @Test
    fun `retry limit is exactly two`() {
        assertEquals(2, Validate.MAX_ATTEMPTS)
    }

    @Test
    fun `valid claims are accepted`() {
        val evidence = mapOf(
            "reps" to 10,
            "weight" to 70.5,
            "fraction" to 0.75,
            "progress" to mapOf("from" to 40, "to" to 45)
        )
        
        val claims = listOf(
            Claim("10", "fact", "exact"),
            Claim("70.5", "fact", "decimal"),
            Claim("75", "fact", "percent"), // 0.75 * 100 = 75
            Claim("5", "fact", "exact") // |45 - 40| = 5
        )
        
        val results = Validate.checkClaims(claims, evidence)
        assertTrue(results.all { it.isSupported })
    }

    @Test
    fun `fabricated claims are rejected`() {
        val evidence = mapOf("reps" to 10)
        
        val claims = listOf(
            Claim("11", "fact", "exact"),
            Claim("10.5", "fact", "decimal")
        )
        
        val results = Validate.checkClaims(claims, evidence)
        assertTrue(results.none { it.isSupported })
    }

    @Test
    fun `recommendation claims are exempt`() {
        val evidence = mapOf("reps" to 10)
        
        val claims = listOf(
            Claim("999", "recommendation", "exact")
        )
        
        val results = Validate.checkClaims(claims, evidence)
        assertTrue(results.all { it.isSupported })
    }
}
