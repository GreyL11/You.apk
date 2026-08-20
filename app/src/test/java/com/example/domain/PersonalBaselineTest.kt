package com.example.domain

import com.example.data.DayRow
import com.example.data.LogEntry
import com.example.data.Meal
import com.example.data.Weight
import org.junit.Assert.*
import org.junit.Test

class PersonalBaselineTest {
    private fun sleepRow(day: String, hours: Double) =
        DayRow(dayKey = day, mood = null, bed = null, wake = null, sleeps = """[{"duration":$hours}]""", plans = null, skin = null)

    // ── refusal: not enough evidence yet ────────────────────────────────────────────────────

    @Test
    fun `fewer than MIN_SAMPLES sleep entries reports INSUFFICIENT, not a guessed typical`() {
        val rows = (1..3).map { sleepRow("2026-08-0$it", 7.0) }
        val m = PersonalBaseline.sleepHours(rows)
        assertNull(m.typical)
        assertEquals(PersonalBaseline.Confidence.INSUFFICIENT, m.confidence)
        assertEquals(3, m.sampleSize)
    }

    @Test
    fun `compare refuses to speak without enough baseline evidence`() {
        val thin = PersonalBaseline.Metric(typical = 7.0, sampleSize = 2, confidence = PersonalBaseline.Confidence.INSUFFICIENT)
        assertNull(PersonalBaseline.compare(5.0, thin))
    }

    @Test
    fun `compare refuses to speak when today's value itself is missing`() {
        val real = PersonalBaseline.Metric(typical = 7.0, sampleSize = 10, confidence = PersonalBaseline.Confidence.MODERATE)
        assertNull(PersonalBaseline.compare(null, real))
    }

    // ── real medians from real logged history ───────────────────────────────────────────────

    @Test
    fun `sleepHours is the median of real logged nights, absent days never counted as zero`() {
        val rows = listOf(
            sleepRow("2026-08-01", 6.0),
            sleepRow("2026-08-02", 7.0),
            sleepRow("2026-08-03", 8.0),
            sleepRow("2026-08-04", 9.0),
            DayRow(dayKey = "2026-08-05", mood = null, bed = null, wake = null, sleeps = null, plans = null, skin = null), // not logged
        )
        val m = PersonalBaseline.sleepHours(rows)
        assertEquals(4, m.sampleSize) // the unlogged day is excluded, not zero
        assertEquals(7.5, m.typical!!, 0.001) // median of 6,7,8,9
        assertEquals(PersonalBaseline.Confidence.LOW, m.confidence)
    }

    @Test
    fun `dailyFluidMl medians real per-day totals and ignores days with nothing logged`() {
        val meals = listOf(
            Meal(at = "2026-08-01T08:00:00", foodId = "water", qty = 2.0), // 500ml
            Meal(at = "2026-08-02T08:00:00", foodId = "water", qty = 4.0), // 1000ml
            Meal(at = "2026-08-03T08:00:00", foodId = "water", qty = 6.0), // 1500ml
            Meal(at = "2026-08-04T08:00:00", foodId = "water", qty = 8.0), // 2000ml
            Meal(at = "2026-08-05T08:00:00", foodId = "chickenBreast", qty = 1.0), // no ml -> 0, excluded
        )
        val m = PersonalBaseline.dailyFluidMl(meals)
        assertEquals(4, m.sampleSize)
        assertEquals(1250.0, m.typical!!, 0.001) // median of 500,1000,1500,2000
    }

    @Test
    fun `trainingSessionsPerWeek reads a real weekly rate from distinct logged days`() {
        val logs = listOf("2026-08-01", "2026-08-03", "2026-08-05", "2026-08-08", "2026-08-10", "2026-08-12")
            .map { LogEntry(exId = "squat", at = "${it}T10:00:00", reps = 5, sets = 1, load = 50.0, faultEvents = "[]", correctedFrom = null) }
        // 6 sessions over a 28-day window -> 1.5/week
        val m = PersonalBaseline.trainingSessionsPerWeek(logs, windowDays = 28)
        assertEquals(6, m.sampleSize)
        assertEquals(1.5, m.typical!!, 0.001)
        // 28 days is a full four weeks of observation, which lands in the MODERATE band
        // (14..27 is LOW). The confidence ladder reads the window length, not the session count.
        assertEquals(PersonalBaseline.Confidence.MODERATE, m.confidence)
    }

    @Test
    fun `trainingSessionsPerWeek needs at least two weeks of window before saying anything`() {
        val logs = listOf(LogEntry(exId = "squat", at = "2026-08-01T10:00:00", reps = 5, sets = 1, load = 50.0, faultEvents = "[]", correctedFrom = null))
        val m = PersonalBaseline.trainingSessionsPerWeek(logs, windowDays = 7)
        assertNull(m.typical)
        assertEquals(PersonalBaseline.Confidence.INSUFFICIENT, m.confidence)
    }

    @Test
    fun `bodyweightKg is a real range from real weigh-ins`() {
        val weights = listOf(80.0, 79.5, 79.0, 78.5, 78.0).mapIndexed { i, kg -> Weight(at = "2026-08-0${i + 1}", kg = kg) }
        val m = PersonalBaseline.bodyweightKg(weights)
        assertEquals(79.0, m.typical!!, 0.001)
        assertEquals(PersonalBaseline.Confidence.LOW, m.confidence)
    }

    // ── compare phrasing ─────────────────────────────────────────────────────────────────────

    @Test
    fun `compare reads meaningfully below, meaningfully above, or close to normal`() {
        val baseline = PersonalBaseline.Metric(typical = 2000.0, sampleSize = 10, confidence = PersonalBaseline.Confidence.MODERATE)
        assertEquals("50% below your normal", PersonalBaseline.compare(1000.0, baseline))
        assertEquals("50% above your normal", PersonalBaseline.compare(3000.0, baseline))
        assertEquals("close to your normal", PersonalBaseline.compare(1950.0, baseline))
    }
}
