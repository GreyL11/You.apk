package com.example.domain

import com.example.data.DayRow
import com.example.data.LogEntry
import com.example.data.Round
import com.example.data.Weight
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * The lifestyle inputs that move testosterone, read out of data the app already keeps.
 *
 * This file does NOT estimate testosterone, and nothing in the app can. There is no sensor, no
 * questionnaire and no training-load model that reads a hormone — a number produced that way is
 * invented. Testosterone is a morning blood draw, twice, read by a doctor.
 *
 * What is honest is the other direction: three things with real evidence behind them move it, and
 * the app is already logging all three, so it can say whether you are doing them. That is a smaller
 * claim than a score, and it is the only one the data supports.
 *
 *   Sleep     A week at 5h dropped daytime T 10-15% in healthy young men (Leproult & Van Cauter,
 *             JAMA 2011). The clearest modifiable input, and the one most people are short on.
 *   Body fat  Strong inverse association; adipose tissue aromatises testosterone to oestradiol.
 *             See the caveat on [TInputs.weight] — the app knows kilos, not fat.
 *   Training  Acts mostly through body composition. Both ends hurt.
 *
 * Ported from the legacy `t_inputs.js`, whose wording is kept deliberately: every sentence here is
 * one the legacy app already ships and tests.
 */
data class SleepRead(
    val verdict: String,
    val nights: Int,
    val avg: Double? = null,
    val totalAvg: Double? = null,
    val napDays: Int = 0,
)

/** Direction only, deliberately unjudged — see [TInputs.weight]. */
data class WeightRead(val verdict: String, val points: Int, val kg: Double? = null)

data class TrainingRead(val verdict: String, val days: Int)

data class WakeRead(
    val median: String,
    val spreadMins: Int,
    val spreadHours: Double,
    val regular: Boolean,
    val nights: Int,
)

data class TAdvice(val text: String?, val plan: String?)

data class TResult(
    val sleep: SleepRead,
    val weight: WeightRead,
    val training: TrainingRead,
    val wake: WakeRead?,
    val advice: TAdvice,
)

object TInputs {
    /** Four weeks: long enough to survive one bad week, short enough to still be about now. */
    const val WINDOW = 28
    const val SLEEP_TARGET = 7
    const val SLEEP_LOW = 6

    /** Below this many logged nights in the window, the average is not worth reporting. */
    const val MIN_NIGHTS = 10

    /** Roughly twice a week. Below it, the body-composition route is not being taken. */
    const val TRAIN_LOW = 8

    /**
     * How far apart wake times may sit before "your usual wake time" stops being a real thing.
     *
     * Three hours. Inside that, a median describes a habit and a bedtime computed from it is
     * something you can actually do. Outside it, the median is an average of a shift pattern — a
     * number no morning ever looked like — and prescribing a clock time from it is the app
     * inventing a routine on someone's behalf.
     */
    const val REGULAR_SPREAD_MINS = 180

    const val HORMONAL_BOUNDARY =
        "We cannot determine testosterone levels from lifestyle tracking. This shows lifestyle " +
            "conditions with real evidence behind them, not a hormone measurement — that needs a " +
            "blood test read by a doctor."

    private fun round1(n: Double) = Math.round(n * 10) / 10.0

    /** 7.0 prints as "7", 7.5 as "7.5" — an hour count, not a measurement to two places. */
    private fun num(n: Double) = if (n == Math.floor(n)) n.toInt().toString() else n.toString()

    /** The MAIN sleep each night, averaged — plus what the naps added, reported separately. */
    fun sleep(rows: List<DayRow>): SleepRead {
        val main = mutableListOf<Double>()
        val total = mutableListOf<Double>()
        var napDays = 0
        for (row in rows) {
            val s = MoodInsights.sleepSummary(row)
            val m = s.main ?: continue
            main.add(m)
            total.add(s.total ?: m)
            if (s.naps > 0) napDays += 1
        }
        if (main.size < MIN_NIGHTS) return SleepRead("unknown", main.size)
        val avg = round1(main.average())
        val totalAvg = round1(total.average())
        val verdict = if (avg < SLEEP_LOW) "low" else if (avg < SLEEP_TARGET) "under" else "good"
        // The total is only worth saying when it differs from the main sleep — otherwise it is the
        // same number twice, and a second identical figure reads as if it means something new.
        return if (totalAvg > avg) SleepRead(verdict, main.size, avg, totalAvg, napDays)
        else SleepRead(verdict, main.size, avg)
    }

    /**
     * Weight movement over the window — direction only, deliberately unjudged.
     *
     * The evidence is about body fat, and the app does not know your body fat. Gaining muscle and
     * gaining fat are the same number here and point opposite ways, so calling a direction "bad"
     * would be a guess dressed as a reading.
     */
    fun weight(weights: List<Weight>, cutoff: String): WeightRead {
        val inWindow = weights.filter { it.at >= cutoff }.sortedBy { it.at }
        if (inWindow.size < 2) return WeightRead("unknown", inWindow.size)
        return WeightRead("known", inWindow.size, round1(inWindow.last().kg - inWindow.first().kg))
    }

    fun training(logs: List<LogEntry>, rounds: List<Round>, cutoff: String): TrainingRead {
        val days = MoodInsights.trainedDays(logs, rounds).count { it >= cutoff }
        return TrainingRead(if (days < TRAIN_LOW) "low" else "good", days)
    }

    /**
     * When you actually wake, and whether "actually" means anything.
     *
     * Clock times are circular, which the obvious implementation gets wrong: waking at 23:00 and at
     * 01:00 is a two-hour spread, not twenty-two. So the spread here is the SMALLEST ARC containing
     * every wake time — the largest gap between neighbours around the circle, subtracted from the
     * day — and the median is taken inside that arc rather than on the raw numbers. Without this,
     * one late night flips a perfectly regular sleeper to "irregular".
     *
     * Reads the end of each day's main sleep, falling back to the legacy `wake` field for days
     * logged before sleep blocks existed.
     */
    fun wakePattern(rows: List<DayRow>): WakeRead? {
        val mins = mutableListOf<Int>()
        for (row in rows) {
            val end = MoodInsights.sleepBlocks(row).firstOrNull()?.end
            val fromBlock = if (end == null) null else try {
                LocalDateTime.parse(end).let { it.hour * 60 + it.minute }
            } catch (e: DateTimeParseException) {
                null
            }
            val m = fromBlock ?: MoodInsights.clockMins(row.wake)
            if (m != null) mins.add(m)
        }
        if (mins.isEmpty()) return null

        val sorted = mins.sorted()
        var gap = sorted[0] + 1440 - sorted.last() // the wrap-around gap
        var origin = sorted[0]
        for (i in 1 until sorted.size) {
            if (sorted[i] - sorted[i - 1] > gap) {
                gap = sorted[i] - sorted[i - 1]
                origin = sorted[i]
            }
        }
        val spread = 1440 - gap
        val rotated = sorted.map { (it - origin + 1440) % 1440 }.sorted()
        return WakeRead(
            median = MoodInsights.clock((origin + rotated[rotated.size / 2]) % 1440),
            spreadMins = spread,
            spreadHours = round1(spread / 60.0),
            regular = spread <= REGULAR_SPREAD_MINS,
            nights = mins.size,
        )
    }

    /** The wake time actually kept, or null when there is no such thing. */
    fun usualWake(rows: List<DayRow>): String? = wakePattern(rows)?.takeIf { it.regular }?.median

    /**
     * The one thing worth doing next, as text plus a plan that can be handed to the Mind tab.
     *
     * Naming the shortfall is not advice — "your sleep is the problem" leaves you holding it. Where
     * the data allows, this states the actual move: your own median wake time minus the target gives
     * a bedtime, which is a thing you can do tonight rather than a thing to feel bad about.
     *
     * Deliberately one line and one plan. A list of five is a list nobody acts on, and the inputs
     * are not equal — sleep is both the best evidenced and the most commonly short.
     */
    fun advice(r: TResult, wake: String? = null): TAdvice {
        // A fresh install has no training days, no nights and no weigh-ins. Reading that back as "0
        // training days" is a judgement drawn from absent data, which is the one thing the rest of
        // this app refuses to do. Silence about the inputs, and say what would make them readable.
        if (r.sleep.verdict == "unknown" && r.weight.verdict == "unknown" && r.training.days == 0) {
            return TAdvice(
                "Nothing logged yet. Log sleep on the Mind tab and finish a session, and this " +
                    "starts telling you something.",
                null,
            )
        }

        val bedtime = MoodInsights.clockMins(wake)
            ?.let { MoodInsights.clock((it - SLEEP_TARGET * 60 + 1440) % 1440) }

        if (r.sleep.verdict == "low" || r.sleep.verdict == "under") {
            val worst = r.sleep.verdict == "low"
            // Training on the floor outranks a merely-short night, but not a genuinely bad one.
            if (!worst && r.training.verdict == "low") return trainAdvice(r)
            val avg = num(r.sleep.avg ?: 0.0)
            if (bedtime != null) {
                return TAdvice(
                    if (worst) {
                        "Averaging ${avg}h, waking at $wake. Lights off by $bedtime is the single " +
                            "change with the clearest effect."
                    } else {
                        "Averaging ${avg}h, waking at $wake. Lights off by $bedtime gets you to " +
                            "${SLEEP_TARGET}h."
                    },
                    "Lights off by $bedtime",
                )
            }

            // A wake time exists but it moves around. Prescribing a bedtime off the median of a
            // rotating schedule would name an hour no morning of theirs ever looked like, so the
            // target moves from a clock time to a length — the part they can act on whenever their
            // day starts.
            val w = r.wake
            if (w != null && !w.regular) {
                return TAdvice(
                    "Main sleep is averaging ${avg}h, and your wake time moves across about " +
                        "${num(w.spreadHours)} hours, so there is no usual hour to set a bedtime " +
                        "against. Aim for ${SLEEP_TARGET}h in the main block, whenever it starts.",
                    "${SLEEP_TARGET}h in the main sleep",
                )
            }

            // No wake time logged at all, so no bedtime can be computed — name a shift, not an hour.
            return TAdvice(
                "Averaging ${avg}h. " +
                    (
                        if (worst) "Sleep is the input with the clearest effect."
                        else "${SLEEP_TARGET}h is where the evidence sits."
                        ) +
                    " Log a wake time and this can name the hour.",
                "Lights off 45 minutes earlier",
            )
        }

        if (r.training.verdict == "low") return trainAdvice(r)

        if (r.sleep.verdict == "unknown") {
            return TAdvice(
                "Only ${r.sleep.nights} nights logged in $WINDOW. Log sleep on the Mind tab and " +
                    "this becomes worth reading.",
                null,
            )
        }
        return TAdvice(null, null)
    }

    private fun trainAdvice(r: TResult) = TAdvice(
        "${r.training.days} training days in $WINDOW. Twice a week is the floor — put the next one " +
            "in tomorrow's plan.",
        "Train",
    )

    /**
     * How well each evidenced input is actually supported by what you have logged.
     *
     * Three states and no score. The temptation here is to weight them and add up to a number, which
     * would be a testosterone estimate wearing a different hat — and there is no published weighting
     * that turns "7.2h of sleep and 9 sessions" into a hormone level, so any weights would be invented
     * by whoever wrote the code.
     *
     * Body composition can never reach SUPPORTED. The evidence is about body FAT and the app only
     * knows kilos, so the most it can honestly say is that the direction is known.
     */
    enum class FactorState { SUPPORTED, PARTIAL, ABSENT }

    data class Factor(val name: String, val state: FactorState, val detail: String)

    fun factors(r: TResult): List<Factor> {
        val sleep = when (r.sleep.verdict) {
            "good" -> Factor(
                "Sleep",
                FactorState.SUPPORTED,
                "${num(r.sleep.avg ?: 0.0)}h main sleep across ${r.sleep.nights} nights, at or above the ${SLEEP_TARGET}h the evidence uses.",
            )
            "under", "low" -> Factor(
                "Sleep",
                FactorState.PARTIAL,
                "${num(r.sleep.avg ?: 0.0)}h average — short of ${SLEEP_TARGET}h. A week at 5h dropped daytime T 10-15% in healthy young men (Leproult & Van Cauter, JAMA 2011).",
            )
            else -> Factor(
                "Sleep",
                FactorState.ABSENT,
                "Only ${r.sleep.nights} of $WINDOW nights logged, which is too few to read an average from.",
            )
        }

        val training = when {
            r.training.verdict == "good" -> Factor(
                "Training",
                FactorState.SUPPORTED,
                "${r.training.days} days in $WINDOW, above the twice-a-week floor.",
            )
            r.training.days > 0 -> Factor(
                "Training",
                FactorState.PARTIAL,
                "${r.training.days} days in $WINDOW. Twice a week ($TRAIN_LOW in the window) is where the body-composition route starts.",
            )
            else -> Factor(
                "Training",
                FactorState.ABSENT,
                "Nothing logged in $WINDOW days.",
            )
        }

        val body = if (r.weight.verdict == "known") Factor(
            "Body composition",
            FactorState.PARTIAL,
            "${r.weight.kg} kg across ${r.weight.points} weigh-ins. Direction only — the evidence is about body fat, which this app cannot measure, so it is not judged either way.",
        ) else Factor(
            "Body composition",
            FactorState.ABSENT,
            "Fewer than two weigh-ins in $WINDOW days, so there is no direction to read.",
        )

        return listOf(sleep, training, body)
    }

    /**
     * The whole read, over records straight out of Room.
     *
     * [rows] may be every day ever logged; only the window is used.
     */
    fun read(
        rows: List<DayRow>,
        weights: List<Weight>,
        logs: List<LogEntry>,
        rounds: List<Round>,
        today: LocalDate,
        n: Int = WINDOW,
    ): TResult {
        val cutoff = today.minusDays((n - 1).toLong()).toString()
        val inWindow = rows.filter { it.dayKey >= cutoff && it.dayKey <= today.toString() }
        val partial = TResult(
            sleep = sleep(inWindow),
            weight = weight(weights, cutoff),
            training = training(logs, rounds, cutoff),
            wake = wakePattern(inWindow),
            advice = TAdvice(null, null),
        )
        return partial.copy(advice = advice(partial, partial.wake?.takeIf { it.regular }?.median))
    }
}
