package com.example.domain

import com.example.data.ActionOutcome
import com.example.data.DayRow
import com.example.data.LogEntry
import com.example.data.Meal
import com.example.data.Profile
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

/**
 * The hydration/skinRoutine/training/hormonalLifestyle branches, isSuppressed, and
 * selectNextBestAction's cross-domain tie-break — the parts of the most-wired engine in the app
 * that HealthCoachMealAskTest.kt (nutrition only) doesn't cover.
 */
class HealthCoachEngineTest {
    private val now: LocalDateTime = LocalDateTime.parse("2026-08-20T13:00:00")

    private fun ctx(
        todayRow: DayRow? = null,
        recentMeals: List<Meal> = emptyList(),
        recentLogEntries: List<LogEntry> = emptyList(),
        recentOutcomes: List<ActionOutcome> = emptyList(),
        profile: Profile? = null,
    ) = HealthCoachEngine.Context(
        now = now, todayRow = todayRow, recentMeals = recentMeals, recentLogEntries = recentLogEntries,
        recentOutcomes = recentOutcomes, totalHistoricalLogs = 20, profile = profile,
    )

    private fun byDomain(c: HealthCoachEngine.Context, domain: String) =
        HealthCoachEngine.candidates(c).single { it.domain == domain }

    // ── no evidence ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `zero history offers only a baseline candidate, never a fake actionable one`() {
        val fresh = ctx().copy(totalHistoricalLogs = 0)
        val all = HealthCoachEngine.candidates(fresh)
        assertEquals(listOf("build_baseline"), all.map { it.actionId })
    }

    // ── hydration ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `no profile and nothing logged is DATA_COLLECTION, not a false GOING_WELL`() {
        val c = byDomain(ctx(), "hydration")
        assertEquals("hydrate_now", c.actionId)
        assertEquals(HealthCoachEngine.Tier.DATA_COLLECTION, c.tier)
    }

    @Test
    fun `real fluid below a real target is ACTIONABLE_NOW`() {
        val profile = Profile(name = "t", bodyweight = 80.0, daysPerWeek = 3, bar = 20.0, plates = "[]", experience = 1, goal = "g", equipment = "[]", injuries = "[]", kcalTarget = null, poseModel = null)
        // target = round100(80*35 + 500*3/7) = round100(2800+214) = 3000
        val meals = listOf(Meal(at = "$now", foodId = "water", qty = 2.0)) // 500ml, well under 3000
        val c = byDomain(ctx(recentMeals = meals, profile = profile), "hydration")
        assertEquals("hydrate_now", c.actionId)
        assertEquals(HealthCoachEngine.Tier.ACTIONABLE_NOW, c.tier)
    }

    @Test
    fun `real fluid meeting a real target is GOING_WELL`() {
        val profile = Profile(name = "t", bodyweight = 40.0, daysPerWeek = 0, bar = 20.0, plates = "[]", experience = 1, goal = "g", equipment = "[]", injuries = "[]", kcalTarget = null, poseModel = null)
        // target = round100(40*35 + 0) = 1400
        val meals = listOf(Meal(at = "$now", foodId = "water", qty = 6.0)) // 1500ml
        val c = byDomain(ctx(recentMeals = meals, profile = profile), "hydration")
        assertEquals("hydrate_maintain", c.actionId)
        assertEquals(HealthCoachEngine.Tier.GOING_WELL, c.tier)
    }

    // ── skinRoutine ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `no skin logged today asks for it, logged today reads GOING_WELL`() {
        val notLogged = byDomain(ctx(todayRow = DayRow(dayKey = "2026-08-20", mood = null, bed = null, wake = null, sleeps = null, plans = null, skin = null)), "skinRoutine")
        assertEquals("skin_log", notLogged.actionId)
        assertEquals(HealthCoachEngine.Tier.DATA_COLLECTION, notLogged.tier)

        val logged = byDomain(ctx(todayRow = DayRow(dayKey = "2026-08-20", mood = null, bed = null, wake = null, sleeps = null, plans = null, skin = "{}")), "skinRoutine")
        assertEquals("skin_good", logged.actionId)
        assertEquals(HealthCoachEngine.Tier.GOING_WELL, logged.tier)
    }

    // ── training ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `no training today is ACTIONABLE_NOW, trained today is GOING_WELL`() {
        val untrained = byDomain(ctx(), "training")
        assertEquals("train_today", untrained.actionId)
        assertEquals(HealthCoachEngine.Tier.ACTIONABLE_NOW, untrained.tier)

        val trained = byDomain(ctx(recentLogEntries = listOf(LogEntry(exId = "squat", at = "$now", reps = 5, sets = 1, load = 50.0, faultEvents = "[]", correctedFrom = null))), "training")
        assertEquals("train_rest", trained.actionId)
        assertEquals(HealthCoachEngine.Tier.GOING_WELL, trained.tier)
    }

    // ── hormonalLifestyle ────────────────────────────────────────────────────────────────────

    @Test
    fun `no sleep logged asks to track it, logged reads GOING_WELL`() {
        val noSleep = byDomain(ctx(todayRow = DayRow(dayKey = "2026-08-20", mood = null, bed = null, wake = null, sleeps = null, plans = null, skin = null)), "hormonalLifestyle")
        assertEquals("hormone_sleep", noSleep.actionId)
        assertEquals(HealthCoachEngine.Tier.DATA_COLLECTION, noSleep.tier)

        val slept = byDomain(ctx(todayRow = DayRow(dayKey = "2026-08-20", mood = null, bed = null, wake = null, sleeps = "[]", plans = null, skin = null)), "hormonalLifestyle")
        assertEquals("hormone_good", slept.actionId)
        assertEquals(HealthCoachEngine.Tier.GOING_WELL, slept.tier)
    }

    // ── isSuppressed / selectNextBestAction ──────────────────────────────────────────────────

    @Test
    fun `completed or skipped suppresses only for the rest of the same calendar day`() {
        val completedToday = listOf(ActionOutcome(at = "$now", actionId = "train_today", domain = "training", event = HealthCoachEngine.ActionState.COMPLETED.name))
        assertTrue(HealthCoachEngine.isSuppressed("train_today", ctx(recentOutcomes = completedToday)))

        val skippedYesterday = listOf(ActionOutcome(at = "2026-08-19T09:00:00", actionId = "train_today", domain = "training", event = HealthCoachEngine.ActionState.SKIPPED.name))
        assertFalse(HealthCoachEngine.isSuppressed("train_today", ctx(recentOutcomes = skippedYesterday)))
    }

    @Test
    fun `postponed suppresses only inside the window, cancelled never suppresses`() {
        val postponedRecently = listOf(ActionOutcome(at = now.minusMinutes(30).toString(), actionId = "x", domain = "training", event = HealthCoachEngine.ActionState.POSTPONED.name))
        assertTrue(HealthCoachEngine.isSuppressed("x", ctx(recentOutcomes = postponedRecently)))

        val postponedLongAgo = listOf(ActionOutcome(at = now.minusMinutes(200).toString(), actionId = "x", domain = "training", event = HealthCoachEngine.ActionState.POSTPONED.name))
        assertFalse(HealthCoachEngine.isSuppressed("x", ctx(recentOutcomes = postponedLongAgo)))

        val cancelledJustNow = listOf(ActionOutcome(at = "$now", actionId = "x", domain = "training", event = HealthCoachEngine.ActionState.CANCELLED.name))
        assertFalse(HealthCoachEngine.isSuppressed("x", ctx(recentOutcomes = cancelledJustNow)))
    }

    @Test
    fun `selectNextBestAction picks the sole ACTIONABLE_NOW candidate over four DATA_COLLECTION ones`() {
        val nba = HealthCoachEngine.selectNextBestAction(ctx())
        assertNotNull(nba)
        assertEquals("training", nba?.domain)
        assertEquals(HealthCoachEngine.Tier.ACTIONABLE_NOW, nba?.tier)
    }

    @Test
    fun `suppressing the current best action lets the next-priority one surface`() {
        val trainedToday = listOf(LogEntry(exId = "squat", at = "$now", reps = 5, sets = 1, load = 50.0, faultEvents = "[]", correctedFrom = null))
        // With training already GOING_WELL, nutrition (meal_log, DATA_COLLECTION) is next.
        val nba = HealthCoachEngine.selectNextBestAction(ctx(recentLogEntries = trainedToday))
        assertNotNull(nba)
        assertEquals("nutrition", nba?.domain)
        assertEquals("meal_log", nba?.actionId)
    }
}
