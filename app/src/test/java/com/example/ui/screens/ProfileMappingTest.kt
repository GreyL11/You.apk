package com.example.ui.screens

import com.example.data.Profile
import com.example.domain.TrainingProfile
import org.junit.Assert.*
import org.junit.Test

class ProfileMappingTest {
    private fun profile(
        plates: String = "[]",
        equipment: String = "[]",
        injuries: String = "[]",
        bar: Double = 20.0,
    ) = Profile(
        id = 1, name = "Alex", bodyweight = 80.0, daysPerWeek = 4, bar = bar,
        plates = plates, experience = 1, goal = "hypertrophy", equipment = equipment,
        injuries = injuries, kcalTarget = null, poseModel = "lite",
    )

    @Test
    fun `real stored plates and injuries survive the round trip`() {
        // This is the actual bug: before this file existed, TodayViewModel built a TrainingProfile
        // from bodyweight/daysPerWeek/goal alone and dropped bar, plates, equipment and injuries on
        // the floor -- so a real injury a person set could never reach Planner's (already correct,
        // already tested) injury filter.
        val p = profile(
            plates = "[20.0, 10.0, 1.25]",
            equipment = "[\"barbell\",\"dumbbell\"]",
            injuries = "[\"knee\",\"shoulder\"]",
            bar = 15.0,
        )
        val tp = p.toTrainingProfile()
        assertEquals(15.0, tp.bar, 0.001)
        assertEquals(listOf(20.0, 10.0, 1.25), tp.plates)
        assertEquals(listOf("barbell", "dumbbell"), tp.equipment)
        assertEquals(listOf("knee", "shoulder"), tp.injuries)
        // The values that were not dropped before this fix either -- must still survive.
        assertEquals(80.0, tp.bodyweight, 0.001)
        assertEquals(4, tp.daysPerWeek)
    }

    @Test
    fun `an empty json array is a real empty list, not the default plate set`() {
        // "[]" is a person who told the app they have no plates -- reading that back as the
        // seven-plate default would be inventing equipment they said they do not have.
        val tp = profile(plates = "[]").toTrainingProfile()
        assertEquals(emptyList<Double>(), tp.plates)
    }

    @Test
    fun `malformed json falls back to the default rather than crashing the whole profile read`() {
        val tp = profile(plates = "not json at all").toTrainingProfile()
        assertEquals(TrainingProfile().plates, tp.plates)
    }

    @Test
    fun `a blank field falls back to the default too`() {
        val tp = profile(equipment = "").toTrainingProfile()
        assertEquals(TrainingProfile().equipment, tp.equipment)
    }

    @Test
    fun `writing and reading back is the identity`() {
        val plates = listOf(25.0, 20.0, 1.25)
        val injuries = listOf("lowerBack")
        val p = profile(plates = doubleListToJson(plates), injuries = listToJson(injuries))
        val tp = p.toTrainingProfile()
        assertEquals(plates, tp.plates)
        assertEquals(injuries, tp.injuries)
    }
}
