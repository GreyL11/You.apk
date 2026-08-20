package com.example.domain

import com.example.data.DayRow
import com.example.data.LogEntry
import java.time.LocalDate

/**
 * "How ready is this person to train hard today?"
 *
 * Layered on top of [RecoveryEngine], which already reads sleep-vs-baseline and load-vs-baseline,
 * by adding the real self-reported signals that engine has no access to: today's energy, soreness,
 * stress, whether they woke rested, consecutive training days, and how hard recent sessions
 * actually felt.
 *
 * Deliberately a category, never a percentage. A "recovery score of 72.4%" implies a precision
 * nothing here has — these inputs are a handful of 1-10 self-reports and a sleep estimate, and
 * dressing that up as a decimal would be false confidence. Every reading carries its own
 * [Confidence] so callers can say how much to trust it.
 */
object ReadinessEngine {
    enum class Level { EXCELLENT, GOOD, MODERATE, LOW, VERY_LOW, INSUFFICIENT_DATA }

    enum class Confidence { HIGH, MODERATE, LOW, INSUFFICIENT }

    data class Reading(
        val level: Level,
        val confidence: Confidence,
        /** Real, human-readable factors that moved this reading — the "why", straight from inputs. */
        val factors: List<String>,
    )

    /** A 1-10 self-report at or below this is genuinely low. */
    const val LOW_SELF_REPORT = 4

    /** Soreness at or above this (of 10) is real, training-relevant soreness. */
    const val HIGH_SORENESS = 7

    /** Training this many days straight is a real accumulated-fatigue signal on its own. */
    const val LONG_STREAK = 4

    /**
     * @param todayRow today's check-in, if it exists. Absent fields are absent, never neutral.
     * @param recovery [RecoveryEngine]'s already-computed sleep/load read.
     */
    fun evaluate(
        recovery: RecoveryEngine.Reading,
        todayRow: DayRow?,
        logs: List<LogEntry>,
        today: LocalDate,
    ): Reading {
        val energy = todayRow?.energy
        val soreness = todayRow?.soreness
        val stress = todayRow?.stress
        val refreshed = todayRow?.refreshed
        val streak = TrainingHistory.consecutiveTrainingDays(logs, today)
        val recentDifficulty = TrainingHistory.recentDifficulty(logs)

        val selfReports = listOfNotNull(energy, soreness, stress).size + listOfNotNull(refreshed).size
        val recoveryKnown = recovery.state != RecoveryEngine.State.INSUFFICIENT_DATA

        if (!recoveryKnown && selfReports == 0) {
            return Reading(Level.INSUFFICIENT_DATA, Confidence.INSUFFICIENT, emptyList())
        }

        // Real negative signals, each named so the UI can show the actual reason.
        val negatives = mutableListOf<String>()
        val positives = mutableListOf<String>()

        if (recovery.state == RecoveryEngine.State.RECOVERY_NEEDED) negatives.addAll(recovery.reasons)
        else if (recovery.state == RecoveryEngine.State.MODERATE) recovery.reasons.forEach { negatives.add(it) }

        if (energy != null && energy <= LOW_SELF_REPORT) negatives.add("energy is low today ($energy/10)")
        if (soreness != null && soreness >= HIGH_SORENESS) negatives.add("you reported significant soreness ($soreness/10)")
        if (stress != null && stress >= 8) negatives.add("stress is high today ($stress/10)")
        if (refreshed == false) negatives.add("you didn't wake up feeling rested")
        if (streak >= LONG_STREAK) negatives.add("you've trained $streak days in a row")
        if (recentDifficulty != null && recentDifficulty >= TrainingHistory.TOO_HARD) {
            negatives.add("recent sessions have felt hard")
        }

        if (energy != null && energy >= 8) positives.add("energy is high today ($energy/10)")
        if (refreshed == true) positives.add("you woke up feeling rested")
        if (recovery.state == RecoveryEngine.State.READY) positives.add("sleep and recent load both look normal")
        if (recentDifficulty != null && recentDifficulty <= TrainingHistory.TOO_EASY) {
            positives.add("recent sessions have felt comfortable")
        }

        // Severity matters more than count: one severe signal (recovery needed, or a genuinely
        // low self-report paired with anything else) outranks several mild ones.
        val severe = recovery.state == RecoveryEngine.State.RECOVERY_NEEDED ||
            (soreness != null && soreness >= 9) ||
            (energy != null && energy <= 2)

        val level = when {
            severe && negatives.size >= 2 -> Level.VERY_LOW
            severe -> Level.LOW
            negatives.size >= 3 -> Level.LOW
            negatives.size >= 1 -> Level.MODERATE
            positives.size >= 2 -> Level.EXCELLENT
            positives.isNotEmpty() -> Level.GOOD
            else -> Level.MODERATE
        }

        // Confidence is about how much real evidence backs the reading, not how extreme it is.
        val confidence = when {
            recoveryKnown && selfReports >= 3 -> Confidence.HIGH
            recoveryKnown && selfReports >= 1 -> Confidence.MODERATE
            recoveryKnown || selfReports >= 2 -> Confidence.LOW
            else -> Confidence.INSUFFICIENT
        }

        val factors = if (negatives.isNotEmpty()) negatives else positives
        return Reading(level, confidence, factors)
    }

    /** Is hard work (intervals, aggressive progression) defensible at this readiness? */
    fun allowsHardWork(level: Level): Boolean = level == Level.EXCELLENT || level == Level.GOOD

    /** Should today favour recovery over any real training stimulus? */
    fun needsRecovery(level: Level): Boolean = level == Level.LOW || level == Level.VERY_LOW
}
