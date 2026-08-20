package com.example.domain

/**
 * A minimal daily mood self-report and persistent-pattern detection over it — never a diagnosis,
 * never a cause, never shame. Detecting a persistent low pattern only ever produces supportive
 * language and a suggestion to simplify today's plan or consider professional support.
 *
 * Scoped to [DayRow.mood] only, the one self-report dimension this schema already has and a real
 * UI can already write to. Energy/motivation/stress/confidence would need a real schema migration
 * (a genuine, deliberate step — not one bullet in a larger sweep) before this could honestly report
 * on them too.
 */
object WellbeingEngine {
    enum class Pattern { STABLE, IMPROVING, PERSISTENT_LOW, INSUFFICIENT_DATA }

    /** Below this many real check-ins, there is nothing to call a pattern. */
    const val MIN_CHECKINS = 5

    /** How many of the most recent check-ins are looked at for "persistent." */
    const val RECENT_WINDOW = 7

    /** A 1-10 self-report at or below this counts as "low" for pattern purposes. */
    const val LOW_THRESHOLD = 4

    /** "Persistent" means more than half the recent window was low, not just one bad day. */
    private const val LOW_FRACTION = 0.5

    data class Reading(val pattern: Pattern, val note: String?)

    /** [moods] are real logged 1-10 values, oldest first — a day with nothing logged is absent,
     *  never a zero and never a skipped-low. */
    fun evaluate(moods: List<Int>): Reading {
        if (moods.size < MIN_CHECKINS) return Reading(Pattern.INSUFFICIENT_DATA, null)
        val recent = moods.takeLast(RECENT_WINDOW)
        val lowCount = recent.count { it <= LOW_THRESHOLD }

        // Checked in this order deliberately: a real recent upswing should read as IMPROVING even
        // if the front-loaded low days still make up more than half the window -- flagging
        // "persistent low" while someone is actively recovering would be a stale, less accurate
        // read of what's actually happening right now.
        val pattern = when {
            recent.size >= 4 && improvingTrend(recent) -> Pattern.IMPROVING
            lowCount > recent.size * LOW_FRACTION -> Pattern.PERSISTENT_LOW
            else -> Pattern.STABLE
        }
        val note = if (pattern == Pattern.PERSISTENT_LOW) {
            "Your mood ratings have stayed low for a while. Today's plan is intentionally simpler. " +
                "If this continues or is affecting your life, it's worth talking to a qualified professional."
        } else null
        return Reading(pattern, note)
    }

    /** The back half of the window averaging meaningfully higher than the front half — a simple,
     *  honest recency read, not a fitted trend line over a handful of points. */
    private fun improvingTrend(recent: List<Int>): Boolean {
        val mid = recent.size / 2
        val front = recent.take(mid).average()
        val back = recent.takeLast(recent.size - mid).average()
        return back - front >= 1.5
    }
}
