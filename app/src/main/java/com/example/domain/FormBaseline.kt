package com.example.domain

import com.example.data.LogEntry

/**
 * What is normal FOR YOU, learned from your own logged sessions.
 *
 * This closes a gap the legacy `exercises.js` wrote down as intent and never actually implemented:
 *
 *   "The app learns what is normal FOR YOU and stops nagging about it. That is right for technique
 *    preferences and catastrophically wrong for injury risk."
 *
 * Every joint threshold in [EXERCISES] is a fixed number applied to every body. Your hip mobility,
 * femur length and shoulder structure are not in them, so a lifter whose anatomy simply moves that
 * way gets the same cue on every rep of every session forever. A cue you have heard two hundred
 * times is not coaching, it is noise, and noise is what makes people stop looking at the screen —
 * including on the rep where something is actually wrong.
 *
 * THE LINE THIS FILE WILL NOT CROSS, and it is the whole reason the severity split exists:
 *
 *   A SAFETY fault is NEVER baselined away, however habitual. "You have rounded your back on all
 *   two hundred sets" is not evidence that rounding your back is fine for you — it is a reason to
 *   say it louder. Only EFFICIENCY faults can become your normal.
 *
 * Nothing here changes what gets RECORDED. Every fault still lands in the log exactly as detected,
 * so the progression engine's fault rate and any future review are computed off the real thing.
 * This decides only whether to speak mid-set.
 */
object FormBaseline {
    /**
     * Sessions of this lift needed before any claim about your normal.
     *
     * Three is not a habit and one bad day would set it. Six sessions of the same pattern is a
     * body, not a mood — and it is roughly two weeks of a lift on a normal split, which is soon
     * enough to be useful and long enough to have survived a deload and a bad night's sleep.
     */
    const val MIN_SESSIONS = 6

    /**
     * The fault has to fire on most reps to count as how you move.
     *
     * At 0.6 it is not an occasional lapse under fatigue — it is the shape of the movement. Below
     * this it is a real, correctable slip and worth a cue.
     */
    const val HABITUAL_RATE = 0.6

    /**
     * And it has to be consistent, not an average of extremes.
     *
     * A lifter who fires on every rep of three sessions and none of the next three averages 0.5 —
     * that is not a stable pattern, that is something changing (a cue that worked, a new shoe, an
     * injury developing), and the app should keep talking about it. Requiring most SESSIONS to be
     * individually habitual, not just the pooled mean, is what tells those two cases apart.
     */
    const val MIN_HABITUAL_SESSIONS = 0.67

    data class Pattern(
        val exId: String,
        val faultId: String,
        val sessions: Int,
        /** Pooled: total fires over total reps across the window. */
        val rate: Double,
        /** How many of those sessions were individually at or above [HABITUAL_RATE]. */
        val habitualSessions: Int,
        val habitual: Boolean,
    )

    /**
     * How often one fault fires per rep, per session, for this lift.
     *
     * Sessions with no reps are skipped rather than counted as clean: dividing by zero reps would
     * make a logging artefact look like a perfect session.
     */
    private fun perSessionRates(history: List<LogEntry>, exId: String, faultId: String): List<Double> =
        history
            .filter { it.exId == exId && it.reps > 0 }
            .map { entry ->
                val fires = Coach.faultEventsOf(entry).count { it.faultId == faultId }
                fires.toDouble() / entry.reps
            }

    /** Everything known about how you perform one fault on one lift. */
    fun pattern(history: List<LogEntry>, exId: String, faultId: String): Pattern {
        val rates = perSessionRates(history, exId, faultId)
        val habitualCount = rates.count { it >= HABITUAL_RATE }
        val pooled = if (rates.isEmpty()) 0.0 else rates.average()
        val habitual = rates.size >= MIN_SESSIONS &&
            pooled >= HABITUAL_RATE &&
            habitualCount.toDouble() / rates.size >= MIN_HABITUAL_SESSIONS
        return Pattern(
            exId = exId,
            faultId = faultId,
            sessions = rates.size,
            rate = Math.round(pooled * 100) / 100.0,
            habitualSessions = habitualCount,
            habitual = habitual,
        )
    }

    /**
     * Should this fault be spoken aloud mid-set?
     *
     * Safety faults: always yes, no matter how habitual — see the note at the top of this file.
     * Efficiency faults: yes until it is demonstrably just how you move.
     */
    fun shouldCue(history: List<LogEntry>, exId: String, faultId: String): Boolean {
        if (isSafetyFault(exId, faultId)) return true
        return !pattern(history, exId, faultId).habitual
    }

    /** The efficiency faults this lifter has been shown to simply do. For the review screen. */
    fun habitualFaults(history: List<LogEntry>, exId: String): List<Pattern> {
        val ex = EXERCISES[exId] ?: return emptyList()
        return ex.faults
            .filterNot { isSafetyFault(exId, it.id) }
            .map { pattern(history, exId, it.id) }
            .filter { it.habitual }
    }

    /**
     * Said once, on the review screen — not mid-set.
     *
     * Being told "this is just how you squat" while under a loaded bar is the wrong moment for a
     * piece of information that is about weeks, not about this rep.
     */
    fun explain(pattern: Pattern): String {
        val name = EXERCISES[pattern.exId]?.name ?: pattern.exId
        val cue = EXERCISES[pattern.exId]?.faults?.find { it.id == pattern.faultId }?.cue
        val pct = (pattern.rate * 100).toInt()
        return "On $name this shows up on about $pct% of your reps across ${pattern.sessions} sessions, " +
            "so it is being treated as how you move rather than something to fix mid-set" +
            (if (cue != null) " — it will stop cueing \"$cue\"." else ".")
    }
}
