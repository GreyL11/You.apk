package com.example.domain

import com.example.data.LogEntry

/** A real, standard strength-training classification — not per-user, the same biomechanical
 *  grouping any coach would use for the existing 28-exercise catalogue. Isolation moves (raises,
 *  curls, extensions) are tracked separately since they don't substitute for compound push/pull
 *  volume the way, say, two different rows do. */
enum class MovementPattern { HORIZONTAL_PUSH, VERTICAL_PUSH, HORIZONTAL_PULL, VERTICAL_PULL, SQUAT, HINGE, LUNGE, ISOLATION }

/**
 * What your ACTUAL last few weeks of training covered, by movement pattern — not a schedule, and
 * not a duplicate of [Coach]'s per-exercise progression or [Planner]'s session generation. Neither
 * of those reads whether your logged training has been push-heavy or pull-heavy; this is that read.
 *
 * Deliberately scoped to ONE comparison with a real basis (push vs pull volume balance, the single
 * most commonly cited compound imbalance in real coaching) rather than inventing absolute
 * per-pattern volume targets — those would need real exercise-science volume landmarks this file
 * doesn't have, and a confidently wrong target is worse than one honest comparison.
 */
object TrainingCoverageEngine {
    val PATTERN_BY_EXERCISE: Map<String, MovementPattern> = mapOf(
        "squat" to MovementPattern.SQUAT,
        "rdl" to MovementPattern.HINGE,
        "deadlift" to MovementPattern.HINGE,
        "lunge" to MovementPattern.LUNGE,
        "bench" to MovementPattern.HORIZONTAL_PUSH,
        "inclineBench" to MovementPattern.HORIZONTAL_PUSH,
        "declineBench" to MovementPattern.HORIZONTAL_PUSH,
        "dbBench" to MovementPattern.HORIZONTAL_PUSH,
        "inclineDbPress" to MovementPattern.HORIZONTAL_PUSH,
        "chestDip" to MovementPattern.HORIZONTAL_PUSH,
        "pushup" to MovementPattern.HORIZONTAL_PUSH,
        "dip" to MovementPattern.HORIZONTAL_PUSH,
        "ohp" to MovementPattern.VERTICAL_PUSH,
        "row" to MovementPattern.HORIZONTAL_PULL,
        "cableRow" to MovementPattern.HORIZONTAL_PULL,
        "straightArmPulldown" to MovementPattern.VERTICAL_PULL,
        "latPulldown" to MovementPattern.VERTICAL_PULL,
        "lateralRaise" to MovementPattern.ISOLATION,
        "frontRaise" to MovementPattern.ISOLATION,
        "rearDeltRaise" to MovementPattern.ISOLATION,
        "cableLateralRaise" to MovementPattern.ISOLATION,
        "cableFrontRaise" to MovementPattern.ISOLATION,
        "curl" to MovementPattern.ISOLATION,
        "hammerCurl" to MovementPattern.ISOLATION,
        "cableCurl" to MovementPattern.ISOLATION,
        "pushdown" to MovementPattern.ISOLATION,
        "skullcrusher" to MovementPattern.ISOLATION,
        "overheadExtension" to MovementPattern.ISOLATION,
    )

    /** Below this many logged sessions on a side, there isn't enough real volume yet to call an
     *  imbalance rather than one session's noise. */
    const val MIN_SESSIONS_PER_SIDE = 3

    /** A ratio outside this band is read as a real imbalance — inside it, roughly-equal push/pull
     *  volume, matching common coaching guidance rather than a stricter 1:1 requirement. */
    private const val BALANCE_BAND = 1.3

    data class PatternVolume(val pattern: MovementPattern, val sets: Int, val sessions: Int)

    /** Real total sets per pattern from actually-logged sessions (a session with no set count
     *  recorded counts as at least the one set it definitionally was, never invented beyond that). */
    fun volumeByPattern(logs: List<LogEntry>): List<PatternVolume> =
        logs.mapNotNull { log -> PATTERN_BY_EXERCISE[log.exId]?.let { it to log } }
            .groupBy({ it.first }, { it.second })
            .map { (pattern, entries) -> PatternVolume(pattern, sets = entries.sumOf { it.sets ?: 1 }, sessions = entries.size) }

    enum class Balance { PULL_NEEDS_WORK, PUSH_NEEDS_WORK, BALANCED, INSUFFICIENT_DATA }

    data class PushPullReading(val pushSets: Int, val pullSets: Int, val pushSessions: Int, val pullSessions: Int, val balance: Balance)

    /** Compares total pushing (horizontal + vertical) against total pulling volume over whatever
     *  window [logs] covers. Refuses to call an imbalance until both sides have real, repeated
     *  volume behind them — one heavy bench session does not make pulling "undertrained." */
    fun pushPullBalance(logs: List<LogEntry>): PushPullReading {
        val byPattern = volumeByPattern(logs).associateBy { it.pattern }
        val push = listOfNotNull(byPattern[MovementPattern.HORIZONTAL_PUSH], byPattern[MovementPattern.VERTICAL_PUSH])
        val pull = listOfNotNull(byPattern[MovementPattern.HORIZONTAL_PULL], byPattern[MovementPattern.VERTICAL_PULL])
        val pushSets = push.sumOf { it.sets }
        val pullSets = pull.sumOf { it.sets }
        val pushSessions = push.sumOf { it.sessions }
        val pullSessions = pull.sumOf { it.sessions }

        if (pushSessions < MIN_SESSIONS_PER_SIDE || pullSessions < MIN_SESSIONS_PER_SIDE) {
            return PushPullReading(pushSets, pullSets, pushSessions, pullSessions, Balance.INSUFFICIENT_DATA)
        }
        val ratio = pushSets.toDouble() / pullSets
        val balance = when {
            ratio > BALANCE_BAND -> Balance.PULL_NEEDS_WORK
            ratio < 1.0 / BALANCE_BAND -> Balance.PUSH_NEEDS_WORK
            else -> Balance.BALANCED
        }
        return PushPullReading(pushSets, pullSets, pushSessions, pullSessions, balance)
    }
}
