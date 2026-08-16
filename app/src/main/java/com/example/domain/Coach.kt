package com.example.domain

import com.example.data.LogEntry
import kotlin.math.roundToInt

object Coach {
    const val CLEAN_FAULTS_PER_REP = 0.34
    const val STALL_LIMIT = 3
    const val DELOAD_FACTOR = 0.9

    enum class Progression {
        INCREASE, HOLD, DELOAD
    }

    // unit distinguishes what `nextLoad` actually means: a bodyweight lift (loadRatio 0 in the
    // Exercises catalogue — chestDip/pushup/dip) progresses its REP TARGET, never an invented
    // load, matching legacy coach.js's preview(): "Nothing to add weight to on a push-up, so the
    // rep target goes up instead." This is the one behavioral gap source comparison actually
    // found in this file (see P0-3's audit) — the rest of evaluateSession is unchanged.
    enum class Unit { KG, REPS }

    data class ProgressionResult(
        val progression: Progression,
        val nextLoad: Double,
        val estimated1RM: Double,
        val isClean: Boolean,
        val unit: Unit = Unit.KG,
        // KNOWN GAP: legacy separately tracks "were target reps hit" (`allReps`) from "was the
        // fault rate clean" — two different conditions producing two different reasons ("reps
        // missed" vs "form broke down"). This function has no `targetReps` input to make that
        // distinction, so the not-clean/not-deload case is reported under the one reason this
        // code can actually support rather than a guessed one. Not fixed here — see the P0-3
        // report — because closing it needs a signature change, not the surgical patch this
        // pass is scoped to.
        val reason: String = "",
    )

    fun estimate1RM(reps: Int, load: Double): Double {
        if (reps <= 0 || load < 0.0) return 0.0
        return load * (1.0 + reps / 30.0)
    }

    fun isExecutionClean(reps: Int, faultCount: Int): Boolean {
        if (reps <= 0) return false
        return (faultCount.toDouble() / reps) < CLEAN_FAULTS_PER_REP
    }

    /** Snap to something the bar can actually be loaded to — port of coach.js's `loadable()`.
     *  `exId == null` (the pre-P0-3 call shape, still used by existing callers/tests) means there
     *  is no exercise context to snap against, so it degrades to the flat, unsnapped increment
     *  rather than throwing — never a null-pointer crash on a legitimate, already-supported call. */
    private fun loadable(exId: String?, kg: Double, profile: TrainingProfile): Double =
        if (exId != null && EXERCISES[exId]?.equipment == "barbell") Planner.achievableLoad(kg, profile) else kg

    // Unchanged from before this pass — nearest 0.5, not legacy insights.js's nearest-2.5
    // `round2()`. That granularity difference is a real, separate gap this surgical patch does
    // NOT touch (see the P0-3 report): fixing it would change existing, already-passing
    // CoachTest.kt expectations for a value this pass wasn't asked to correct. `loadable()`
    // layers real plate-snapping on top of this when an exId/barbell context is actually given.
    private fun deloadTo(load: Double) = (load * DELOAD_FACTOR * 2.0).roundToInt() / 2.0

    /**
     * @param exId matches the `Exercises` catalogue entry this session was — needed to know
     *  whether it's a bodyweight lift (progresses reps) and to snap a loaded lift's next weight
     *  to what the profile's actual bar/plates can make, instead of a flat, unsnapped `+2.5`.
     * @param profile supplies the plate set for snapping; defaults to legacy's own DEFAULT_PROFILE
     *  equivalent when the caller has no real persisted profile yet (see TodayViewModel — the
     *  Profile table is not populated by any current screen, so this mirrors planner.js's own
     *  `getProfile() = {...DEFAULT_PROFILE, ...stored}` fallback rather than inventing one).
     */
    fun evaluateSession(
        history: List<LogEntry>,
        currentReps: Int,
        currentLoad: Double,
        currentFaultCount: Int,
        exId: String? = null,
        profile: TrainingProfile = TrainingProfile(),
    ): ProgressionResult {
        val bodyweight = exId != null && EXERCISES[exId]?.loadRatio == 0.0
        val unit = if (bodyweight) Unit.REPS else Unit.KG

        // Missing/invalid data handling
        if (currentReps <= 0 || currentLoad < 0.0) {
            return ProgressionResult(Progression.HOLD, currentLoad, 0.0, false, unit, "no valid data")
        }

        val increment = if (exId != null) Planner.increment(exId, profile) else 2.5

        // No-history baseline handling
        if (history.isEmpty()) {
            val clean = isExecutionClean(currentReps, currentFaultCount)
            val nextLoad = when {
                !clean -> currentLoad
                bodyweight -> currentReps + 1.0 // reps target goes up, never a fabricated load
                else -> loadable(exId, currentLoad + increment, profile)
            }
            return ProgressionResult(
                progression = if (clean) Progression.INCREASE else Progression.HOLD,
                nextLoad = nextLoad,
                estimated1RM = estimate1RM(currentReps, currentLoad),
                isClean = clean,
                unit = unit,
                reason = if (clean) "all reps clean" else "form broke down",
            )
        }

        val clean = isExecutionClean(currentReps, currentFaultCount)
        val estimated1RM = estimate1RM(currentReps, currentLoad)

        if (clean) {
            val nextLoad = if (bodyweight) currentReps + 1.0 else loadable(exId, currentLoad + increment, profile)
            return ProgressionResult(Progression.INCREASE, nextLoad, estimated1RM, true, unit, "all reps clean")
        } else {
            val stallsAtCurrentLoad = countStalls(history, currentLoad) + 1 // +1 for the current session

            if (!bodyweight && stallsAtCurrentLoad >= STALL_LIMIT) {
                val nextLoad = loadable(exId, deloadTo(currentLoad), profile)
                return ProgressionResult(Progression.DELOAD, nextLoad, estimated1RM, false, unit, "stalled three sessions")
            } else {
                return ProgressionResult(Progression.HOLD, currentLoad, estimated1RM, false, unit, "form broke down")
            }
        }
    }

    private fun countStalls(history: List<LogEntry>, currentLoad: Double): Int {
        var stalls = 0
        for (i in history.indices.reversed()) {
            val entry = history[i]
            if (Math.abs(entry.load - currentLoad) < 0.1) {
                // Naive count of fault events to avoid org.json mocking issues in JVM unit tests
                val faults = entry.faultEvents.count { it == '{' }
                
                if (!isExecutionClean(entry.reps, faults)) {
                    stalls++
                } else {
                    break // A clean session breaks the stall streak
                }
            } else {
                break // Different load breaks the streak
            }
        }
        return stalls
    }
}
