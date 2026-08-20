package com.example.domain

import com.example.data.LogEntry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONException

/**
 * What to put on the bar next time, and why.
 *
 * Ported from the legacy `coach.js` `preview()` and `insights.js`. Two conditions decide, and they
 * are deliberately separate:
 *
 *   REPS HIT      every set reached its target. A session where you managed 3 of 15 is not a session
 *                 to add weight to, however clean those 3 were.
 *   FORM CLEAN    corrections stayed under [CLEAN_FAULTS_PER_REP] per rep.
 *
 * Only both together earn an increase. Failing them produces different reasons — "reps missed" and
 * "form broke down" are different facts about a session and a person acts on them differently — and
 * the [ProgressionResult.evidence] bundle carries the numbers each conclusion was drawn from, so a
 * decision is explainable after the fact rather than merely announced.
 */
object Coach {
    const val CLEAN_FAULTS_PER_REP = 0.34
    const val STALL_LIMIT = 3
    const val DELOAD_FACTOR = 0.9

    enum class Progression { INCREASE, HOLD, DELOAD }

    /**
     * What `nextLoad` means. A bodyweight lift (loadRatio 0 in the catalogue — chestDip/pushup/dip)
     * progresses its REP TARGET, never an invented load: there is nothing to add weight to on a
     * push-up.
     */
    enum class Unit { KG, REPS }

    data class ProgressionResult(
        val progression: Progression,
        val nextLoad: Double,
        val estimated1RM: Double,
        val isClean: Boolean,
        val unit: Unit = Unit.KG,
        /** "all reps clean", "reps missed", "form broke down", "stalled three sessions", "no valid data". */
        val reason: String = "",
        /**
         * The numbers this verdict was computed from — not new inputs, the same values the branches
         * already used, kept instead of discarded.
         *
         * This is what [Explain] validates a model's sentence against: "corrections ran at 0.4 per rep
         * against a 0.34 limit" is checkable, "your form broke down" is not.
         */
        val evidence: Map<String, Any?> = emptyMap(),
    )

    /** Epley. */
    fun estimate1RM(reps: Int, load: Double): Double {
        if (reps <= 0 || load < 0.0) return 0.0
        return load * (1.0 + reps / 30.0)
    }

    fun isExecutionClean(reps: Int, faultCount: Int): Boolean {
        if (reps <= 0) return false
        return (faultCount.toDouble() / reps) < CLEAN_FAULTS_PER_REP
    }

    /**
     * Nearest 2.5 kg, never below zero — the legacy `insights.js` `round2()`.
     *
     * Was nearest 0.5, which is a granularity no plate set produces: 49.5 kg cannot be loaded on a
     * 20 kg bar with 2.5s, so the app would print a number the person then has to reinterpret.
     * [loadable] snaps to the real plate grid on top of this wherever the exercise is known.
     */
    private fun round2(kg: Double): Double = max(0.0, (kg / 2.5).roundToInt() * 2.5)

    private fun deloadTo(load: Double) = round2(load * DELOAD_FACTOR)

    /** Snap to something the bar can actually be loaded to — port of coach.js's `loadable()`. */
    private fun loadable(exId: String?, kg: Double, profile: TrainingProfile): Double =
        if (exId != null && EXERCISES[exId]?.equipment == "barbell") Planner.achievableLoad(kg, profile) else kg

    /**
     * How many faults a logged session actually recorded.
     *
     * Parses the stored JSON rather than counting `{` characters, which was a workaround for org.json
     * throwing "Stub!" in unit tests. A brace count is wrong the moment a fault event carries a nested
     * object: every nested brace becomes another phantom fault, and phantom faults push a clean
     * session into a stall and a stall into a deload.
     */
    fun faultCountOf(entry: LogEntry): Int = try {
        JSONArray(entry.faultEvents).length()
    } catch (e: JSONException) {
        0
    }

    /**
     * The individual fault events a session recorded, as {rep, id} pairs.
     *
     * Lives here beside [faultCountOf] so the stored JSON has exactly one reader: two parsers of the
     * same column is how a count and a breakdown of that same count drift apart. An entry whose
     * events are unparseable, or whose objects are missing the `id` written by
     * `TodayViewModel.logTraining`, yields nothing rather than a partially-invented list.
     */
    fun faultEventsOf(entry: LogEntry): List<FaultEvent> = try {
        val arr = JSONArray(entry.faultEvents)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifEmpty { return@mapNotNull null }
            FaultEvent(rep = o.optInt("rep", 0), faultId = id)
        }
    } catch (e: JSONException) {
        emptyList()
    }

    /**
     * @param targetReps what the session was supposed to hit. Null means the caller has no plan to
     *   compare against, and is treated as "reps hit" — inventing a target would fabricate the "reps
     *   missed" verdict rather than withhold it.
     * @param exId the catalogue entry, needed to know whether this is a bodyweight lift and to snap a
     *   loaded lift's next weight to what the profile's bar and plates can actually make.
     */
    fun evaluateSession(
        history: List<LogEntry>,
        currentReps: Int,
        currentLoad: Double,
        currentFaultCount: Int,
        exId: String? = null,
        profile: TrainingProfile = TrainingProfile(),
        targetReps: Int? = null,
    ): ProgressionResult {
        val bodyweight = exId != null && EXERCISES[exId]?.loadRatio == 0.0
        val unit = if (bodyweight) Unit.REPS else Unit.KG

        if (currentReps <= 0 || currentLoad < 0.0) {
            return ProgressionResult(Progression.HOLD, currentLoad, 0.0, false, unit, "no valid data")
        }

        val clean = isExecutionClean(currentReps, currentFaultCount)
        val repsHit = targetReps == null || currentReps >= targetReps
        val estimated1RM = estimate1RM(currentReps, currentLoad)
        val increment = if (exId != null) Planner.increment(exId, profile) else 2.5
        val stalls = countStalls(history, currentLoad) + 1 // the session being judged is not in history yet
        val faultsPerRep = (currentFaultCount.toDouble() / currentReps * 100).roundToInt() / 100.0

        val evidence = mapOf(
            "totalReps" to currentReps,
            "targetReps" to targetReps,
            "repsHit" to repsHit,
            "totalFaults" to currentFaultCount,
            "faultsPerRep" to faultsPerRep,
            "cleanLimit" to CLEAN_FAULTS_PER_REP,
            // The same count the deload branch tests, so an explanation quoting it quotes the number
            // that actually decided.
            "stalledSessions" to stalls,
            "from" to if (bodyweight) currentReps.toDouble() else currentLoad,
        )

        // Both conditions, or no increase.
        if (repsHit && clean) {
            val nextLoad = if (bodyweight) currentReps + 1.0
            else loadable(exId, currentLoad + increment, profile)
            return ProgressionResult(
                Progression.INCREASE, nextLoad, estimated1RM, true, unit, "all reps clean",
                evidence + ("to" to nextLoad),
            )
        }

        if (!bodyweight && stalls >= STALL_LIMIT) {
            val nextLoad = loadable(exId, deloadTo(currentLoad), profile)
            return ProgressionResult(
                Progression.DELOAD, nextLoad, estimated1RM, clean, unit, "stalled three sessions",
                evidence + ("to" to nextLoad),
            )
        }

        val held = if (bodyweight) currentReps.toDouble() else currentLoad
        return ProgressionResult(
            Progression.HOLD, held, estimated1RM, clean, unit,
            if (!repsHit) "reps missed" else "form broke down",
            evidence + ("to" to held),
        )
    }

    /**
     * Consecutive sessions at this same load that did not go clean.
     *
     * A clean session breaks the streak, and so does a different load: a stall is "this weight has
     * beaten me repeatedly", not "I have had a few bad sessions lately".
     */
    private fun countStalls(history: List<LogEntry>, currentLoad: Double): Int {
        var stalls = 0
        for (i in history.indices.reversed()) {
            val entry = history[i]
            if (abs(entry.load - currentLoad) >= 0.1) break
            if (isExecutionClean(entry.reps, faultCountOf(entry))) break
            stalls++
        }
        return stalls
    }
}
