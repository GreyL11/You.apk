package com.example.domain

import com.example.data.ActionOutcome
import java.time.LocalDateTime

/**
 * When you actually do things, learned from when you actually did them.
 *
 * Ported from the legacy `health.js`'s `preferredHour` / `reminderDelayMs` / `reminderExplanation`,
 * replacing an earlier version of this file that only knew about two hardcoded domains (hydration
 * and skin) and was never called by anything.
 *
 * THE ONE RULE, and it is the legacy's own: this is DELIVERY TIMING ONLY. It never becomes a health
 * fact, never changes what the coach recommends, and never reorders priority. "You usually drink
 * water in the morning" is a fact about your phone habits, not about your body — the moment timing
 * data starts influencing WHAT gets recommended, the app is drawing health conclusions from when
 * someone happens to tap a button.
 *
 * It also never silences anything permanently. Waiting for your usual hour is only allowed while
 * that hour is still ahead of you today; once it has passed, the reminder goes out regardless. An
 * adaptation that can defer forever is indistinguishable from a bug.
 */
object AdaptationEngine {
    /** Below this many recorded completions, timing stays generic. Three is the legacy's floor. */
    const val MIN_COMPLETIONS = 3

    enum class Bucket { MORNING, AFTERNOON, EVENING }

    /** A representative clock hour for each bucket, and the hour it stops being "still ahead". */
    private val BUCKET_HOUR = mapOf(Bucket.MORNING to 8, Bucket.AFTERNOON to 13, Bucket.EVENING to 18)
    private val BUCKET_END = mapOf(Bucket.MORNING to 12, Bucket.AFTERNOON to 18, Bucket.EVENING to 24)

    private fun bucketOf(hour: Int): Bucket = when {
        hour < 12 -> Bucket.MORNING
        hour < 18 -> Bucket.AFTERNOON
        else -> Bucket.EVENING
    }

    data class Preference(val bucket: Bucket, val completions: Int)

    /**
     * The part of day this specific action actually gets completed in.
     *
     * Per action, not per domain: "log a meal" and "drink water" are both eating-adjacent and happen
     * at completely different times. Null until there is enough history to have an opinion, which is
     * most of the time and is the correct answer then.
     */
    fun preferredBucket(actionId: String, outcomes: List<ActionOutcome>): Preference? {
        val hours = outcomes
            .filter { it.actionId == actionId && it.event == HealthCoachEngine.ActionState.COMPLETED.name }
            .mapNotNull { parseHour(it.at) }
        if (hours.size < MIN_COMPLETIONS) return null
        val best = hours.groupingBy { bucketOf(it) }.eachCount().maxByOrNull { it.value } ?: return null
        return Preference(best.key, hours.size)
    }

    /**
     * Is now a bad moment to interrupt about this, given a better one is still coming today?
     *
     * True only when all of these hold: there is real history, now is outside the bucket they
     * actually act in, and that bucket has not already passed today. The last condition is what
     * stops this from ever eating a whole day's reminder.
     */
    fun shouldWaitForBetterHour(
        actionId: String,
        outcomes: List<ActionOutcome>,
        now: LocalDateTime,
    ): Boolean {
        val pref = preferredBucket(actionId, outcomes) ?: return false
        val nowBucket = bucketOf(now.hour)
        if (nowBucket == pref.bucket) return false
        // Their hour has already gone by today — send it rather than skip the day entirely.
        val end = BUCKET_END[pref.bucket] ?: return false
        return now.hour < end
    }

    /**
     * How long to wait before re-raising a POSTPONED action, in minutes.
     *
     * Nudges toward the start of the bucket they actually finish this in, clamped to [30 min, 6 h]
     * so a thin estimate can neither bury a reminder for the rest of the day nor fire one straight
     * back at them.
     */
    fun reminderDelayMinutes(
        actionId: String,
        outcomes: List<ActionOutcome>,
        now: LocalDateTime,
        defaultMinutes: Long = 120,
    ): Long {
        val pref = preferredBucket(actionId, outcomes) ?: return defaultMinutes
        val targetHour = BUCKET_HOUR[pref.bucket] ?: return defaultMinutes
        var target = now.withHour(targetHour).withMinute(0).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        val minutes = java.time.temporal.ChronoUnit.MINUTES.between(now, target)
        return minutes.coerceIn(30L, 360L)
    }

    /** The answer to "why did you remind me now?" — always statable, never causal. */
    fun reminderExplanation(actionId: String, outcomes: List<ActionOutcome>): String {
        val pref = preferredBucket(actionId, outcomes)
            ?: return "Reminding you after the usual wait — not enough history yet to personalize timing."
        val word = pref.bucket.name.lowercase()
        return "You usually complete this in the $word, based on ${pref.completions} recorded " +
            "completions, so the reminder was scheduled closer to that time."
    }

    // ── which reminders you simply ignore ────────────────────────────────────────────────────

    /** Below this many offers, a low completion rate is small-sample noise, not a preference. */
    const val MIN_OFFERS = 5

    data class DomainHabit(val domain: String, val offered: Int, val completed: Int) {
        val rate: Double get() = if (offered == 0) 0.0 else completed.toDouble() / offered
    }

    /**
     * How often each domain's reminders actually get acted on.
     *
     * Read-only on purpose. It would be easy to have the coach quietly stop offering a domain
     * someone always skips, and that would be wrong twice over: the thing they skip is often the
     * thing they most need, and silently dropping a domain would make the app's own coverage a
     * secret. Surfacing it — "you have skipped this 9 of 10 times, want to turn it off?" — keeps
     * that a decision the person makes.
     */
    fun domainHabits(outcomes: List<ActionOutcome>): List<DomainHabit> =
        outcomes.groupBy { it.domain }
            .map { (domain, events) ->
                DomainHabit(
                    domain = domain,
                    offered = events.count { it.event == HealthCoachEngine.ActionState.OFFERED.name },
                    completed = events.count { it.event == HealthCoachEngine.ActionState.COMPLETED.name },
                )
            }
            .filter { it.offered >= MIN_OFFERS }

    private fun parseHour(at: String): Int? = try {
        LocalDateTime.parse(at).hour
    } catch (e: Exception) {
        null
    }
}
