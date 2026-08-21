package com.example.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * The validator is the whole safety story for typed logging: the model can return anything, and
 * everything it returns passes through here before it can be written. These tests are mostly about
 * what it REFUSES.
 */
class TextLogTest {
    private fun set(exId: String, sets: Int? = 3, reps: Int? = 8, load: Double? = 60.0, difficulty: Int? = null) =
        TextLog.RawSet(exId, sets, reps, load, difficulty)

    // ── exercise ids ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a real exercise id is kept, with the catalogue's own name attached`() {
        val r = TextLog.validate(rawSets = listOf(set("bench")))
        assertEquals(1, r.sets.size)
        assertEquals("bench", r.sets[0].exId)
        assertEquals(EXERCISES.getValue("bench").name, r.sets[0].label)
        assertEquals(3, r.sets[0].sets)
        assertEquals(8, r.sets[0].reps)
        assertEquals(60.0, r.sets[0].load, 0.001)
    }

    @Test
    fun `an invented exercise id is refused and surfaced, never silently dropped`() {
        val r = TextLog.validate(rawSets = listOf(set("legPress3000"), set("bench")))
        assertEquals(1, r.sets.size)
        assertEquals("bench", r.sets[0].exId)
        assertTrue("the unrecognised id must be reported", r.unknown.contains("legPress3000"))
    }

    // ── the core rule: never invent what wasn't said ──────────────────────────────────────────

    @Test
    fun `a set with no reps is refused rather than given an invented rep count`() {
        val r = TextLog.validate(rawSets = listOf(set("bench", reps = null)))
        assertTrue("no reps means no set", r.sets.isEmpty())
        assertFalse(r.unknown.isEmpty())
    }

    @Test
    fun `missing difficulty stays null, never a middle value`() {
        val r = TextLog.validate(rawSets = listOf(set("bench", difficulty = null)))
        assertNull(r.sets[0].difficulty)
    }

    @Test
    fun `an unreported check-in field stays null instead of becoming a neutral score`() {
        // Only energy was mentioned. Everything else must come back null, not 5.
        val r = TextLog.validate(rawCheckIn = TextLog.RawCheckIn(energy = 4, soreness = null, stress = null, mood = null, refreshed = null))
        assertEquals(4, r.checkIn!!.energy)
        assertNull(r.checkIn!!.soreness)
        assertNull(r.checkIn!!.stress)
        assertNull(r.checkIn!!.mood)
        assertNull(r.checkIn!!.refreshed)
    }

    @Test
    fun `an out-of-scale rating is discarded, not clamped into a rating never given`() {
        val r = TextLog.validate(rawCheckIn = TextLog.RawCheckIn(energy = 12, soreness = 0, stress = -3, mood = null, refreshed = null))
        assertNull("12 is not a 1-10 answer, and must not become 10", r.checkIn!!.energy)
        assertNull(r.checkIn!!.soreness)
        assertNull(r.checkIn!!.stress)
    }

    @Test
    fun `a difficulty outside 1 to 3 is discarded rather than coerced`() {
        val r = TextLog.validate(rawSets = listOf(set("bench", difficulty = 9)))
        assertNull(r.sets[0].difficulty)
    }

    // ── bodyweight vs absurd loads ───────────────────────────────────────────────────────────

    @Test
    fun `an absent load is a real bodyweight answer, not a refusal`() {
        val r = TextLog.validate(rawSets = listOf(set("pushup", load = null)))
        assertEquals(1, r.sets.size)
        assertEquals(0.0, r.sets[0].load, 0.001)
    }

    @Test
    fun `an absurd or negative load refuses the whole set instead of guessing`() {
        val tooHeavy = TextLog.validate(rawSets = listOf(set("bench", load = 9000.0)))
        assertTrue(tooHeavy.sets.isEmpty())

        val negative = TextLog.validate(rawSets = listOf(set("bench", load = -20.0)))
        assertTrue(negative.sets.isEmpty())
    }

    @Test
    fun `an absurd rep or set count is refused`() {
        assertTrue(TextLog.validate(rawSets = listOf(set("bench", reps = 5000))).sets.isEmpty())
        // Sets clamp rather than refuse -- "did a few sets" misread as 40 is still a real session,
        // whereas 5000 reps is not a plausible reading of anything.
        assertEquals(TextLog.MAX_SETS, TextLog.validate(rawSets = listOf(set("bench", sets = 900))).sets[0].sets)
    }

    // ── cardio ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a real cardio mode with real minutes is kept`() {
        val r = TextLog.validate(rawCardio = listOf(TextLog.RawCardio("AEROBIC_BASE", 25, 2)))
        assertEquals(1, r.cardio.size)
        assertEquals(Cardio.Mode.AEROBIC_BASE, r.cardio[0].mode)
        assertEquals(25, r.cardio[0].minutes)
        assertEquals(2, r.cardio[0].effortRating)
    }

    @Test
    fun `an unknown cardio mode is refused and reported`() {
        val r = TextLog.validate(rawCardio = listOf(TextLog.RawCardio("CROSSFIT_WOD", 30, null)))
        assertTrue(r.cardio.isEmpty())
        assertTrue(r.unknown.contains("CROSSFIT_WOD"))
    }

    @Test
    fun `cardio with no duration is refused rather than assigned a default length`() {
        val r = TextLog.validate(rawCardio = listOf(TextLog.RawCardio("EASY_WALK", null, null)))
        assertTrue(r.cardio.isEmpty())
    }

    @Test
    fun `NONE is not a loggable cardio session`() {
        // "no cardio today" is a decision the engine makes, never a session someone performed.
        val r = TextLog.validate(rawCardio = listOf(TextLog.RawCardio("NONE", 20, null)))
        assertTrue(r.cardio.isEmpty())
    }

    // ── weight ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a plausible bodyweight is kept and an implausible one is dropped`() {
        assertEquals(89.0, TextLog.validate(rawWeightKg = 89.0).weightKg!!, 0.001)
        assertNull(TextLog.validate(rawWeightKg = 890.0).weightKg)
        assertNull(TextLog.validate(rawWeightKg = 3.0).weightKg)
    }

    // ── meals reuse the food table's own validator ───────────────────────────────────────────

    @Test
    fun `meals are validated against the real food table`() {
        val r = TextLog.validate(rawMeals = listOf("roti" to 2.0, "unicornSteak" to 1.0))
        assertEquals(1, r.meals.size)
        assertEquals("roti", r.meals[0].foodId)
        assertTrue(r.unknown.contains("unicornSteak"))
    }

    // ── emptiness and summary ────────────────────────────────────────────────────────────────

    @Test
    fun `nothing recognisable reads as empty`() {
        assertTrue(TextLog.validate().isEmpty)
        // A check-in object where every field failed validation is still empty.
        assertTrue(TextLog.validate(rawCheckIn = TextLog.RawCheckIn(99, 99, 99, 99, null)).isEmpty)
    }

    @Test
    fun `one real field is not empty`() {
        assertFalse(TextLog.validate(rawCheckIn = TextLog.RawCheckIn(5, null, null, null, null)).isEmpty)
        assertFalse(TextLog.validate(rawSets = listOf(set("bench"))).isEmpty)
    }

    @Test
    fun `the summary only ever describes what was actually validated`() {
        val r = TextLog.validate(
            rawSets = listOf(set("bench"), set("legPress3000")),
            rawCardio = listOf(TextLog.RawCardio("EASY_WALK", 20, null)),
        )
        val s = TextLog.summary(r)
        assertTrue(s.contains(EXERCISES.getValue("bench").name))
        assertFalse("a refused entry must never appear as logged", s.contains("legPress3000"))
        assertTrue(s.contains("20 min"))
    }

    @Test
    fun `an empty result says so rather than producing an empty bullet list`() {
        assertEquals("Nothing I could log in that.", TextLog.summary(TextLog.validate()))
    }

    @Test
    fun `bodyweight sets are described as bodyweight, not as zero kilos`() {
        val r = TextLog.validate(rawSets = listOf(set("pushup", load = null)))
        assertTrue(TextLog.summary(r).contains("bodyweight"))
        assertFalse(TextLog.summary(r).contains("0kg"))
    }

    // ── the catalogues handed to the model must be real ──────────────────────────────────────

    @Test
    fun `the exercise catalogue lists every real exercise and nothing else`() {
        val cat = TextLog.exerciseCatalogue()
        assertEquals(EXERCISES.size, cat.lines().size)
        for (id in EXERCISES.keys) assertTrue("$id missing from catalogue", cat.contains(id))
    }

    @Test
    fun `the cardio catalogue offers every mode except NONE`() {
        val cat = TextLog.cardioCatalogue()
        assertEquals(Cardio.Mode.entries.size - 1, cat.lines().size)
        assertFalse("NONE is not something a person can have done", cat.contains("NONE"))
        assertTrue(cat.contains("INTERVALS"))
    }
}
