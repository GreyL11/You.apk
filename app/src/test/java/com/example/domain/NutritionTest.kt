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
}
