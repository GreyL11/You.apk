package com.example.domain

import com.example.data.DayRow
import com.example.data.Weight
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Instant

/**
 * Turning Health Connect records into the app's own rows. Pure — no client, no permissions, no
 * Android. The reading half lives in `HealthConnectReader`; the deciding half is here so it can be
 * tested without a phone, a watch, or a granted permission.
 *
 * THE RULE: imported data is the same shape as hand-logged data, and hand-logged data wins.
 *
 * A sleep block you typed in is a statement about your night. A sleep session from a watch is a
 * sensor's opinion of it. They are both real, but if both exist for one night the app must not double
 * count them or silently overwrite what you said — so [mergeSleep] keeps what is already on the day
 * row and only fills the nights that are empty.
 */
data class SleepImport(
    /** Day the sleep ENDED — the same filing rule hand-logged sleep uses. */
    val dayKey: String,
    val startIso: String,
    val endIso: String,
)

data class StepImport(val dayKey: String, val steps: Int)

data class WeightImport(val dayKey: String, val kg: Double)

/** What one import run changed, so the UI can say so instead of claiming a silent success. */
data class ImportSummary(
    val sleepNightsAdded: Int = 0,
    val sleepNightsKept: Int = 0,
    val stepDaysAdded: Int = 0,
    val weighInsAdded: Int = 0,
) {
    val nothingNew: Boolean
        get() = sleepNightsAdded == 0 && stepDaysAdded == 0 && weighInsAdded == 0
}

object HealthImport {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun dayKeyOf(instant: Instant): String = instant.atZone(zone).toLocalDate().toString()

    fun localIso(instant: Instant): String = LocalDateTime.ofInstant(instant, zone).toString()

    /**
     * One sleep session becomes one block, filed under the day it ended.
     *
     * Sessions shorter than [minMinutes] are dropped. A watch logs a great deal of ten-minute
     * "sleep" while you sit still on a sofa, and letting those through would flood the nap count and
     * drag the main-sleep average toward nonsense.
     */
    fun toSleepImport(startEnd: List<Pair<Instant, Instant>>, minMinutes: Long = 45): List<SleepImport> =
        startEnd.mapNotNull { (start, end) ->
            val minutes = java.time.Duration.between(start, end).toMinutes()
            if (minutes < minMinutes) return@mapNotNull null
            SleepImport(dayKeyOf(end), localIso(start), localIso(end))
        }

    /**
     * Fold imported sleep into the day rows that already exist.
     *
     * A day that already has `sleeps` is left completely alone — see the rule at the top of this
     * file. Returns the rows to write plus a count of what was skipped, because "we imported 0
     * nights" and "you already had all 14 logged" are different sentences and the second one is not
     * a failure.
     */
    fun mergeSleep(
        imports: List<SleepImport>,
        existing: Map<String, DayRow>,
    ): Pair<List<DayRow>, ImportSummary> {
        val byDay = imports.groupBy { it.dayKey }
        val out = mutableListOf<DayRow>()
        var added = 0
        var kept = 0
        for ((dayKey, blocks) in byDay) {
            val row = existing[dayKey]
            if (row?.sleeps != null) {
                kept += 1
                continue
            }
            val json = blocks.joinToString(",", prefix = "[", postfix = "]") {
                """{"start":"${it.startIso}","end":"${it.endIso}"}"""
            }
            // The wake time is written to the legacy field too, from the LONGEST block, so screens
            // that still read `wake` agree with what wakePattern() computes off the blocks.
            val main = blocks.maxByOrNull {
                java.time.Duration.between(
                    LocalDateTime.parse(it.startIso), LocalDateTime.parse(it.endIso),
                ).toMinutes()
            }
            val wake = main?.endIso?.let { LocalDateTime.parse(it).toLocalTime().toString().take(5) }
            val bed = main?.startIso?.let { LocalDateTime.parse(it).toLocalTime().toString().take(5) }
            out.add(
                (row ?: DayRow(dayKey, null, null, null, null, null, null))
                    .copy(sleeps = json, bed = bed, wake = wake),
            )
            added += 1
        }
        return out to ImportSummary(sleepNightsAdded = added, sleepNightsKept = kept)
    }

    /**
     * Step records summed per day.
     *
     * Health Connect returns steps as many small records, not one daily total — a phone writes a
     * batch every few minutes and a watch writes its own. Summing per calendar day is the only way to
     * get "steps today"; taking the largest record instead would report a single walk.
     */
    fun toStepImports(records: List<Pair<Instant, Long>>): List<StepImport> =
        records
            .groupBy { dayKeyOf(it.first) }
            .map { (day, sameDay) -> StepImport(day, sameDay.sumOf { it.second }.toInt()) }

    /**
     * Steps overwrite freely, unlike sleep.
     *
     * A step count is a total the phone owns and revises upward all day; there is no hand-logged
     * version of it to protect, and the newest number is always the better one.
     */
    fun mergeSteps(
        imports: List<StepImport>,
        existing: Map<String, DayRow>,
    ): Pair<List<DayRow>, Int> {
        val out = imports
            .filter { it.steps > 0 && existing[it.dayKey]?.steps != it.steps }
            .map { imp ->
                (existing[imp.dayKey] ?: DayRow(imp.dayKey, null, null, null, null, null, null))
                    .copy(steps = imp.steps)
            }
        return out to out.size
    }

    /**
     * One weigh-in per day, the LAST of that day.
     *
     * Weight is stored keyed by day, so a day with three readings has to pick one. The last is taken
     * rather than the mean: a mean of a morning and an evening reading is a number no scale ever
     * showed, and the 28-day trend only needs consistency, not precision.
     */
    fun toWeightImports(readings: List<Pair<Instant, Double>>): List<WeightImport> =
        readings
            .sortedBy { it.first }
            .groupBy { dayKeyOf(it.first) }
            .map { (day, sameDay) -> WeightImport(day, sameDay.last().second) }

    fun mergeWeights(
        imports: List<WeightImport>,
        existing: List<Weight>,
    ): Pair<List<Weight>, Int> {
        val have = existing.associateBy { it.at }
        val out = imports
            .filter { have[it.dayKey]?.kg != it.kg }
            .map { Weight(at = it.dayKey, kg = it.kg) }
        return out to out.size
    }

    /** The window to ask Health Connect for: enough to fill TInputs' 28-day read, and no more. */
    fun windowStart(today: LocalDate, days: Int = TInputs.WINDOW): Instant =
        today.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant()
}
