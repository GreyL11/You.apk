package com.example.domain

import com.example.data.ActionOutcome
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

/**
 * Learning WHEN someone acts — and the two things that must stay true no matter what it learns:
 * it can never defer a reminder past the day, and it never touches what gets recommended.
 */
class AdaptationEngineTest {
    private fun outcome(actionId: String, domain: String, event: String, at: String) =
        ActionOutcome(at = at, actionId = actionId, domain = domain, event = event)

    private fun completions(actionId: String, hour: Int, n: Int, domain: String = "hydration") =
        (1..n).map {
            outcome(
                actionId, domain, HealthCoachEngine.ActionState.COMPLETED.name,
                "2026-08-%02dT%02d:30:00".format(it, hour),
            )
        }

    private fun at(hour: Int) = LocalDateTime.parse("2026-08-20T%02d:00:00".format(hour))

    // ── it stays quiet until it knows something ──────────────────────────────────────────────

    @Test
    fun `two completions is not a habit`() {
        val history = completions("hydrate_now", hour = 9, n = 2)
        assertNull(AdaptationEngine.preferredBucket("hydrate_now", history))
        assertFalse(
            "with no preference known, it must never ask the notifier to wait",
            AdaptationEngine.shouldWaitForBetterHour("hydrate_now", history, at(7)),
        )
    }

    @Test
    fun `no history at all falls back to the plain wait, not to zero`() {
        assertEquals(120L, AdaptationEngine.reminderDelayMinutes("hydrate_now", emptyList(), at(9)))
        assertTrue(
            AdaptationEngine.reminderExplanation("hydrate_now", emptyList())
                .contains("not enough history"),
        )
    }

    // ── what it learns ───────────────────────────────────────────────────────────────────────

    @Test
    fun `it finds the part of day you actually complete this in`() {
        val pref = AdaptationEngine.preferredBucket("hydrate_now", completions("hydrate_now", hour = 9, n = 5))!!
        assertEquals(AdaptationEngine.Bucket.MORNING, pref.bucket)
        assertEquals(5, pref.completions)

        val evening = AdaptationEngine.preferredBucket("skin_log", completions("skin_log", hour = 21, n = 4))!!
        assertEquals(AdaptationEngine.Bucket.EVENING, evening.bucket)
    }

    @Test
    fun `timing is learned per action, not per domain`() {
        // Two actions in the same domain, done at opposite ends of the day. Pooling them by domain
        // would average into an afternoon nobody actually uses.
        val history = completions("hydrate_now", hour = 8, n = 4, domain = "hydration") +
            completions("meal_log", hour = 20, n = 4, domain = "nutrition")
        assertEquals(
            AdaptationEngine.Bucket.MORNING,
            AdaptationEngine.preferredBucket("hydrate_now", history)!!.bucket,
        )
        assertEquals(
            AdaptationEngine.Bucket.EVENING,
            AdaptationEngine.preferredBucket("meal_log", history)!!.bucket,
        )
    }

    @Test
    fun `only completions count, not offers or skips`() {
        val noise = (1..9).map {
            outcome("hydrate_now", "hydration", HealthCoachEngine.ActionState.OFFERED.name, "2026-08-0${it}T07:00:00")
        } + (1..9).map {
            outcome("hydrate_now", "hydration", HealthCoachEngine.ActionState.SKIPPED.name, "2026-08-0${it}T07:30:00")
        }
        assertNull(
            "being pestered at 07:00 and ignoring it is not evidence you like 07:00",
            AdaptationEngine.preferredBucket("hydrate_now", noise),
        )
    }

    // ── the guarantee: it can delay, never cancel ────────────────────────────────────────────

    @Test
    fun `it asks the notifier to wait when their usual window is still ahead`() {
        val morningPerson = completions("hydrate_now", hour = 9, n = 5)
        assertTrue(
            "07:00 for a 09:00 person, with the morning still to come",
            AdaptationEngine.shouldWaitForBetterHour("hydrate_now", morningPerson, at(7)),
        )
    }

    @Test
    fun `it never defers past their window, so a day is never silently skipped`() {
        val morningPerson = completions("hydrate_now", hour = 9, n = 5)
        // Morning has gone. Waiting now would mean waiting until tomorrow, which is not waiting.
        assertFalse(AdaptationEngine.shouldWaitForBetterHour("hydrate_now", morningPerson, at(14)))
        assertFalse(AdaptationEngine.shouldWaitForBetterHour("hydrate_now", morningPerson, at(20)))
    }

    @Test
    fun `inside their own window it never asks to wait`() {
        val eveningPerson = completions("skin_log", hour = 21, n = 5)
        assertFalse(AdaptationEngine.shouldWaitForBetterHour("skin_log", eveningPerson, at(21)))
    }

    // ── postpone delay ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a postpone is nudged toward their hour, and always clamped`() {
        val eveningPerson = completions("skin_log", hour = 20, n = 5)
        // From 09:00 toward an 18:00 bucket start is 9 hours — clamped down to the 6 hour ceiling.
        assertEquals(360L, AdaptationEngine.reminderDelayMinutes("skin_log", eveningPerson, at(9)))

        val morningPerson = completions("hydrate_now", hour = 8, n = 5)
        // From 07:50 toward 08:00 is 10 minutes — clamped up to the 30 minute floor, so a postpone
        // never fires straight back at them.
        val justBefore = LocalDateTime.parse("2026-08-20T07:50:00")
        assertEquals(30L, AdaptationEngine.reminderDelayMinutes("hydrate_now", morningPerson, justBefore))
    }

    // ── it can say why ───────────────────────────────────────────────────────────────────────

    @Test
    fun `the explanation states the habit and its sample size, and claims no cause`() {
        val text = AdaptationEngine.reminderExplanation("hydrate_now", completions("hydrate_now", 9, 5))
        assertTrue(text, text.contains("morning"))
        assertTrue(text, text.contains("5 recorded completions"))
        // A timing habit is not a health finding. It must not imply one.
        assertFalse(text.lowercase().contains("because you"))
        assertFalse(text.lowercase().contains("better for you"))
    }

    // ── skipped domains are surfaced, never silently dropped ─────────────────────────────────

    @Test
    fun `domain habits need a real sample before reporting a rate`() {
        val thin = (1..3).map {
            outcome("skin_log", "skinRoutine", HealthCoachEngine.ActionState.OFFERED.name, "2026-08-0${it}T09:00:00")
        }
        assertTrue("3 offers is not a preference", AdaptationEngine.domainHabits(thin).isEmpty())
    }

    @Test
    fun `a domain you always ignore is reported, not auto-disabled`() {
        val ignored = (1..9).map {
            outcome("skin_log", "skinRoutine", HealthCoachEngine.ActionState.OFFERED.name, "2026-08-0${it}T09:00:00")
        }
        val habit = AdaptationEngine.domainHabits(ignored).single()
        assertEquals("skinRoutine", habit.domain)
        assertEquals(9, habit.offered)
        assertEquals(0, habit.completed)
        assertEquals(0.0, habit.rate, 0.001)
        // The engine reports it. Nothing here turns the domain off: the thing someone skips is often
        // the thing they most need, and a silently dropped domain makes the app's coverage a secret.
    }
}
