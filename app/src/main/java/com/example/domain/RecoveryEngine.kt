package com.example.domain

/**
 * Combines two real, already-computed reads — [HealthStateEngine]'s sleep-vs-personal-baseline and
 * [TrainingLoadEngine]'s volume-vs-personal-baseline — into one recovery signal. Never a
 * physiological measurement (no HRV, no biometric sensor here): a real proxy from real logged
 * behavior, and it says so by naming its own inputs rather than presenting a bare state.
 */
object RecoveryEngine {
    enum class State { READY, MODERATE, RECOVERY_NEEDED, INSUFFICIENT_DATA }

    data class Reading(val state: State, val reasons: List<String>)

    fun evaluate(sleep: HealthStateEngine.State, load: TrainingLoadEngine.State): Reading {
        val sleepKnown = sleep != HealthStateEngine.State.INSUFFICIENT_DATA
        val loadKnown = load != TrainingLoadEngine.State.INSUFFICIENT_DATA
        if (!sleepKnown && !loadKnown) return Reading(State.INSUFFICIENT_DATA, emptyList())

        val reasons = mutableListOf<String>()
        val poorSleep = sleep == HealthStateEngine.State.NEEDS_ATTENTION
        val watchSleep = sleep == HealthStateEngine.State.WATCH
        val highLoad = load == TrainingLoadEngine.State.HIGH_LOAD
        if (poorSleep) reasons.add("sleep well below your normal")
        if (highLoad) reasons.add("recent training load well above your normal")

        val state = when {
            poorSleep && highLoad -> State.RECOVERY_NEEDED
            poorSleep || highLoad -> State.MODERATE
            watchSleep -> State.MODERATE
            !sleepKnown || !loadKnown -> State.MODERATE // real but partial evidence -- deliberately not READY on half a picture
            else -> State.READY
        }
        return Reading(state, reasons)
    }
}
