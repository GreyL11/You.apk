package com.example.domain

import com.example.data.LogEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * What the app actually remembers about training, so the person never has to.
 *
 * Every function here answers one factual question from real logged sets — "when did I last train
 * this pattern", "how many days in a row have I trained", "what has been neglected". Nothing is
 * inferred or scored; that is [ReadinessEngine] and [DailyDecisionEngine]'s job. Reuses
 * [TrainingCoverageEngine.PATTERN_BY_EXERCISE] as the single source of movement-pattern truth
 * rather than defining a second, divergent mapping.
 */
object TrainingHistory {
    /** Patterns a balanced week should actually cover. Isolation is deliberately excluded: curls
     *  and raises are fine to do, but "you haven't done isolation in 9 days" is not a real gap
     *  the way "you haven't trained legs in 9 days" is. */
    val BALANCED_PATTERNS = listOf(
        MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH,
        MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PULL,
        MovementPattern.SQUAT, MovementPattern.HINGE, MovementPattern.LUNGE,
    )

    /** Lower-body patterns, the ones most commonly neglected in favour of chest/arms work. */
    val LOWER_BODY = listOf(MovementPattern.SQUAT, MovementPattern.HINGE, MovementPattern.LUNGE)

    /** Beyond this many days without a pattern, it counts as genuinely neglected rather than just
     *  "not yesterday" — a little over a week, so a normal 3-4 day split never trips it. */
    const val NEGLECTED_AFTER_DAYS = 9

    /** A pattern trained within this many days is too recent to prioritize again. Two days of rest
     *  between hitting the same movement pattern is the common floor in real programming. */
    const val TRAINED_RECENTLY_DAYS = 2

    /** Distinct calendar days that have any logged set, most recent first. */
    fun trainingDays(logs: List<LogEntry>): List<String> =
        logs.map { it.at.take(10) }.distinct().sortedDescending()

    /**
     * Days since a pattern was last trained. Null means never in [logs] — which is different from
     * "a long time ago" and callers must handle it as such, not substitute a large number.
     */
    fun daysSincePattern(logs: List<LogEntry>, pattern: MovementPattern, today: LocalDate): Int? {
        val last = logs
            .filter { TrainingCoverageEngine.PATTERN_BY_EXERCISE[it.exId] == pattern }
            .maxByOrNull { it.at.take(10) }
            ?: return null
        return ChronoUnit.DAYS.between(LocalDate.parse(last.at.take(10)), today).toInt()
    }

    /** Days since each pattern, for every pattern in [BALANCED_PATTERNS]. Absent keys are patterns
     *  never trained at all. */
    fun patternRecency(logs: List<LogEntry>, today: LocalDate): Map<MovementPattern, Int> =
        BALANCED_PATTERNS.mapNotNull { p -> daysSincePattern(logs, p, today)?.let { p to it } }.toMap()

    /** Consecutive calendar days trained up to and including [today]. Zero when nothing was logged
     *  today — this counts an unbroken current streak, not the longest streak ever. */
    fun consecutiveTrainingDays(logs: List<LogEntry>, today: LocalDate): Int {
        val days = trainingDays(logs).toSet()
        var count = 0
        var cursor = today
        while (days.contains(cursor.toString())) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /** Days since ANY training. Null when nothing has ever been logged. */
    fun daysSinceLastSession(logs: List<LogEntry>, today: LocalDate): Int? {
        val last = trainingDays(logs).firstOrNull() ?: return null
        return ChronoUnit.DAYS.between(LocalDate.parse(last), today).toInt()
    }

    /**
     * Patterns not trained for longer than [NEGLECTED_AFTER_DAYS], plus patterns never trained at
     * all — but only once there's real history to judge against. With almost nothing logged,
     * everything would look "neglected", which is true but useless: at that point the honest read
     * is "not enough history", handled by the caller via [hasEnoughHistory].
     */
    fun neglectedPatterns(logs: List<LogEntry>, today: LocalDate): List<MovementPattern> {
        val recency = patternRecency(logs, today)
        return BALANCED_PATTERNS.filter { p ->
            val since = recency[p]
            since == null || since > NEGLECTED_AFTER_DAYS
        }
    }

    /** Patterns trained within [TRAINED_RECENTLY_DAYS] — the ones not to prioritize again today. */
    fun recentlyTrainedPatterns(logs: List<LogEntry>, today: LocalDate): List<MovementPattern> =
        patternRecency(logs, today).filter { it.value <= TRAINED_RECENTLY_DAYS }.keys.toList()

    /** Below a couple of weeks of real logged days, pattern-balance claims aren't yet meaningful. */
    const val MIN_DAYS_FOR_BALANCE = 6

    fun hasEnoughHistory(logs: List<LogEntry>): Boolean =
        trainingDays(logs).size >= MIN_DAYS_FOR_BALANCE

    /**
     * Real user-reported difficulty across the most recent [sessions] logged days, averaged per day
     * then over days, so one 12-set day doesn't outweigh three 3-set days. Null when nobody
     * answered — never a neutral 2.
     */
    fun recentDifficulty(logs: List<LogEntry>, sessions: Int = 3): Double? {
        val byDay = logs.filter { it.difficulty != null }.groupBy { it.at.take(10) }
        if (byDay.isEmpty()) return null
        val perDay = byDay.keys.sortedDescending().take(sessions).map { day ->
            byDay.getValue(day).mapNotNull { it.difficulty }.average()
        }
        return if (perDay.isEmpty()) null else perDay.average()
    }

    /** Difficulty at or above this (of 3) across recent sessions reads as genuinely too hard. */
    const val TOO_HARD = 2.7

    /** Difficulty at or below this reads as genuinely easy — room to progress. */
    const val TOO_EASY = 1.4
}
