package com.example.domain

import com.example.data.DayRow
import com.example.data.LogEntry
import com.example.data.Round
import org.json.JSONArray
import java.time.Duration
import java.time.LocalDateTime

/**
 * Arithmetic over days already logged. No model, no network, nothing that has to be right about the
 * future. Ported from the legacy `mood_insights.js`.
 *
 * The rule this file carries over: a day you did not record is not a day you did not sleep, so
 * nothing logged returns null rather than zero.
 */
data class SleepBlock(
    val start: String?,
    val end: String?,
    val hours: Double,
    val legacy: Boolean = false,
)

/**
 * The main sleep and everything else, split rather than summed.
 *
 * The evidence the hormonal-lifestyle read rests on (Leproult & Van Cauter, JAMA 2011) is about
 * CONSOLIDATED nightly sleep. Adding a three-hour nap to a four-hour night and calling it seven
 * hours would report a number that study says nothing about, so the verdict comes from [main] and
 * [total] is reported beside it as what it is — time spent asleep.
 */
data class SleepSummary(
    val main: Double?,
    val total: Double?,
    val naps: Int,
    val blocks: List<SleepBlock>,
)

object MoodInsights {
    private val CLOCK = Regex("""^(\d{1,2}):(\d{2})$""")

    private fun round1(n: Double) = Math.round(n * 10) / 10.0

    /** 'HH:MM' → minutes past midnight, or null when it is not a clock time. */
    fun clockMins(t: String?): Int? {
        val m = CLOCK.find(t ?: "") ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }

    /** minutes past midnight → 'HH:MM'. */
    fun clock(mins: Int): String = "%02d:%02d".format(mins / 60, mins % 60)

    /**
     * 'HH:MM' → hours, crossing midnight. The legacy shape: two clock times on a day row, no dates.
     * Handles daytime sleep (10:00 → 16:00 is six hours) but cannot represent more than one sleep,
     * and the modulo means a full 24 hours reads as zero. Kept because days logged before blocks
     * existed are stored this way, and those days are still real.
     */
    fun sleepHours(bed: String?, wake: String?): Double? {
        val b = clockMins(bed) ?: return null
        val w = clockMins(wake) ?: return null
        return round1(((w - b + 1440) % 1440) / 60.0)
    }

    /**
     * Every sleep on a day row, longest first.
     *
     * `sleeps` holds `[{start, end}]` — real timestamps, so a block from Tuesday 22:00 to Wednesday
     * 22:00 is twenty-four hours rather than the zero clock times report. Falls back to the legacy
     * `bed`/`wake` pair only when there are no blocks: that pair is the same sleep written the old
     * way, so adding both would double-count it.
     *
     * Also reads `{duration}` blocks, which is what the first version of the sleep sheet wrote —
     * a length with no times. Those count as a night's sleep and contribute no wake time, which is
     * exactly what they are.
     */
    fun sleepBlocks(row: DayRow?): List<SleepBlock> {
        val blocks = mutableListOf<SleepBlock>()
        val raw = row?.sleeps
        if (raw != null) {
            val arr = try { JSONArray(raw) } catch (e: org.json.JSONException) { null }
            for (i in 0 until (arr?.length() ?: 0)) {
                val o = arr!!.optJSONObject(i) ?: continue
                val start = o.optString("start").ifEmpty { null }
                val end = o.optString("end").ifEmpty { null }
                val hours = if (start != null && end != null) {
                    try {
                        round1(
                            Duration.between(LocalDateTime.parse(start), LocalDateTime.parse(end))
                                .toMinutes() / 60.0,
                        )
                    } catch (e: java.time.format.DateTimeParseException) {
                        null
                    }
                } else if (o.has("duration")) {
                    round1(o.optDouble("duration", 0.0))
                } else {
                    null
                }
                if (hours != null && hours > 0) blocks.add(SleepBlock(start, end, hours))
            }
        }
        if (blocks.isNotEmpty()) return blocks.sortedByDescending { it.hours }

        val legacy = sleepHours(row?.bed, row?.wake)
        return if (legacy == null || legacy <= 0) emptyList()
        else listOf(SleepBlock(null, null, legacy, legacy = true))
    }

    fun sleepSummary(row: DayRow?): SleepSummary {
        val blocks = sleepBlocks(row)
        if (blocks.isEmpty()) return SleepSummary(null, null, 0, emptyList())
        return SleepSummary(
            main = blocks[0].hours,
            total = round1(blocks.sumOf { it.hours }),
            naps = blocks.size - 1,
            blocks = blocks,
        )
    }

    /** Which days were trained, straight out of the lifting log and the boxing rounds. */
    fun trainedDays(logs: List<LogEntry>, rounds: List<Round>): Set<String> =
        (logs.map { it.at } + rounds.map { it.at })
            .mapNotNull { at -> at.takeIf { it.length >= 10 }?.substring(0, 10) }
            .toSet()
}
