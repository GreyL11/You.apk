package com.example.domain

import com.example.data.ActionOutcome
import com.example.data.Meal
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

/**
 * The coach asking what you ate — the one candidate that is a question rather than an instruction,
 * because the app genuinely does not know and must not guess.
 */
class HealthCoachMealAskTest {
    private val now: LocalDateTime = LocalDateTime.parse("2026-08-18T13:00:00")

    private fun meal(foodId: String) = Meal(id = 1, at = "$now", foodId = foodId, qty = 1.0)

    private fun ctx(meals: List<Meal>) = HealthCoachEngine.Context(
        now = now,
        todayRow = null,
        recentMeals = meals,
        recentLogEntries = emptyList(),
        recentOutcomes = emptyList(),
        // Not a fresh install: with zero history the engine deliberately offers only build_baseline.
        totalHistoricalLogs = 12,
        profile = null,
    )

    private fun nutrition(meals: List<Meal>) =
        HealthCoachEngine.candidates(ctx(meals)).single { it.domain == "nutrition" }

    @Test
    fun `nothing eaten today is asked about, not assumed`() {
        val c = nutrition(emptyList())
        assertEquals("meal_log", c.actionId)
        assertEquals("What did you have today?", c.title)
        assertEquals(HealthCoachEngine.Tier.DATA_COLLECTION, c.tier)
    }

    @Test
    fun `a drink is not a meal, so the question still gets asked`() {
        // Water, coffee and chai all carry ml: they are already counted as hydration, and a day of
        // them is not a day of eating.
        listOf("water", "coffee", "chai", "milk").forEach { drink ->
            assertEquals(
                "logging $drink should not count as food",
                "meal_log",
                nutrition(listOf(meal(drink))).actionId,
            )
        }
    }

    @Test
    fun `once food is logged the question stops`() {
        val c = nutrition(listOf(meal("rice")))
        assertEquals("meal_logged", c.actionId)
        assertEquals(HealthCoachEngine.Tier.GOING_WELL, c.tier)
    }

    @Test
    fun `the ask is allowed to become a notification, the all-clear is not`() {
        val asked = nutrition(emptyList())
        assertTrue(
            NotificationDecisionEngine.shouldNotify(asked, emptyList(), now, isQuietHours = false),
        )
        // GOING_WELL never interrupts anyone.
        val fine = nutrition(listOf(meal("rice")))
        assertFalse(
            NotificationDecisionEngine.shouldNotify(fine, emptyList(), now, isQuietHours = false),
        )
        // Nor does anything at 2am.
        assertFalse(
            NotificationDecisionEngine.shouldNotify(asked, emptyList(), now, isQuietHours = true),
        )
    }

    @Test
    fun `answering it today stops it being asked again today`() {
        val answered = listOf(
            ActionOutcome(
                at = "$now",
                actionId = "meal_log",
                domain = "nutrition",
                event = HealthCoachEngine.ActionState.COMPLETED.name,
            ),
        )
        val suppressed = HealthCoachEngine.Context(
            now = now.plusHours(3),
            todayRow = null,
            recentMeals = emptyList(),
            recentLogEntries = emptyList(),
            recentOutcomes = answered,
            totalHistoricalLogs = 12,
            profile = null,
        )
        assertTrue(HealthCoachEngine.isSuppressed("meal_log", suppressed))
    }

    @Test
    fun `a fresh install is still asked to build a baseline first, not quizzed on meals`() {
        val fresh = HealthCoachEngine.candidates(
            HealthCoachEngine.Context(
                now = now,
                todayRow = null,
                recentMeals = emptyList(),
                recentLogEntries = emptyList(),
                recentOutcomes = emptyList(),
                totalHistoricalLogs = 0,
                profile = null,
            ),
        )
        assertEquals(listOf("build_baseline"), fresh.map { it.actionId })
    }
}
