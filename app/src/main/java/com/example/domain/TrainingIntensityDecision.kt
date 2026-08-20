package com.example.domain

import kotlin.math.roundToInt

/**
 * "Should today be a full session, a reduced one, or a recovery day?" — built entirely from
 * [RecoveryEngine] and [TrainingLoadEngine]'s already-computed real reads.
 *
 * Deliberately NOT a general decision engine: no candidate generation across exercise selection,
 * no consequence simulation — those would need training-plan detail (planned muscle groups,
 * per-session time budget) this app doesn't have real data for yet. This is the one training
 * question genuinely answerable today from what's already logged, and it says so with an explicit
 * [Confidence] rather than presenting every answer with the same certainty.
 */
object TrainingIntensityDecision {
    enum class Decision { FULL_SESSION, REDUCED_SESSION, RECOVERY_DAY, INSUFFICIENT_DATA }
    enum class Confidence { HIGH, MODERATE, LOW }

    data class Reading(val decision: Decision, val reason: String, val confidence: Confidence)

    /** The real numbers behind a HIGH_LOAD read, so the reason names what actually happened
     *  instead of just a qualifier -- empty when [TrainingLoadEngine] itself had none to give. */
    private fun loadDetail(load: TrainingLoadEngine.Reading): String {
        val typical = load.typicalWeeklySets ?: return ""
        return " (${load.recentSets} sets this week vs your typical ${typical.roundToInt()}/week)"
    }

    fun decide(recovery: RecoveryEngine.Reading, load: TrainingLoadEngine.Reading): Reading {
        val recoveryKnown = recovery.state != RecoveryEngine.State.INSUFFICIENT_DATA
        val loadKnown = load.state != TrainingLoadEngine.State.INSUFFICIENT_DATA

        if (!recoveryKnown && !loadKnown) {
            return Reading(
                Decision.INSUFFICIENT_DATA,
                "Not enough recent sleep and training history yet to make this call responsibly.",
                Confidence.LOW,
            )
        }
        return when {
            recovery.state == RecoveryEngine.State.RECOVERY_NEEDED ->
                Reading(Decision.RECOVERY_DAY, "Recovery needs attention: ${recovery.reasons.joinToString(" and ")}.", Confidence.HIGH)

            recovery.state == RecoveryEngine.State.MODERATE && load.state == TrainingLoadEngine.State.HIGH_LOAD ->
                Reading(Decision.REDUCED_SESSION, "Recent training load is above your normal, and recovery is only moderate right now.${loadDetail(load)}", Confidence.MODERATE)

            load.state == TrainingLoadEngine.State.HIGH_LOAD ->
                Reading(Decision.REDUCED_SESSION, "Recent training load is well above your normal.${loadDetail(load)}", Confidence.MODERATE)

            recovery.state == RecoveryEngine.State.MODERATE ->
                Reading(Decision.REDUCED_SESSION, "Recovery is currently only moderate — a lighter session keeps progress going without adding to it.", Confidence.MODERATE)

            recoveryKnown && loadKnown && recovery.state == RecoveryEngine.State.READY && load.state != TrainingLoadEngine.State.UNDERLOADED ->
                Reading(Decision.FULL_SESSION, "Sleep and recent training load both look normal.", Confidence.HIGH)

            else ->
                // One side (or the "underloaded" case, which never argues against training) is fine,
                // but the other has thin history -- a real, honest, lower-confidence green light.
                Reading(Decision.FULL_SESSION, "No red flags in what's currently tracked, though some of your history is still thin.", Confidence.LOW)
        }
    }
}
