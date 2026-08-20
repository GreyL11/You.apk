package com.example.domain

import com.example.data.DayRow
import com.example.data.LogEntry
import com.example.data.Meal
import com.example.data.Profile
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * One real-data state per life domain — never a single fake "health score." Every domain's state
 * is independently derived from what's already logged, reading HealthCoachEngine's own real checks
 * (nutrition/hydration), [PersonalBaseline] for personal norms (sleep, training frequency), and
 * [MoodInsights] for real sleep arithmetic. Nothing here is averaged into one number, and every
 * state is INSUFFICIENT_DATA rather than a guess when there isn't enough real evidence.
 */
object HealthStateEngine {
    enum class State { ON_TRACK, WATCH, NEEDS_ATTENTION, INSUFFICIENT_DATA }

    data class Snapshot(
        val nutrition: State,
        val hydration: State,
        val sleep: State,
        val training: State,
        val skinRoutine: State,
    )

    data class Inputs(
        val now: LocalDateTime,
        val todayRow: DayRow?,
        val recentMeals: List<Meal>, // today's meals only
        val allMeals: List<Meal>, // full history, for baseline reads
        val recentLogEntries: List<LogEntry>, // today's training only
        val allLogEntries: List<LogEntry>, // full history
        val allDayRows: List<DayRow>,
        val profile: Profile?,
    )

    fun evaluate(i: Inputs): Snapshot = Snapshot(
        nutrition = nutritionState(i),
        hydration = hydrationState(i),
        sleep = sleepState(i),
        training = trainingState(i),
        skinRoutine = skinState(i),
    )

    // Same real check HealthCoachEngine.candidates() already uses for the nutrition domain — a
    // drink isn't a meal, matching HealthCoachEngine's own comment on why.
    private fun nutritionState(i: Inputs): State {
        val ateToday = i.recentMeals.any { m -> Nutrition.FOODS.find { it.id == m.foodId }?.ml == null }
        return if (ateToday) State.ON_TRACK else State.WATCH
    }

    private fun hydrationState(i: Inputs): State {
        val waterEntries = i.recentMeals.map { m -> m to Nutrition.FOODS.find { it.id == m.foodId }?.ml }
        val haveMl = Nutrition.fluid(waterEntries)
        val targetMl = Nutrition.waterTarget(i.profile)
        if (targetMl == 0 || i.recentMeals.isEmpty()) return State.INSUFFICIENT_DATA
        return when {
            haveMl >= targetMl -> State.ON_TRACK
            haveMl >= targetMl / 2 -> State.WATCH
            else -> State.NEEDS_ATTENTION
        }
    }

    /** Last night against the user's OWN typical, never a generic "8 hours" guideline. */
    private fun sleepState(i: Inputs): State {
        val lastNight = i.todayRow?.let { MoodInsights.sleepSummary(it).main } ?: return State.INSUFFICIENT_DATA
        val baseline = PersonalBaseline.sleepHours(i.allDayRows.filter { it.dayKey != i.todayRow.dayKey })
        val typical = baseline.typical ?: return State.INSUFFICIENT_DATA
        return when {
            lastNight >= typical * 0.9 -> State.ON_TRACK
            lastNight >= typical * 0.75 -> State.WATCH
            else -> State.NEEDS_ATTENTION
        }
    }

    /** Not trained today is not automatically a problem — compared against how many rest days this
     *  person's own real weekly frequency implies, not a fixed "train every day" assumption. */
    private fun trainingState(i: Inputs): State {
        if (i.recentLogEntries.isNotEmpty()) return State.ON_TRACK
        val baseline = PersonalBaseline.trainingSessionsPerWeek(i.allLogEntries, windowDays = 56)
        val perWeek = baseline.typical ?: return State.INSUFFICIENT_DATA
        val lastSessionDay = i.allLogEntries.maxByOrNull { it.at }?.at?.take(10) ?: return State.INSUFFICIENT_DATA
        val daysSince = ChronoUnit.DAYS.between(LocalDate.parse(lastSessionDay), i.now.toLocalDate())
        val expectedGapDays = 7.0 / perWeek
        return when {
            daysSince <= expectedGapDays * 1.5 -> State.ON_TRACK
            daysSince <= expectedGapDays * 2.5 -> State.WATCH
            else -> State.NEEDS_ATTENTION
        }
    }

    private fun skinState(i: Inputs): State = if (i.todayRow?.skin != null) State.ON_TRACK else State.WATCH
}
