package com.example.domain

import com.example.data.Meal
import com.example.data.Profile
import org.junit.Assert.*
import org.junit.Test

class NutritionTest {
    @Test
    fun `water target scales with bodyweight and training frequency`() {
        val p1 = Profile(name = "", bodyweight = 70.0, daysPerWeek = 3, bar = 20.0, plates = "[]", experience = 1, goal = "", equipment = "[]", injuries = "[]", kcalTarget = null, poseModel = null)
        // 70 * 35 + 500 * 3 / 7 = 2450 + 214.28 = 2664.28 -> round to 2700
        assertEquals(2700, Nutrition.waterTarget(p1))
        
        val p2 = Profile(name = "", bodyweight = 100.0, daysPerWeek = 5, bar = 20.0, plates = "[]", experience = 1, goal = "", equipment = "[]", injuries = "[]", kcalTarget = null, poseModel = null)
        // 100 * 35 + 500 * 5 / 7 = 3500 + 357.14 = 3857.14 -> 3900
        assertEquals(3900, Nutrition.waterTarget(p2))
    }
    
    @Test
    fun `water is counted from anything with volume but not alcohol`() {
        val entries = listOf(
            Pair(Meal(at = "2023-10-25T10:00:00Z", foodId = "water", qty = 1.0), 250.0), // 1 glass
            Pair(Meal(at = "2023-10-25T12:00:00Z", foodId = "coffee", qty = 2.0), 200.0), // 2 cups
            Pair(Meal(at = "2023-10-25T18:00:00Z", foodId = "beer", qty = 1.0), null) // alcohol, no ml
        )
        // 250 + 400 = 650
        assertEquals(650, Nutrition.fluid(entries))
    }

    @Test
    fun `macros sum real per-serving values across a mixed log`() {
        // Roti (104 kcal, 3g prot, 20g carb, 2g fat) x2, plus dal (200, 12, 33, 3) x1.
        val r = Nutrition.macros(listOf("roti" to 2.0, "dal" to 1.0))
        assertEquals(104 * 2 + 200, r.kcal)
        assertEquals(3 * 2 + 12, r.protein)
        assertEquals(20 * 2 + 33, r.carbs)
        assertEquals(2 * 2 + 3, r.fat)
    }

    @Test
    fun `a half serving is half the macros, not a rounded whole one`() {
        val whole = Nutrition.macros(listOf("chickenBreast" to 1.0))
        val half = Nutrition.macros(listOf("chickenBreast" to 0.5))
        // 165 kcal whole -> 82.5 -> rounds to 82 or 83, not 165 and not 0.
        assertTrue(half.kcal in 80..85)
        assertTrue(half.kcal < whole.kcal)
    }

    @Test
    fun `an id that no longer resolves contributes nothing rather than crashing the total`() {
        val r = Nutrition.macros(listOf("roti" to 1.0, "thisFoodWasDeleted" to 3.0))
        assertEquals(Nutrition.macros(listOf("roti" to 1.0)), r)
    }

    @Test
    fun `drinks with real calories count toward the total, water does not`() {
        val chai = Nutrition.macros(listOf("chai" to 1.0))
        assertTrue("a chai's calories are real and must be counted", chai.kcal > 0)
        val water = Nutrition.macros(listOf("water" to 1.0))
        assertEquals(0, water.kcal)
    }

    @Test
    fun `the Meal overload matches the (foodId, qty) overload it wraps`() {
        val meals = listOf(
            Meal(at = "2023-10-25T10:00:00Z", foodId = "roti", qty = 2.0),
            Meal(at = "2023-10-25T12:00:00Z", foodId = "dal", qty = 1.0),
        )
        assertEquals(Nutrition.macros(listOf("roti" to 2.0, "dal" to 1.0)), Nutrition.macros(meals))
    }

    @Test
    fun `nothing logged is a real zero, not an absence`() {
        // Explicit: macros() is overloaded for both List<Meal> and List<Pair<String, Double>>, so a
        // bare emptyList() is genuinely ambiguous. This asserts the no-meals-logged case.
        assertEquals(Nutrition.Macros(0, 0, 0, 0), Nutrition.macros(emptyList<Meal>()))
    }
}
