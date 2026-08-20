package com.example.domain

import com.example.data.DayRow
import com.example.data.LogEntry
import com.example.data.Meal
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class HealthStateEngineTest {
    private val now = LocalDateTime.parse("2026-08-20T13:00:00")

    private fun inputs(
        todayRow: DayRow? = null,
        recentMeals: List<Meal> = emptyList(),
        allMeals: List<Meal> = recentMeals,
        recentLogEntries: List<LogEntry> = emptyList(),
        allLogEntries: List<LogEntry> = recentLogEntries,
        allDayRows: List<DayRow> = listOfNotNull(todayRow),
    ) = HealthStateEngine.Inputs(
        now = now, todayRow = todayRow, recentMeals = recentMeals, allMeals = allMeals,
        recentLogEntries = recentLogEntries, allLogEntries = allLogEntries, allDayRows = allDayRows, profile = null,
    )

    @Test
    fun `nutrition is ON_TRACK once real food is logged, WATCH when only drinks are logged`() {
        assertEquals(HealthStateEngine.State.WATCH, HealthStateEngine.evaluate(inputs()).nutrition)
        val withFood = inputs(recentMeals = listOf(Meal(at = "$now", foodId = "rice", qty = 1.0)))
        assertEquals(HealthStateEngine.State.ON_TRACK, HealthStateEngine.evaluate(withFood).nutrition)
        val onlyWater = inputs(recentMeals = listOf(Meal(at = "$now", foodId = "water", qty = 2.0)))
        assertEquals(HealthStateEngine.State.WATCH, HealthStateEngine.evaluate(onlyWater).nutrition)
    }

    @Test
    fun `hydration is INSUFFICIENT_DATA without a profile, since there is no real target to compare against`() {
        val withWater = inputs(recentMeals = listOf(Meal(at = "$now", foodId = "water", qty = 4.0)))
        assertEquals(HealthStateEngine.State.INSUFFICIENT_DATA, HealthStateEngine.evaluate(withWater).hydration)
    }

    @Test
    fun `sleep is INSUFFICIENT_DATA with no baseline history, even if last night was logged`() {
        val row = DayRow(dayKey = "2026-08-20", mood = null, bed = null, wake = null, sleeps = """[{"duration":7.0}]""", plans = null, skin = null)
        val i = inputs(todayRow = row, allDayRows = listOf(row)) // only 1 day of history, no baseline possible
        assertEquals(HealthStateEngine.State.INSUFFICIENT_DATA, HealthStateEngine.evaluate(i).sleep)
    }

    @Test
    fun `sleep reads ON_TRACK near baseline and NEEDS_ATTENTION well below it`() {
        val history = (1..5).map { DayRow(dayKey = "2026-08-0$it", mood = null, bed = null, wake = null, sleeps = """[{"duration":8.0}]""", plans = null, skin = null) }
        val today = DayRow(dayKey = "2026-08-20", mood = null, bed = null, wake = null, sleeps = """[{"duration":7.6}]""", plans = null, skin = null)
        val onTrack = inputs(todayRow = today, allDayRows = history + today)
        assertEquals(HealthStateEngine.State.ON_TRACK, HealthStateEngine.evaluate(onTrack).sleep)

        val poorNight = today.copy(sleeps = """[{"duration":5.0}]""")
        val needsAttention = inputs(todayRow = poorNight, allDayRows = history + poorNight)
        assertEquals(HealthStateEngine.State.NEEDS_ATTENTION, HealthStateEngine.evaluate(needsAttention).sleep)
    }

    @Test
    fun `training today is always ON_TRACK regardless of baseline`() {
        val log = LogEntry(exId = "squat", at = "$now", reps = 5, sets = 1, load = 50.0, faultEvents = "[]", correctedFrom = null)
        assertEquals(HealthStateEngine.State.ON_TRACK, HealthStateEngine.evaluate(inputs(recentLogEntries = listOf(log))).training)
    }

    @Test
    fun `a rest day within this person's own real rhythm is not flagged as a problem`() {
        // Trains 3x/week historically (12 sessions over 28 days), last session 2 days ago.
        // Expected gap ~2.33 days, so 2 days since is comfortably ON_TRACK, not NEEDS_ATTENTION.
        val history = (0 until 12).map { i ->
            LogEntry(exId = "squat", at = now.minusDays((2 + i * 2).toLong()).toString(), reps = 5, sets = 1, load = 50.0, faultEvents = "[]", correctedFrom = null)
        }
        val state = HealthStateEngine.evaluate(inputs(allLogEntries = history)).training
        assertEquals(HealthStateEngine.State.ON_TRACK, state)
    }

    @Test
    fun `a real gap far beyond this person's own rhythm reads NEEDS_ATTENTION`() {
        val history = (0 until 12).map { i ->
            LogEntry(exId = "squat", at = now.minusDays((20 + i * 2).toLong()).toString(), reps = 5, sets = 1, load = 50.0, faultEvents = "[]", correctedFrom = null)
        }
        val state = HealthStateEngine.evaluate(inputs(allLogEntries = history)).training
        assertEquals(HealthStateEngine.State.NEEDS_ATTENTION, state)
    }

    @Test
    fun `skin routine reflects whether today's row actually has one logged`() {
        val logged = DayRow(dayKey = "2026-08-20", mood = null, bed = null, wake = null, sleeps = null, plans = null, skin = "{}")
        assertEquals(HealthStateEngine.State.ON_TRACK, HealthStateEngine.evaluate(inputs(todayRow = logged)).skinRoutine)
        assertEquals(HealthStateEngine.State.WATCH, HealthStateEngine.evaluate(inputs()).skinRoutine)
    }
}
