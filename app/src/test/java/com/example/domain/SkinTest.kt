package com.example.domain

import com.example.data.DayRow
import com.example.data.LogEntry
import com.example.data.Meal
import org.junit.Assert.*
import org.junit.Test

class SkinTest {
    @Test
    fun `no custom products exist`() {
        assertEquals(4, Skin.HABITS.size)
        assertEquals("spf", Skin.HABITS[0].id)
        assertEquals("washPost", Skin.HABITS[1].id)
        assertEquals("moisturise", Skin.HABITS[2].id)
        assertEquals("nopick", Skin.HABITS[3].id)
    }

    @Test
    fun `every habit has a real rationale, not a placeholder`() {
        for (h in Skin.HABITS) {
            assertNotEquals("${h.id} still has a placeholder why", "...", h.why)
            assertTrue("${h.id}'s why is suspiciously short", h.why.length > 10)
        }
    }

    @Test
    fun `toJson then fromJson round-trips score, flags and habits`() {
        val json = Skin.toJson(4, listOf("oily"), listOf("spf", "moisturise"))
        val entry = Skin.fromJson(json)
        assertEquals(4, entry.score)
        assertEquals(listOf("oily"), entry.flags)
        assertEquals(listOf("spf", "moisturise"), entry.habits)
    }

    @Test
    fun `fromJson never crashes on missing, null, or malformed data`() {
        assertEquals(Skin.SkinEntry(null, emptyList(), emptyList()), Skin.fromJson(null))
        assertEquals(Skin.SkinEntry(null, emptyList(), emptyList()), Skin.fromJson(""))
        assertEquals(Skin.SkinEntry(null, emptyList(), emptyList()), Skin.fromJson("not json"))
        // The old 3-checkbox shape this replaced: no score/flags/habits keys at all.
        assertEquals(Skin.SkinEntry(null, emptyList(), emptyList()), Skin.fromJson("""{"cleanser":true,"spf":true}"""))
    }

    @Test
    fun `scored only counts days that actually have a score, oldest first`() {
        val rows = listOf(
            DayRow(dayKey = "2026-08-18", mood = null, bed = null, wake = null, sleeps = null, plans = null, skin = Skin.toJson(3, emptyList(), emptyList())),
            DayRow(dayKey = "2026-08-16", mood = null, bed = null, wake = null, sleeps = null, plans = null, skin = Skin.toJson(5, emptyList(), listOf("spf"))),
            DayRow(dayKey = "2026-08-17", mood = null, bed = null, wake = null, sleeps = null, plans = null, skin = null),
        )
        val scored = Skin.scored(rows)
        assertEquals(listOf("2026-08-16", "2026-08-18"), scored.map { it.key })
    }

    @Test
    fun `routineAdherence counts only days every habit was logged`() {
        val allDone = Skin.HABITS.map { it.id }
        val rows = listOf(
            DayRow(dayKey = "2026-08-18", mood = null, bed = null, wake = null, sleeps = null, plans = null, skin = Skin.toJson(3, emptyList(), allDone)),
            DayRow(dayKey = "2026-08-19", mood = null, bed = null, wake = null, sleeps = null, plans = null, skin = Skin.toJson(3, emptyList(), listOf("spf"))),
        )
        val adherence = Skin.routineAdherence(rows)
        assertEquals(1, adherence.complete)
        assertEquals(2, adherence.of)
    }

    @Test
    fun `advice asks for more logging rather than guessing when there is not enough data`() {
        val fewDays = (1..3).map { DayRow(dayKey = "2026-08-1$it", mood = null, bed = null, wake = null, sleeps = null, plans = null, skin = Skin.toJson(3, emptyList(), emptyList())) }
        val advice = Skin.advice(fewDays, emptyList(), emptyList())
        assertEquals("none yet", advice.evidence)
        assertTrue(advice.text.contains("more days"))
    }

    @Test
    fun `advice flags an unticked habit before ever reaching for a diet correlation`() {
        // 10 days scored, SPF never logged, everything else logged every day — SPF should win
        // over any dietary association even if one existed, per Skin.advice's own ordering.
        val rows = (1..10).map {
            DayRow(
                dayKey = "2026-08-%02d".format(it), mood = null, bed = null, wake = null, sleeps = null, plans = null,
                skin = Skin.toJson(3, emptyList(), listOf("washPost", "moisturise", "nopick")),
            )
        }
        val advice = Skin.advice(rows, emptyList(), emptyList())
        assertEquals("spf", advice.habitId)
        assertTrue(advice.text.startsWith("Sunscreen:"))
    }

    @Test
    fun `association needs at least MIN_DAYS_PER_SIDE on both sides or it says nothing`() {
        // Only 3 scored days total — nowhere near enough for even one side of a comparison.
        val rows = (1..3).map { DayRow(dayKey = "2026-08-0$it", mood = null, bed = null, wake = null, sleeps = null, plans = null, skin = Skin.toJson(3, emptyList(), emptyList())) }
        assertNull(Skin.association("sugar", rows, emptyList(), emptyList()))
    }

    @Test
    fun `sugar association reads worse skin after more high-GI exposure, from real logged meals`() {
        // 14 scored days. Rice (HIGH_GI) starts on day 8, so day D's 3-day LAG_DAYS lookback stays
        // at zero exposure through day 8 (its window is days 5-7, all rice-free) and only turns
        // positive from day 9 on — giving 8 genuinely zero-exposure days and 6 exposed ones,
        // comfortably clearing MIN_DAYS_PER_SIDE(4) on both sides of the median split.
        val days = (1..14).map { "2026-08-%02d".format(it) }
        val rows = days.mapIndexed { i, key ->
            val score = if (i < 8) 5 else 2 // good skin while unexposed, worse once exposed
            DayRow(dayKey = key, mood = null, bed = null, wake = null, sleeps = null, plans = null, skin = Skin.toJson(score, emptyList(), emptyList()))
        }
        val meals = (8..14).map { d -> Meal(at = "2026-08-%02dT08:00:00".format(d), foodId = "rice", qty = 1.0) }
        val assoc = Skin.association("sugar", rows, meals, emptyList())
        assertNotNull(assoc)
        assertEquals(8, assoc!!.lowDays)
        assertEquals(6, assoc.highDays)
        assertTrue("expected skin to score better on low-exposure days, got diff=${assoc.diff}", assoc.diff > 0)
    }
}
