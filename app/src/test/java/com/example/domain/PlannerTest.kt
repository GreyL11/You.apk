package com.example.domain

import org.junit.Assert.*
import org.junit.Test

/** Covers the plate-loadout snapping and session-planning logic ported from planner.js — the
 *  audit's "plate-loadout snapping is unbuilt" and "load handling is effectively hardcoded" gaps. */
class PlannerTest {
    private val profile = TrainingProfile() // default bar=20, plates=[25,20,15,10,5,2.5,1.25]

    @Test
    fun `barbellStep is twice the smallest plate the gym actually has`() {
        assertEquals(2.5, Planner.barbellStep(profile), 0.001) // 2 * 1.25
        val noSmallPlates = profile.copy(plates = listOf(20.0, 10.0, 5.0))
        assertEquals(10.0, Planner.barbellStep(noSmallPlates), 0.001) // 2 * 5
    }

    @Test
    fun `achievableLoad snaps to the real grid, never assuming plates that do not exist`() {
        // Requesting 62.5kg with only 2.5kg-smallest plates (step=5) cannot land on 62.5 exactly.
        val coarse = profile.copy(plates = listOf(20.0, 10.0, 5.0, 2.5))
        val snapped = Planner.achievableLoad(62.5, coarse)
        // step = 2*2.5 = 5; nearest multiple of 5 from bar(20): 20 + round((62.5-20)/5)*5 = 20+45=65
        assertEquals(65.0, snapped, 0.001)
    }

    @Test
    fun `achievableLoad never returns less than the bar itself`() {
        assertEquals(20.0, Planner.achievableLoad(5.0, profile), 0.001)
    }

    @Test
    fun `loadout picks biggest plates first and reports whether the target was exact`() {
        val l = Planner.loadout(100.0, profile) // 100 - 20 bar = 80 total, 40 per side
        assertTrue(l.exact)
        // 40 = 25 + 15 -> biggest-first greedy
        assertEquals(listOf(25.0 to 1, 15.0 to 1), l.perSide)
    }

    @Test
    fun `loadout reports an inexact result honestly rather than pretending a match`() {
        val coarse = profile.copy(plates = listOf(20.0)) // only 20kg plates, none smaller
        val l = Planner.loadout(105.0, coarse) // 42.5kg per side needed: two 20s fit, 2.5kg cannot
        assertFalse(l.exact)
        assertEquals(100.0, l.actual, 0.001) // bar(20) + 2*(2*20) = 100, honestly short of the requested 105
    }

    @Test
    fun `loadoutText renders a human-readable line for both the exact and under-the-bar cases`() {
        // 62.5 = bar(20) + 2 * (20 + 1.25) -> one 20 and one 1.25 per side, exact
        assertEquals("Bar + 20 + 1.25 per side", Planner.loadoutText(62.5, profile.copy(plates = listOf(20.0, 1.25))))
        assertTrue(Planner.loadout(10.0, profile).under)
    }

    @Test
    fun `bodyweight exercises have zero starting load, loaded exercises scale with bodyweight`() {
        assertEquals(0.0, Planner.startingLoad("pushup", profile), 0.001)
        assertTrue(Planner.startingLoad("squat", profile) > 0.0)
    }

    @Test
    fun `starting load for a barbell exercise is already snapped to the real plate grid`() {
        val load = Planner.startingLoad("squat", profile) // 75 * 0.60 * 1.0 = 45, already on a 2.5 grid with these plates
        val snapped = Planner.achievableLoad(load, profile)
        assertEquals(load, snapped, 0.001)
    }

    @Test
    fun `heavy leg compounds increment by 5kg, everything else by 2_5, never below barbellStep`() {
        assertEquals(5.0, Planner.increment("squat", profile), 0.001)
        assertEquals(2.5, Planner.increment("bench", profile), 0.001)
        val coarsePlates = profile.copy(plates = listOf(20.0, 10.0)) // barbellStep = 20
        assertEquals(20.0, Planner.increment("bench", coarsePlates), 0.001) // floored up from 2.5
    }

    @Test
    fun `available lifts respect both equipment and injuries`() {
        val noBarbell = profile.copy(equipment = listOf("dumbbell", "bodyweight"))
        assertFalse(Planner.available(noBarbell).contains("squat"))
        val badKnee = profile.copy(injuries = listOf("knee"))
        assertFalse(Planner.available(badKnee).contains("squat"))
        assertTrue(Planner.available(badKnee).contains("bench"))
    }

    @Test
    fun `a session never prescribes the same lift twice`() {
        val session = Planner.today(1, profile) // Monday, 3-day split default
        assertNotNull(session)
        val ids = session!!.exercises.map { it.exId }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `doneToday-equivalent scoping is the caller's job, but today() honors rest days`() {
        val restDayProfile = profile.copy(daysPerWeek = 3) // TRAINING_DAYS[3] = Mon/Wed/Fri
        assertNull(Planner.today(0, restDayProfile)) // Sunday is a rest day on a 3-day split
        assertNotNull(Planner.today(1, restDayProfile)) // Monday trains
    }
}
