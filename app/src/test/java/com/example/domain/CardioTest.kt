package com.example.domain

import com.example.data.DayRow
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CardioTest {
    private val today = LocalDate.parse("2026-08-20")

    private fun row(day: String, vararg sessions: Cardio.Session) = DayRow(
        dayKey = day, mood = null, bed = null, wake = null, sleeps = null,
        plans = if (sessions.isEmpty()) null else Cardio.toJson(sessions.toList()), skin = null,
    )

    @Test
    fun `a session round-trips through json intact`() {
        val s = Cardio.Session(Cardio.Mode.AEROBIC_BASE, 25, effortRating = 2)
        val back = Cardio.fromJson(Cardio.toJson(listOf(s)))
        assertEquals(listOf(s), back)
    }

    @Test
    fun `malformed or legacy plans data yields no sessions rather than crashing`() {
        assertEquals(emptyList<Cardio.Session>(), Cardio.fromJson("not json at all"))
        assertEquals(emptyList<Cardio.Session>(), Cardio.fromJson(null))
        assertEquals(emptyList<Cardio.Session>(), Cardio.fromJson(""))
        // The legacy shape this column used to hold -- objects with no recognizable mode.
        assertEquals(emptyList<Cardio.Session>(), Cardio.fromJson("""[{"title":"old plan","done":true}]"""))
    }

    @Test
    fun `a zero-minute or unknown-mode entry is dropped, never counted as a real session`() {
        assertEquals(emptyList<Cardio.Session>(), Cardio.fromJson("""[{"mode":"AEROBIC_BASE","minutes":0}]"""))
        assertEquals(emptyList<Cardio.Session>(), Cardio.fromJson("""[{"mode":"MOON_RUN","minutes":30}]"""))
    }

    @Test
    fun `days since last reads the most recent real session`() {
        val rows = listOf(
            row("2026-08-10", Cardio.Session(Cardio.Mode.AEROBIC_BASE, 25)),
            row("2026-08-18", Cardio.Session(Cardio.Mode.EASY_WALK, 20)),
        )
        assertEquals(2, Cardio.daysSinceLast(rows, today))
    }

    @Test
    fun `no cardio ever logged is null, not zero days ago`() {
        assertNull(Cardio.daysSinceLast(listOf(row("2026-08-18")), today))
    }

    @Test
    fun `weekly minutes counts only the trailing seven days`() {
        val rows = listOf(
            row("2026-08-20", Cardio.Session(Cardio.Mode.AEROBIC_BASE, 25)),
            row("2026-08-16", Cardio.Session(Cardio.Mode.EASY_WALK, 20)),
            row("2026-08-01", Cardio.Session(Cardio.Mode.INTERVALS, 60)), // outside the window
        )
        assertEquals(45, Cardio.weeklyMinutes(rows, today))
        assertEquals(2, Cardio.weeklySessions(rows, today))
    }

    @Test
    fun `too few sessions refuses to call a cardio base at all`() {
        val rows = (17..19).map { row("2026-08-$it", Cardio.Session(Cardio.Mode.AEROBIC_BASE, 30)) }
        assertEquals(Cardio.Base.INSUFFICIENT_DATA, Cardio.base(rows, today))
    }

    @Test
    fun `a month of short recovery walks is never mistaken for a real aerobic base`() {
        // Eight logged sessions, but all LIGHT_RECOVERY -- real consistency, not aerobic volume.
        val rows = (5..12).map { row("2026-08-%02d".format(it), Cardio.Session(Cardio.Mode.LIGHT_RECOVERY, 15)) }
        assertEquals(Cardio.Base.BEGINNER, Cardio.base(rows, today))
    }

    @Test
    fun `real sustained aerobic volume reads as an established base`() {
        // 16 sessions x 40 min over the month = 640 aerobic min -> 160/week -> ESTABLISHED.
        val rows = (1..16).map { row("2026-08-%02d".format(it), Cardio.Session(Cardio.Mode.AEROBIC_BASE, 40)) }
        assertEquals(Cardio.Base.ESTABLISHED, Cardio.base(rows, today))
    }

    @Test
    fun `moderate aerobic volume reads as developing, between the two extremes`() {
        // 6 sessions x 40 min = 240 min -> 60/week -> DEVELOPING (>=50, <120).
        val rows = (10..15).map { row("2026-08-$it", Cardio.Session(Cardio.Mode.AEROBIC_BASE, 40)) }
        assertEquals(Cardio.Base.DEVELOPING, Cardio.base(rows, today))
    }

    @Test
    fun `recovery cost is ordered so candidates are genuinely comparable`() {
        assertTrue(Cardio.cost(Cardio.Mode.NONE) < Cardio.cost(Cardio.Mode.EASY_WALK))
        assertTrue(Cardio.cost(Cardio.Mode.EASY_WALK) < Cardio.cost(Cardio.Mode.AEROBIC_BASE))
        assertTrue(Cardio.cost(Cardio.Mode.AEROBIC_BASE) < Cardio.cost(Cardio.Mode.MODERATE_CONDITIONING))
        assertTrue(Cardio.cost(Cardio.Mode.MODERATE_CONDITIONING) < Cardio.cost(Cardio.Mode.INTERVALS))
    }

    @Test
    fun `every real mode has a duration and an effort description a person can act on`() {
        Cardio.Mode.entries.filter { it != Cardio.Mode.NONE }.forEach { mode ->
            assertNotNull("$mode needs a duration", Cardio.minutes(mode))
            assertTrue("$mode needs an effort description", Cardio.effort(mode).isNotBlank())
            // No invented heart-rate targets -- the app has no heart-rate data.
            assertFalse("$mode must not claim a bpm target", Cardio.effort(mode).contains("bpm", ignoreCase = true))
        }
    }
}
