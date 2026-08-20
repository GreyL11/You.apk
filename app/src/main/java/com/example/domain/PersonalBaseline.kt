package com.example.domain

import com.example.data.DayRow
import com.example.data.LogEntry
import com.example.data.Meal
import com.example.data.Weight
import kotlin.math.roundToInt

/**
 * Learns the user's own normal ranges from what they've actually logged — never a generic
 * guideline. "You're 35% below your usual activity" is a claim this data can support; "you need
 * 10,000 steps" is not this file's job. Every baseline carries its own sample size and confidence,
 * and [compare] refuses to say anything once there isn't enough evidence to say it against.
 *
 * The foundation several later engines (goal-gap, bottleneck, next-best-action) will read from —
 * kept deliberately small and separate rather than folded into any one of them, since "what is
 * normal for this person" is a fact about the user, not a decision any single engine owns.
 */
object PersonalBaseline {
    /** Below this many samples there is nothing to compare against — the same "at least 4"
     *  evidence floor Skin.kt already uses for its own comparisons. */
    const val MIN_SAMPLES = 4
    private const val MODERATE_SAMPLES = 10
    private const val HIGH_SAMPLES = 20

    enum class Confidence { INSUFFICIENT, LOW, MODERATE, HIGH }

    data class Metric(val typical: Double?, val sampleSize: Int, val confidence: Confidence)

    private fun confidenceFor(n: Int): Confidence = when {
        n < MIN_SAMPLES -> Confidence.INSUFFICIENT
        n < MODERATE_SAMPLES -> Confidence.LOW
        n < HIGH_SAMPLES -> Confidence.MODERATE
        else -> Confidence.HIGH
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    private fun metricFrom(values: List<Double>): Metric {
        val n = values.size
        return Metric(if (n >= MIN_SAMPLES) median(values) else null, n, confidenceFor(n))
    }

    /** Median main-sleep hours, one sample per day that actually has a sleep entry — a day with
     *  nothing logged is absent from the sample, never counted as zero hours. */
    fun sleepHours(dayRows: List<DayRow>): Metric =
        metricFrom(dayRows.mapNotNull { MoodInsights.sleepSummary(it).main })

    /** Real training frequency, from distinct calendar days with a logged session over whatever
     *  window [logs] covers — a single derived rate, not a list of daily samples, so it earns
     *  confidence from how many days were actually observed rather than from a sample count. */
    fun trainingSessionsPerWeek(logs: List<LogEntry>, windowDays: Int): Metric {
        if (windowDays <= 0) return Metric(null, 0, Confidence.INSUFFICIENT)
        val distinctDays = logs.map { it.at.take(10) }.distinct().size
        val confidence = when {
            windowDays < 14 -> Confidence.INSUFFICIENT
            windowDays < 28 -> Confidence.LOW
            windowDays < 56 -> Confidence.MODERATE
            else -> Confidence.HIGH
        }
        val perWeek = distinctDays.toDouble() / windowDays * 7.0
        return Metric(if (confidence == Confidence.INSUFFICIENT) null else perWeek, distinctDays, confidence)
    }

    /** Median daily fluid ml, one sample per day that has any drink logged at all — an unlogged
     *  day is absent, not a zero-hydration day. */
    fun dailyFluidMl(meals: List<Meal>): Metric {
        val daily = meals.groupBy { it.at.take(10) }
            .values
            .map { dayMeals -> Nutrition.fluid(dayMeals.map { m -> m to Nutrition.FOODS.find { f -> f.id == m.foodId }?.ml }) }
            .filter { it > 0 }
            .map { it.toDouble() }
        return metricFrom(daily)
    }

    /** Median bodyweight over whatever history is given — the range the user actually lives in,
     *  not their latest single reading (see [Nutrition.weightTrend] for direction/trend). */
    fun bodyweightKg(weights: List<Weight>): Metric = metricFrom(weights.map { it.kg })

    /** How today's [value] compares to [baseline] — null unless there's real evidence to compare
     *  against, and always phrased against the user's OWN typical, never a generic target. */
    fun compare(value: Double?, baseline: Metric): String? {
        val typical = baseline.typical
        if (value == null || typical == null || typical == 0.0 || baseline.confidence == Confidence.INSUFFICIENT) return null
        val pct = ((value - typical) / typical * 100.0).roundToInt()
        return when {
            pct <= -20 -> "${-pct}% below your normal"
            pct >= 20 -> "$pct% above your normal"
            else -> "close to your normal"
        }
    }
}
