package com.example.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExplainTest {
    @Test
    fun `successful explanation on first attempt`() = runBlocking {
        val evidence = mapOf("reps" to 10)
        var callCount = 0
        
        val result = Explain.explainDecision(evidence) { feedback ->
            callCount++
            assertNull(feedback)
            listOf(Claim("10", "fact", "exact"))
        }
        
        assertEquals(1, callCount)
        assertEquals("10", result)
    }

    @Test
    fun `successful explanation on second attempt`() = runBlocking {
        val evidence = mapOf("reps" to 10)
        var callCount = 0
        
        val result = Explain.explainDecision(evidence) { feedback ->
            callCount++
            if (callCount == 1) {
                assertNull(feedback)
                listOf(Claim("11", "fact", "exact")) // Fabricated
            } else {
                assertNotNull(feedback)
                assertTrue(feedback!!.contains("11"))
                listOf(Claim("10", "fact", "exact")) // Corrected
            }
        }
        
        assertEquals(2, callCount)
        assertEquals("10", result)
    }

    @Test
    fun `fallback after two failed attempts`() = runBlocking {
        val evidence = mapOf("reps" to 10)
        var callCount = 0
        
        val result = Explain.explainDecision(evidence) { feedback ->
            callCount++
            listOf(Claim("11", "fact", "exact")) // Always fabricated
        }
        
        assertEquals(2, callCount)
        assertEquals(Explain.fallbackExplanation(), result)
    }
}
