package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class MealParseTest {
    @Test
    fun `real ids come through with the app's own labels`() {
        val r = MealParse.validate(listOf("roti" to 2.0, "dal" to 1.0))
        assertEquals(2, r.items.size)
        assertEquals(setOf("roti", "dal"), r.items.map { it.foodId }.toSet())
        assertEquals("Roti / chapati", r.items.first { it.foodId == "roti" }.label)
        assertTrue(r.unknown.isEmpty())
    }

    @Test
    fun `an invented id is reported, never silently dropped`() {
        // The model guessing "paratha" is the expected failure — the catalogue has no paratha, and the
        // person needs to know it was skipped rather than believe their meal is fully logged.
        val r = MealParse.validate(listOf("roti" to 2.0, "paratha" to 1.0))
        assertEquals(listOf("roti"), r.items.map { it.foodId })
        assertEquals(listOf("paratha"), r.unknown)
    }

    @Test
    fun `a nonsense quantity is rejected, not rounded into something plausible`() {
        val zero = MealParse.validate(listOf("rice" to 0.0))
        assertTrue(zero.items.isEmpty())
        assertEquals(1, zero.unknown.size)

        val negative = MealParse.validate(listOf("rice" to -2.0))
        assertTrue(negative.items.isEmpty())

        val absurd = MealParse.validate(listOf("egg" to 500.0))
        assertTrue(absurd.items.isEmpty())
        assertTrue(absurd.unknown.single().contains("Egg"))
    }

    @Test
    fun `half a serving is a real quantity`() {
        val r = MealParse.validate(listOf("chickenBreast" to 0.5))
        assertEquals(0.5, r.items.single().qty, 0.001)
    }

    @Test
    fun `the same food said twice becomes one row`() {
        val r = MealParse.validate(listOf("rice" to 1.0, "rice" to 0.5))
        assertEquals(1, r.items.size)
        assertEquals(1.5, r.items.single().qty, 0.001)
    }

    @Test
    fun `nothing recognisable gives nothing, and does not throw`() {
        val r = MealParse.validate(emptyList())
        assertTrue(r.items.isEmpty())
        assertTrue(r.unknown.isEmpty())
    }

    @Test
    fun `the catalogue handed to the model is the real table`() {
        val cat = MealParse.foodCatalogue()
        assertTrue(cat.contains("soyaChunks = Soya chunks, dry"))
        assertEquals(Nutrition.FOODS.size, cat.lines().size)
    }
}
