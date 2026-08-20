package com.example.domain

import com.example.data.Profile
import com.example.data.Weight
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/**
 * The fat-loss loop. These test the REFUSALS hardest, because this is the one part of the app that
 * tells someone how much to eat, and a confidently wrong number here is worse than silence.
 */
class NutritionFatLossTest {
    private val today: LocalDate = LocalDate.parse("2026-08-20")

    private fun profile(bw: Double = 80.0, days: Int = 4, kcalTarget: Int? = null) = Profile(
        id = 1, name = "", bodyweight = bw, daysPerWeek = days, bar = 20.0, plates = "[]",
        experience = 1, goal = "hypertrophy", equipment = "[]", injuries = "[]",
        kcalTarget = kcalTarget, poseModel = null,
    )

    /** n days of the same intake — the shape `suggestion` wants. */
    private fun logged(n: Int, kcal: Int) = List(n) { kcal }

    private fun weighIns(vararg pairs: Pair<String, Double>) = pairs.toList()

    // ── targets ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a cut is a real deficit below maintenance, and gaining is above it`() {
        val hold = Nutrition.targets(profile(), Nutrition.Phase.MAINTAIN)!!
        val cut = Nutrition.targets(profile(), Nutrition.Phase.CUT)!!
        val gain = Nutrition.targets(profile(), Nutrition.Phase.GAIN)!!
        assertTrue("a cut must be below maintenance", cut.kcal < hold.kcal)
        assertTrue("gaining must be above maintenance", gain.kcal > hold.kcal)
        // 20% deficit: sustainable, not a crash diet.
        assertEquals(hold.kcal * 0.80, cut.kcal.toDouble(), hold.kcal * 0.03)
    }

    @Test
    fun `protein goes UP on a cut, because that is what decides fat versus muscle`() {
        val hold = Nutrition.targets(profile(), Nutrition.Phase.MAINTAIN)!!
        val cut = Nutrition.targets(profile(), Nutrition.Phase.CUT)!!
        assertTrue("protein must rise in a deficit, got ${cut.protein} vs ${hold.protein}", cut.protein > hold.protein)
        // 2.2 g/kg at 80 kg.
        assertEquals(176, cut.protein)
    }

    @Test
    fun `an explicitly set target replaces the formula, and carbs absorb the change`() {
        val formula = Nutrition.targets(profile(), Nutrition.Phase.CUT)!!
        val fixed = Nutrition.targets(profile(kcalTarget = 1800), Nutrition.Phase.CUT)!!
        assertEquals(1800, fixed.kcal)
        // Protein and fat stay tied to bodyweight; only carbs move.
        assertEquals(formula.protein, fixed.protein)
        assertEquals(formula.fat, fixed.fat)
        assertNotEquals(formula.carbs, fixed.carbs)
    }

    @Test
    fun `no profile means no target rather than a target for a 0 kg person`() {
        assertNull(Nutrition.targets(null, Nutrition.Phase.CUT))
    }

    // ── weight trend ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `the trend reads direction correctly regardless of stored order`() {
        // The order-dependence bug the legacy comment warns about: reading first/last by position
        // off an unsorted array reports LOSS as GAIN, and every piece of advice inverts with it.
        val jumbled = listOf(
            Weight(at = "2026-08-18", kg = 79.0),
            Weight(at = "2026-08-01", kg = 81.0),
            Weight(at = "2026-08-10", kg = 80.0),
        )
        val t = Nutrition.weightTrend(jumbled, today)
        assertEquals(-2.0, t.changeKg!!, 0.001)
        assertEquals(79.0, t.now!!, 0.001)
    }

    @Test
    fun `one weigh-in is a weight but not yet a trend`() {
        val t = Nutrition.weightTrend(listOf(Weight(at = "2026-08-18", kg = 80.0)), today)
        assertEquals(80.0, t.now!!, 0.001)
        assertNull("a single point cannot have a direction", t.changeKg)
    }

    @Test
    fun `weigh-ins older than the window do not count toward the change`() {
        val old = listOf(
            Weight(at = "2026-01-01", kg = 95.0), // far outside 28 days
            Weight(at = "2026-08-18", kg = 80.0),
        )
        val t = Nutrition.weightTrend(old, today)
        assertNull("only one in-window point, so no trend", t.changeKg)
        assertEquals("but the latest weight is still the latest weight", 80.0, t.now!!, 0.001)
    }

    // ── the scale's correction: mostly it must stay quiet ────────────────────────────────────

    @Test
    fun `too little food logged means no suggestion at all`() {
        val s = Nutrition.suggestion(
            profile(), Nutrition.Phase.CUT, logged(6, 2000),
            weighIns("2026-08-01" to 82.0, "2026-08-20" to 81.0),
        )
        assertNull("6 days is under the 7-day floor", s)
    }

    @Test
    fun `too short a span between weigh-ins means no suggestion`() {
        val s = Nutrition.suggestion(
            profile(), Nutrition.Phase.CUT, logged(14, 2000),
            weighIns("2026-08-14" to 82.0, "2026-08-20" to 81.0), // 6 days
        )
        assertNull("under 10 days of span is scale noise, not a trend", s)
    }

    @Test
    fun `obvious under-logging is refused rather than corrected`() {
        // Eating far below a cut target AND not losing means the log is incomplete. Correcting the
        // target here would tell someone to eat even less, built on a number that is simply wrong.
        val t = Nutrition.targets(profile(), Nutrition.Phase.CUT)!!
        val s = Nutrition.suggestion(
            profile(), Nutrition.Phase.CUT, logged(14, (t.kcal * 0.5).toInt()),
            weighIns("2026-08-01" to 80.0, "2026-08-20" to 80.0),
        )
        assertNull("half the target logged is a logging problem, not a metabolism one", s)
    }

    @Test
    fun `not losing on a cut suggests eating LESS, and says so from what you actually ate`() {
        // Flat weight over 19 days on a cut: the deficit is not real.
        val eating = 2400
        val s = Nutrition.suggestion(
            profile(), Nutrition.Phase.CUT, logged(14, eating),
            weighIns("2026-08-01" to 80.0, "2026-08-20" to 80.0),
        )
        assertNotNull("flat weight on a cut is exactly when it should speak", s)
        assertTrue("must suggest eating less than the flat-weight intake", s!!.to < eating)
        assertEquals("anchored to what was actually eaten", eating, s.eating)
        assertTrue(s.reason.contains("not losing"))
    }

    @Test
    fun `losing at the intended rate says nothing, because nothing needs changing`() {
        // 80 kg, cutting at 0.5%/wk = 0.4 kg/wk. Over 19 days that is ~1.1 kg.
        val s = Nutrition.suggestion(
            profile(), Nutrition.Phase.CUT, logged(14, 2200),
            weighIns("2026-08-01" to 80.0, "2026-08-20" to 78.9),
        )
        assertNull("on-target progress needs no correction", s)
    }

    @Test
    fun `a correction never lurches more than a quarter of the current target`() {
        // Absurd loss rate that would otherwise demand an enormous increase.
        val t = Nutrition.targets(profile(), Nutrition.Phase.CUT)!!
        val s = Nutrition.suggestion(
            profile(), Nutrition.Phase.CUT, logged(14, 2000),
            weighIns("2026-08-01" to 90.0, "2026-08-20" to 78.0), // 12 kg in 19 days
        )
        assertNotNull(s)
        assertTrue("clamped above", s!!.to <= (t.kcal * 1.25).toInt() + 5)
        assertTrue("clamped below", s.to >= (t.kcal * 0.75).toInt() - 5)
    }

    // ── the coach line ───────────────────────────────────────────────────────────────────────

    @Test
    fun `with almost no data it asks for data rather than inventing a reading`() {
        val line = Nutrition.coachLine(
            profile(), Nutrition.Phase.CUT, logged(2, 2000),
            Nutrition.weightTrend(emptyList(), today),
        )
        assertTrue(line, line.contains("log a few more days", ignoreCase = true))
    }

    @Test
    fun `food logged but never weighed asks for the scale`() {
        val line = Nutrition.coachLine(
            profile(), Nutrition.Phase.CUT, logged(10, 2000),
            Nutrition.weightTrend(emptyList(), today),
        )
        assertTrue(line, line.contains("Weigh yourself"))
    }

    @Test
    fun `gaining on a cut with a low log is called out as unlogged food, not as biology`() {
        val t = Nutrition.targets(profile(), Nutrition.Phase.CUT)!!
        val line = Nutrition.coachLine(
            profile(), Nutrition.Phase.CUT, logged(10, (t.kcal * 0.7).toInt()),
            Nutrition.weightTrend(
                listOf(Weight(at = "2026-08-01", kg = 80.0), Weight(at = "2026-08-19", kg = 81.5)),
                today,
            ),
        )
        assertTrue(line, line.contains("unlogged"))
    }

    @Test
    fun `losing on a cut is confirmed as working`() {
        val line = Nutrition.coachLine(
            profile(), Nutrition.Phase.CUT, logged(14, 2100),
            Nutrition.weightTrend(
                listOf(Weight(at = "2026-08-01", kg = 81.0), Weight(at = "2026-08-19", kg = 80.0)),
                today,
            ),
        )
        assertTrue(line, line.contains("working"))
    }

    @Test
    fun `no body fat percentage is ever produced anywhere in this loop`() {
        // The app measures mass, not composition. A body-fat number would be invented, and this is
        // the loop someone would most expect to find one in.
        val s = Nutrition.suggestion(
            profile(), Nutrition.Phase.CUT, logged(14, 2400),
            weighIns("2026-08-01" to 80.0, "2026-08-20" to 80.0),
        )
        val line = Nutrition.coachLine(
            profile(), Nutrition.Phase.CUT, logged(14, 2400),
            Nutrition.weightTrend(
                listOf(Weight(at = "2026-08-01", kg = 80.0), Weight(at = "2026-08-19", kg = 80.0)),
                today,
            ),
        )
        val everything = "${s?.reason} $line".lowercase()
        assertFalse(everything.contains("body fat"))
        assertFalse(everything.contains("bodyfat"))
        assertFalse(everything.contains("% fat"))
    }
}
