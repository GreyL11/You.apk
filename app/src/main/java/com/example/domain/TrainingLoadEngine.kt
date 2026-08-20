package com.example.domain

import com.example.data.LogEntry
import java.time.LocalDate

/**
 * Real recent training volume against THIS person's own historical average — never an absolute
 * "you need N sets/week" target, since that needs population exercise-science norms this file
 * doesn't have real numbers for. High or low is only ever relative to what this person has
 * actually been doing.
 */
object TrainingLoadEngine {
    enum class State { UNDERLOADED, APPROPRIATE, HIGH_LOAD, INSUFFICIENT_DATA }

    data class Reading(val recentSets: Int, val typicalWeeklySets: Double?, val state: State)

    /** Below this many prior weeks with any real logged volume, there's no real "typical" to
     *  compare the current week against. */
    private const val MIN_WEEKS_HISTORY = 3

    /** Total real logged sets in the trailing 7 days (including [nowDayKey]) against the average
     *  of the [priorWeeks] full weeks immediately before that — both computed from the same [logs]. */
    fun evaluate(logs: List<LogEntry>, nowDayKey: String, priorWeeks: Int = 4): Reading {
        val now = LocalDate.parse(nowDayKey)
        val recentStart = now.minusDays(6).toString()
        val recentSets = logs.filter { it.at.take(10) in recentStart..nowDayKey }.sumOf { it.sets ?: 1 }

        val weeklyTotals = (1..priorWeeks).map { w ->
            val end = now.minusDays((7 * w).toLong())
            val start = end.minusDays(6)
            logs.filter { it.at.take(10) in start.toString()..end.toString() }.sumOf { it.sets ?: 1 }
        }.filter { it > 0 }

        if (weeklyTotals.size < MIN_WEEKS_HISTORY) return Reading(recentSets, null, State.INSUFFICIENT_DATA)
        val typical = weeklyTotals.average()
        val state = when {
            recentSets > typical * 1.4 -> State.HIGH_LOAD
            recentSets < typical * 0.5 -> State.UNDERLOADED
            else -> State.APPROPRIATE
        }
        return Reading(recentSets, typical, state)
    }
}
