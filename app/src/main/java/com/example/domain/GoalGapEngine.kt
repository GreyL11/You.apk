package com.example.domain

/**
 * Which supporting objective is most limiting progress right now — read entirely from real
 * per-domain states [HealthStateEngine] and [RecoveryEngine] already computed. This file measures
 * nothing of its own; "primary bottleneck = recovery" is only a claim it can make because those
 * engines already did the real evidence-gathering. No fake universal percentage — every dimension
 * keeps its own independent read.
 */
object GoalGapEngine {
    enum class Dimension { TRAINING_CONSISTENCY, NUTRITION_CONSISTENCY, SLEEP_CONSISTENCY, RECOVERY, HYDRATION, SKIN_ROUTINE }

    enum class Gap { ON_TRACK, MODERATE_GAP, LARGE_GAP, INSUFFICIENT_DATA }

    data class DimensionReading(val dimension: Dimension, val gap: Gap)
    data class Reading(val dimensions: List<DimensionReading>, val bottleneck: Dimension?)

    /** Which dimension wins when more than one has a real gap — sleep and recovery first since
     *  most other dimensions depend on them (a sleep-deprived, under-recovered day makes every
     *  other habit harder), not because their gap is inherently "worse." */
    private val PRIORITY = listOf(
        Dimension.SLEEP_CONSISTENCY, Dimension.RECOVERY, Dimension.TRAINING_CONSISTENCY,
        Dimension.NUTRITION_CONSISTENCY, Dimension.HYDRATION, Dimension.SKIN_ROUTINE,
    )

    private fun gapFor(state: HealthStateEngine.State): Gap = when (state) {
        HealthStateEngine.State.ON_TRACK -> Gap.ON_TRACK
        HealthStateEngine.State.WATCH -> Gap.MODERATE_GAP
        HealthStateEngine.State.NEEDS_ATTENTION -> Gap.LARGE_GAP
        HealthStateEngine.State.INSUFFICIENT_DATA -> Gap.INSUFFICIENT_DATA
    }

    private fun gapForRecovery(state: RecoveryEngine.State): Gap = when (state) {
        RecoveryEngine.State.READY -> Gap.ON_TRACK
        RecoveryEngine.State.MODERATE -> Gap.MODERATE_GAP
        RecoveryEngine.State.RECOVERY_NEEDED -> Gap.LARGE_GAP
        RecoveryEngine.State.INSUFFICIENT_DATA -> Gap.INSUFFICIENT_DATA
    }

    /** A LARGE_GAP anywhere always outranks every MODERATE_GAP — never average severities away. */
    fun evaluate(health: HealthStateEngine.Snapshot, recovery: RecoveryEngine.Reading): Reading {
        val readings = listOf(
            DimensionReading(Dimension.TRAINING_CONSISTENCY, gapFor(health.training)),
            DimensionReading(Dimension.NUTRITION_CONSISTENCY, gapFor(health.nutrition)),
            DimensionReading(Dimension.SLEEP_CONSISTENCY, gapFor(health.sleep)),
            DimensionReading(Dimension.RECOVERY, gapForRecovery(recovery.state)),
            DimensionReading(Dimension.HYDRATION, gapFor(health.hydration)),
            DimensionReading(Dimension.SKIN_ROUTINE, gapFor(health.skinRoutine)),
        )
        val large = readings.filter { it.gap == Gap.LARGE_GAP }.map { it.dimension }.toSet()
        val moderate = readings.filter { it.gap == Gap.MODERATE_GAP }.map { it.dimension }.toSet()
        val bottleneck = when {
            large.isNotEmpty() -> PRIORITY.firstOrNull { it in large }
            moderate.isNotEmpty() -> PRIORITY.firstOrNull { it in moderate }
            else -> null
        }
        return Reading(readings, bottleneck)
    }
}
