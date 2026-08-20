package com.example.domain

import com.example.data.DayRow
import com.example.data.LogEntry
import com.example.data.Round
import com.example.data.Weight
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class TInputsTest {
    private val today: LocalDate = LocalDate.parse("2026-08-18")
    private val clock = Regex("\\d\\d:\\d\\d")

    /** n nights of [hours] main sleep, each ending at [wake] — real blocks, so a wake time exists. */
    private fun nights(n: Int, hours: Double, wake: String = "06:00"): List<DayRow> =
        (0 until n).map { i ->
            val day = today.minusDays(i.toLong())
            val end = LocalDateTime.of(day, LocalTime.parse(wake))
            val start = end.minusMinutes((hours * 60).toLong())
            DayRow(day.toString(), null, null, null, """[{"start":"$start","end":"$end"}]""", null, null)
        }

    /** n nights logged as bare lengths — what the first sleep sheet wrote. No wake time in them. */
    private fun untimedNights(n: Int, hours: Double): List<DayRow> =
        (0 until n).map { i ->
            val day = today.minusDays(i.toLong())
            DayRow(day.toString(), null, null, null, """[{"duration":$hours}]""", null, null)
        }

    private fun read(
        rows: List<DayRow> = emptyList(),
        weights: List<Weight> = emptyList(),
        logs: List<LogEntry> = emptyList(),
        rounds: List<Round> = emptyList(),
    ) = TInputs.read(rows, weights, logs, rounds, today)

    private fun trainingDays(n: Int): List<LogEntry> =
        (0 until n).map { i ->
            LogEntry(
                id = i + 1,
                exId = "squat",
                at = "${today.minusDays(i.toLong())}T18:00:00",
                reps = 5,
                sets = 3,
                load = 60.0,
                faultEvents = "[]",
                correctedFrom = null,
            )
        }

    @Test
    fun `no testosterone score or TRT guidance anywhere`() {
        val r = read(nights(14, 5.5), listOf(Weight(today.toString(), 80.0)), trainingDays(10))
        val everything = r.toString()
        assertFalse(everything.contains("testosterone_score", ignoreCase = true))
        assertFalse(everything.contains("trt", ignoreCase = true))
        assertFalse(everything.contains("sarm", ignoreCase = true))
        // No number claiming to be a hormone level, and the boundary says so out loud.
        assertTrue(TInputs.HORMONAL_BOUNDARY.contains("blood test"))
    }

    @Test
    fun `sleep bands and the nights floor`() {
        assertEquals("unknown", read(nights(9, 8.0)).sleep.verdict)
        assertEquals("low", read(nights(10, 5.5)).sleep.verdict)
        assertEquals("under", read(nights(10, 6.5)).sleep.verdict)
        assertEquals("good", read(nights(10, 7.5)).sleep.verdict)
        assertEquals(10, read(nights(10, 7.5)).sleep.nights)
    }

    @Test
    fun `a nap is reported beside the night, never added into the verdict`() {
        val rows = (0 until 10).map { i ->
            val day = today.minusDays(i.toLong())
            val end = LocalDateTime.of(day, LocalTime.parse("06:00"))
            val napEnd = LocalDateTime.of(day, LocalTime.parse("15:00"))
            DayRow(
                day.toString(), null, null, null,
                """[{"start":"${end.minusHours(4)}","end":"$end"},""" +
                    """{"start":"${napEnd.minusHours(3)}","end":"$napEnd"}]""",
                null, null,
            )
        }
        val s = read(rows).sleep
        // Four hours plus a three-hour nap is not the seven hours the evidence is about.
        assertEquals("low", s.verdict)
        assertEquals(4.0, s.avg!!, 0.01)
        assertEquals(7.0, s.totalAvg!!, 0.01)
        assertEquals(10, s.napDays)
    }

    @Test
    fun `weight is a direction or nothing, never a judgement`() {
        assertEquals("unknown", read(weights = listOf(Weight(today.toString(), 80.0))).weight.verdict)
        val r = read(
            weights = listOf(
                Weight(today.minusDays(20).toString(), 80.0),
                Weight(today.toString(), 78.5),
            ),
        )
        assertEquals("known", r.weight.verdict)
        assertEquals(-1.5, r.weight.kg!!, 0.01)
        assertFalse(r.weight.verdict == "bad" || r.weight.verdict == "good")
    }

    @Test
    fun `wake spread is measured around the clock, not across it`() {
        // Waking at 23:00 and 01:00 is two hours apart, not twenty-two.
        val rows = nights(1, 8.0, "23:00") + nights(1, 8.0, "01:00").map { it.copy(dayKey = "2026-08-17") }
        val w = TInputs.wakePattern(rows)!!
        assertEquals(120, w.spreadMins)
        assertTrue(w.regular)
        assertEquals(2, w.nights)
    }

    @Test
    fun `a bedtime is computed from your own wake time, never a stock hour`() {
        val r = read(nights(10, 5.5, "06:00"))
        // 06:00 minus the 7h target. Nothing here is a constant somebody typed.
        assertEquals("Lights off by 23:00", r.advice.plan)
        assertTrue(r.advice.text!!.contains("waking at 06:00"))

        val later = read(nights(10, 5.5, "08:30"))
        assertEquals("Lights off by 01:30", later.advice.plan)
    }

    @Test
    fun `no wake time logged means no hour is invented`() {
        val r = read(untimedNights(10, 5.5))
        assertNull(TInputs.wakePattern(untimedNights(10, 5.5)))
        assertEquals("Lights off 45 minutes earlier", r.advice.plan)
        assertFalse(clock.containsMatchIn(r.advice.text!!))
        assertFalse(clock.containsMatchIn(r.advice.plan!!))
        assertTrue(r.advice.text!!.contains("Log a wake time"))
    }

    @Test
    fun `an irregular wake time withholds the bedtime and names a length instead`() {
        val rows = (0 until 10).map { i ->
            nights(1, 5.5, if (i % 2 == 0) "04:00" else "11:00")[0]
                .copy(dayKey = today.minusDays(i.toLong()).toString())
        }
        val r = read(rows)
        assertFalse(r.wake!!.regular)
        assertFalse(clock.containsMatchIn(r.advice.plan!!))
        assertEquals("7h in the main sleep", r.advice.plan)
        assertTrue(r.advice.text!!.contains("no usual hour"))
    }

    @Test
    fun `a fresh install is told what to log, not judged on zeroes`() {
        val r = read()
        assertNull(r.advice.plan)
        assertTrue(r.advice.text!!.startsWith("Nothing logged yet."))
        assertEquals(0, r.training.days)
        // Absent is not zero: no "0 hours of sleep" verdict anywhere in it.
        assertEquals("unknown", r.sleep.verdict)
        assertEquals("unknown", r.weight.verdict)
    }

    @Test
    fun `training on the floor outranks a short night but not a bad one`() {
        // 6.5h is short; with nothing trained, training is the bigger miss.
        assertEquals("Train", read(nights(10, 6.5)).advice.plan)
        // 5.5h is genuinely bad; sleep wins even with nothing trained.
        assertEquals("Lights off by 23:00", read(nights(10, 5.5)).advice.plan)
        // Trained enough and sleeping enough: nothing to say.
        val fine = read(rows = nights(10, 7.5), logs = trainingDays(10))
        assertNull(fine.advice.text)
    }
}
