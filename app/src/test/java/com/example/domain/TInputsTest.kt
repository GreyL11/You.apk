package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class TInputsTest {
    @Test
    fun `no testosterone score or TRT guidance anywhere`() {
        val result = TInputs.evaluate(TInputData(emptyList(), emptyList(), 0, false))
        assertFalse(result.toString().contains("testosterone_score", ignoreCase = true))
        assertFalse(result.toString().contains("trt", ignoreCase = true))
        assertFalse(result.toString().contains("sarm", ignoreCase = true))
    }
    
    @Test
    fun `weight direction is known or unknown only, never bad`() {
        val r1 = TInputs.evaluate(TInputData(listOf(8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0), listOf(70.0, 75.0), 10, false))
        assertEquals("known", r1.weight)
        
        val r2 = TInputs.evaluate(TInputData(listOf(8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0), emptyList(), 10, false))
        assertEquals("unknown", r2.weight)
    }
    
    @Test
    fun `sleep floor and bands`() {
        // Less than 10 nights = unknown
        val r0 = TInputs.evaluate(TInputData(List(9) { 8.0 }, emptyList(), 10, false))
        assertEquals("unknown", r0.sleep)
        
        val r1 = TInputs.evaluate(TInputData(List(10) { 5.5 }, emptyList(), 10, false))
        assertEquals("low", r1.sleep)
        
        val r2 = TInputs.evaluate(TInputData(List(10) { 6.5 }, emptyList(), 10, false))
        assertEquals("under", r2.sleep)
        
        val r3 = TInputs.evaluate(TInputData(List(10) { 7.5 }, emptyList(), 10, false))
        assertEquals("good", r3.sleep)
    }
    
    @Test
    fun `irregular sleep pattern withholds bedtime but names target`() {
        val r = TInputs.evaluate(TInputData(List(10) { 5.5 }, emptyList(), 10, true))
        assertNotNull(r.advice.plan)
        assertFalse(r.advice.plan!!.contains(Regex("\\d\\d:\\d\\d")))
        assertTrue(r.advice.plan!!.contains("7 hours"))
    }
}
