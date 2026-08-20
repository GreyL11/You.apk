package com.example.domain

import com.example.data.DayRow
import com.example.data.Weight
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class HealthImportTest {
    private val zone: ZoneId = ZoneId.systemDefault()

    /** Built through the system zone so the test says the same thing in every timezone. */
    private fun at(iso: String): Instant = LocalDateTime.parse(iso).atZone(zone).toInstant()

    private fun row(dayKey: String, sleeps: String? = null, steps: Int? = null) =
        DayRow(dayKey, null, null, null, sleeps, null, null, steps)

    @Test
    fun `a sleep session is filed under the day it ended`() {
        // Asleep Monday night, awake Tuesday morning: this is Tuesday's sleep.
        val imports = HealthImport.toSleepImport(
            listOf(at("2026-08-17T23:10:00") to at("2026-08-18T06:40:00")),
        )
        assertEquals(1, imports.size)
        assertEquals("2026-08-18", imports[0].dayKey)
        assertEquals("2026-08-17T23:10", imports[0].startIso)
        assertEquals("2026-08-18T06:40", imports[0].endIso)
    }

    @Test
    fun `a twenty minute doze is not a sleep`() {
        // Watches log a lot of this while you sit still, and letting it through would flood the nap
        // count and drag the main-sleep average somewhere meaningless.
        val imports = HealthImport.toSleepImport(
            listOf(at("2026-08-18T14:00:00") to at("2026-08-18T14:20:00")),
        )
        assertTrue(imports.isEmpty())
    }

    @Test
    fun `a night you logged yourself is never overwritten`() {
        val existing = mapOf(
            "2026-08-18" to row("2026-08-18", sleeps = """[{"start":"2026-08-17T22:00","end":"2026-08-18T05:00"}]"""),
        )
        val (rows, summary) = HealthImport.mergeSleep(
            listOf(SleepImport("2026-08-18", "2026-08-17T23:30", "2026-08-18T07:00")),
            existing,
        )
        assertTrue(rows.isEmpty())
        assertEquals(0, summary.sleepNightsAdded)
        assertEquals(1, summary.sleepNightsKept)
        assertTrue(summary.nothingNew)
    }

    @Test
    fun `an empty night is filled, and the legacy wake time is written from the longest block`() {
        val (rows, summary) = HealthImport.mergeSleep(
            listOf(
                SleepImport("2026-08-18", "2026-08-17T23:00", "2026-08-18T06:30"), // 7.5h, the main one
                SleepImport("2026-08-18", "2026-08-18T14:00", "2026-08-18T15:00"), // 1h nap
            ),
            emptyMap(),
        )
        assertEquals(1, rows.size)
        assertEquals(1, summary.sleepNightsAdded)
        assertEquals("06:30", rows[0].wake)
        assertEquals("23:00", rows[0].bed)
        // Both blocks are stored — the nap is kept, just not counted as the night.
        assertEquals(2, MoodInsights.sleepBlocks(rows[0]).size)
        assertEquals(7.5, MoodInsights.sleepSummary(rows[0]).main!!, 0.01)
    }

    @Test
    fun `steps are summed across the day's many records`() {
        val imports = HealthImport.toStepImports(
            listOf(
                at("2026-08-18T08:00:00") to 1200L,
                at("2026-08-18T12:00:00") to 3400L,
                at("2026-08-17T09:00:00") to 900L,
            ),
        )
        assertEquals(4600, imports.first { it.dayKey == "2026-08-18" }.steps)
        assertEquals(900, imports.first { it.dayKey == "2026-08-17" }.steps)
    }

    @Test
    fun `steps overwrite, but an unchanged count is not rewritten`() {
        val existing = mapOf("2026-08-18" to row("2026-08-18", steps = 4600))
        val (same, addedSame) = HealthImport.mergeSteps(listOf(StepImport("2026-08-18", 4600)), existing)
        assertTrue(same.isEmpty())
        assertEquals(0, addedSame)

        val (moved, addedMoved) = HealthImport.mergeSteps(listOf(StepImport("2026-08-18", 5200)), existing)
        assertEquals(5200, moved.single().steps)
        assertEquals(1, addedMoved)
    }

    @Test
    fun `a day with three weigh-ins keeps the last, not an average nobody saw`() {
        val imports = HealthImport.toWeightImports(
            listOf(
                at("2026-08-18T07:00:00") to 81.2,
                at("2026-08-18T13:00:00") to 81.9,
                at("2026-08-18T22:00:00") to 82.4,
            ),
        )
        assertEquals(1, imports.size)
        assertEquals(82.4, imports[0].kg, 0.001)
    }

    @Test
    fun `an unchanged weight is not rewritten`() {
        val existing = listOf(Weight("2026-08-18", 82.4))
        val (rows, added) = HealthImport.mergeWeights(listOf(WeightImport("2026-08-18", 82.4)), existing)
        assertTrue(rows.isEmpty())
        assertEquals(0, added)
    }

    @Test
    fun `the window asked for is the window the coach reads`() {
        val start = HealthImport.windowStart(java.time.LocalDate.parse("2026-08-18"))
        assertEquals("2026-07-22", HealthImport.dayKeyOf(start))
    }
}
