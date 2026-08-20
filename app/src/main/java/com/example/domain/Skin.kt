package com.example.domain

import com.example.data.DayRow
import com.example.data.LogEntry
import com.example.data.Meal
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Skin, read against everything this app already logs — a faithful port of the legacy `skin.js`.
 *
 * WHY THIS EXISTS AT ALL, given every phone already has a dozen skincare apps: those apps know what
 * you put ON your face and nothing else. This one already logs, by name, the two dietary things
 * with the most real evidence behind skin flare-ups — dairy (whey especially) and high-glycaemic
 * food — plus sleep, stress, and which days you trained. Nobody else has both halves.
 *
 * WHAT IT WILL NOT DO: it cannot see your face and it is not a doctor. It never names a condition,
 * never says whether something is normal or bad, never suggests a medicine, and never claims one
 * thing caused another. It reports what YOUR OWN log shows alongside what your skin did, says how
 * thin the evidence is, and offers the one move an individual can actually make: change one thing
 * for two weeks and look at the difference.
 */
object Skin {
    /** Skin logged 1 (bad day) to 5 (good day). One number, because a person will answer one
     *  number every day and will not fill in a form. */
    val SCALE = 1..5

    data class Flag(val id: String, val label: String)

    /** Descriptive, never diagnostic — "sore spots" is an observation, "acne" is a diagnosis and
     *  not this app's to make. */
    val FLAGS = listOf(
        Flag("breakout", "Breaking out"),
        Flag("oily", "Oily"),
        Flag("dry", "Dry or tight"),
        Flag("red", "Red or irritated"),
        Flag("sore", "Sore spots"),
        Flag("puffy", "Puffy / tired"),
    )

    data class Habit(val id: String, val label: String, val why: String)

    /** Fixed, short, not personalised — the evidence behind these does not vary by person, and
     *  dressing a known checklist up as a daily insight would be the fake kind of intelligence this
     *  file exists to avoid. Sunscreen is first: the single best-evidenced thing anyone can do for
     *  how skin looks over years, and the one most often skipped training indoors. */
    val HABITS = listOf(
        Habit("spf", "Sunscreen", "The best evidenced thing you can do for skin over years. Daylight counts, indoors or not."),
        Habit("washPost", "Washed after training", "Sweat sitting under a cap or a collar is the most avoidable irritation a lifter has."),
        Habit("moisturise", "Moisturised", "A barrier that holds water is calmer. Nothing clever required."),
        Habit("nopick", "Left it alone", "Picking is what turns a spot that would have gone into a mark that stays."),
    )

    /** Anything below this many days on EITHER side of a comparison and there is nothing to say. */
    const val MIN_DAYS_PER_SIDE = 4

    /** Skin answers slowly — something eaten today shows up over the next few days, not this
     *  evening, so exposure is summed over the days BEFORE the day being scored. */
    const val LAG_DAYS = 3

    // Only the two food groups with real evidence behind them, kept narrow on purpose: a list that
    // quietly grows to "everything you ate" would find a pattern in noise every week.
    val DAIRY = setOf("whey", "milk", "curd", "greekYogurt", "paneer", "cottageCheese", "chai")
    val HIGH_GI = setOf("softDrink", "rice", "bread", "potato", "idli", "dosa", "banana")

    data class Factor(val id: String, val label: String, val change: String)
    val FACTORS = listOf(
        Factor("dairy", "dairy and whey", "Try two weeks with less of it — whey is the usual one for lifters."),
        Factor("sugar", "high-sugar and refined carbs", "Try two weeks with fewer sugary drinks and less white rice or bread."),
        Factor("sleep", "short sleep", "Try a fortnight of getting to bed earlier."),
        Factor("stress", "low mood days", "Worth mentioning to someone. Stress and skin travel together and neither is fixed by a cream."),
        Factor("training", "training days", "If you are not already, wash your face soon after a session rather than hours later."),
    )
    private val FACTOR_BY_ID = FACTORS.associateBy { it.id }

    /** Said whenever skin is on screen — not a disclaimer to bury. */
    const val SEE_SOMEONE =
        "Anything painful, spreading, or still there after a few weeks is worth a dermatologist rather than an app. This only reads what you logged."

    data class SkinEntry(val score: Int?, val flags: List<String>, val habits: List<String>)
    data class ScoredDay(val key: String, val score: Int, val flags: List<String>, val habits: List<String>)
    data class Adherence(val complete: Int, val of: Int)
    data class Association(
        val factor: String,
        val label: String,
        val change: String,
        /** Positive = skin scored better on the lower-exposure days. */
        val diff: Double,
        val highDays: Int,
        val lowDays: Int,
        val highScore: Double,
        val lowScore: Double,
    )
    data class Advice(val text: String, val habitId: String? = null, val factor: String? = null, val evidence: String)

    /** The exact shape `DayRow.skin` stores: `{score, flags, habits}`. */
    fun toJson(score: Int?, flags: List<String>, habits: List<String>): String = JSONObject().apply {
        put("score", score ?: JSONObject.NULL)
        put("flags", JSONArray(flags))
        put("habits", JSONArray(habits))
    }.toString()

    fun fromJson(data: String?): SkinEntry {
        if (data.isNullOrBlank()) return SkinEntry(null, emptyList(), emptyList())
        return try {
            val o = JSONObject(data)
            val score = if (o.isNull("score")) null else o.optInt("score")
            SkinEntry(score, jsonStrings(o, "flags"), jsonStrings(o, "habits"))
        } catch (e: JSONException) {
            SkinEntry(null, emptyList(), emptyList())
        }
    }

    private fun jsonStrings(o: JSONObject, key: String): List<String> {
        val arr = o.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun dayKeyOf(at: String) = at.take(10)
    private fun round1(n: Double) = Math.round(n * 10) / 10.0

    /** Every day that has a skin score, oldest first. */
    fun scored(dayRows: List<DayRow>): List<ScoredDay> =
        dayRows.mapNotNull { row ->
            val entry = fromJson(row.skin)
            entry.score?.let { ScoredDay(row.dayKey, it, entry.flags, entry.habits) }
        }.sortedBy { it.key }

    /** Of the last [n] days with a skin entry, how many had every routine habit logged. Adherence,
     *  not appearance — never implies the routine changed anything, only that it was followed. */
    fun routineAdherence(dayRows: List<DayRow>, n: Int = 10): Adherence {
        val rows = scored(dayRows).takeLast(n)
        val complete = rows.count { row -> HABITS.all { row.habits.contains(it.id) } }
        return Adherence(complete, rows.size)
    }

    private fun servingsOn(key: String, group: Set<String>, meals: List<Meal>): Double =
        meals.filter { dayKeyOf(it.at) == key && group.contains(it.foodId) }.sumOf { it.qty }

    /** Exposure to a factor over the LAG_DAYS before [key], since skin answers late. Inverted for
     *  sleep/stress so "more exposure" always means "worse," keeping every comparison reading the
     *  same way. */
    private fun exposureBefore(key: String, factor: String, meals: List<Meal>, dayRowsByKey: Map<String, DayRow>, logs: List<LogEntry>): Double? {
        val date = LocalDate.parse(key)
        val window = (1..LAG_DAYS).map { date.minusDays(it.toLong()).toString() }
        return when (factor) {
            "dairy" -> window.map { servingsOn(it, DAIRY, meals) }.average()
            "sugar" -> window.map { servingsOn(it, HIGH_GI, meals) }.average()
            "training" -> window.map { k -> if (logs.any { dayKeyOf(it.at) == k }) 1.0 else 0.0 }.average()
            "sleep" -> {
                val hrs = window.mapNotNull { k -> dayRowsByKey[k]?.let { MoodInsights.sleepSummary(it).main } }
                if (hrs.isEmpty()) null else -hrs.average()
            }
            "stress" -> {
                val moods = window.mapNotNull { k -> dayRowsByKey[k]?.mood?.toDouble() }
                if (moods.isEmpty()) null else -moods.average()
            }
            else -> null
        }
    }

    /** Split scored days by whether exposure to [factor] was above or below YOUR OWN median, and
     *  report what skin averaged on each side. Never a guideline comparison — only ever a
     *  comparison against your own data, and null whenever either side is too thin to mean
     *  anything. */
    fun association(factor: String, dayRows: List<DayRow>, meals: List<Meal>, logs: List<LogEntry>): Association? {
        val dayRowsByKey = dayRows.associateBy { it.dayKey }
        val rows = scored(dayRows).mapNotNull { r -> exposureBefore(r.key, factor, meals, dayRowsByKey, logs)?.let { r to it } }
        if (rows.size < MIN_DAYS_PER_SIDE * 2) return null

        val median = rows.map { it.second }.sorted()[rows.size / 2]
        val high = rows.filter { it.second > median }
        val low = rows.filter { it.second <= median }
        if (high.size < MIN_DAYS_PER_SIDE || low.size < MIN_DAYS_PER_SIDE) return null

        val f = FACTOR_BY_ID.getValue(factor)
        return Association(
            factor = factor, label = f.label, change = f.change,
            diff = round1(low.map { it.first.score.toDouble() }.average() - high.map { it.first.score.toDouble() }.average()),
            highDays = high.size, lowDays = low.size,
            highScore = round1(high.map { it.first.score.toDouble() }.average()),
            lowScore = round1(low.map { it.first.score.toDouble() }.average()),
        )
    }

    /** Everything readable, worst-looking factor first. */
    fun associations(dayRows: List<DayRow>, meals: List<Meal>, logs: List<LogEntry>): List<Association> =
        FACTORS.mapNotNull { association(it.id, dayRows, meals, logs) }.sortedByDescending { it.diff }

    /** One thing to do, and the honest reason for it. An unticked habit beats a correlation,
     *  because habits are known to work and a correlation from a few weeks of one person's
     *  self-scored data is a hint. A difference under half a point is inside the noise of how you
     *  happened to feel that morning, so it is not reported at all. */
    fun advice(dayRows: List<DayRow>, meals: List<Meal>, logs: List<LogEntry>): Advice {
        val rows = scored(dayRows)
        if (rows.size < MIN_DAYS_PER_SIDE * 2) {
            return Advice(
                text = "Log your skin for ${MIN_DAYS_PER_SIDE * 2 - rows.size} more days and this starts comparing it against your food, sleep and training.",
                habitId = "spf",
                evidence = "none yet",
            )
        }

        val recent = rows.takeLast(7)
        for (h in HABITS) {
            val doneDays = recent.count { it.habits.contains(h.id) }
            if (doneDays <= recent.size / 3.0) {
                return Advice(
                    text = "${h.label}: ${h.why}",
                    habitId = h.id,
                    evidence = "logged on $doneDays of your last ${recent.size} days",
                )
            }
        }

        val top = associations(dayRows, meals, logs).firstOrNull()
        if (top != null && top.diff >= 0.5) {
            return Advice(
                text = "Your skin scored ${top.diff} higher on the days after less ${top.label}. ${top.change}",
                factor = top.factor,
                evidence = "${top.lowDays} days vs ${top.highDays} — your own log, not a study. It shows the two move together, not that one causes the other.",
            )
        }

        return Advice(
            text = "Nothing in your log stands out against your skin right now. The habits are doing the work — keep them up.",
            evidence = "${rows.size} days logged, no factor above half a point",
        )
    }
}
