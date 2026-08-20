package com.example.domain

import com.example.data.Meal
import com.example.data.Profile
import com.example.data.Weight
import java.time.LocalDate
import kotlin.math.roundToInt

object Nutrition {

    data class Food(
        val id: String,
        val label: String,
        val cat: String = "Other",
        val serving: String = "",
        val ml: Double? = null,
        val kcal: Double? = null,
        val prot: Double? = null,
        val carb: Double? = null,
        val fat: Double? = null,
        /** Grams of FREE/ADDED sugar per serving — not total carbs, and not the same thing as
         *  Skin.HIGH_GI's glycaemic-impact classification. Only set where the figure is genuinely
         *  known (a sugar-sweetened drink's carbs ARE essentially its sugar); left null everywhere
         *  else rather than guessed, including whole fruit, whose natural sugar the WHO free-sugar
         *  definition explicitly excludes. */
        val sugarG: Double? = null,
    )

    // Ported verbatim from the legacy app's FOODS table (www/nutrition.js) — same ids, same
    // per-serving macros, same categories. Legacy's own comment explains why this is a static
    // table rather than a food API or a model: no network/account in this app, a lifter eats the
    // same fifteen things, and a confidently wrong number is worse than a visible, editable one.
    val FOODS = listOf(
        // Protein
        Food("chickenBreast", "Chicken breast", cat = "Protein", serving = "100 g", kcal = 165.0, prot = 31.0, carb = 0.0, fat = 3.6),
        Food("chickenThigh", "Chicken thigh", cat = "Protein", serving = "100 g", kcal = 209.0, prot = 26.0, carb = 0.0, fat = 11.0),
        Food("egg", "Egg", cat = "Protein", serving = "1 large", kcal = 72.0, prot = 6.3, carb = 0.4, fat = 4.8),
        Food("eggWhite", "Egg white", cat = "Protein", serving = "1", kcal = 17.0, prot = 3.6, carb = 0.2, fat = 0.0),
        Food("whey", "Whey scoop", cat = "Protein", serving = "30 g", kcal = 120.0, prot = 24.0, carb = 3.0, fat = 1.5),
        Food("paneer", "Paneer", cat = "Protein", serving = "100 g", kcal = 296.0, prot = 18.0, carb = 3.4, fat = 22.0),
        Food("tofu", "Tofu, firm", cat = "Protein", serving = "100 g", kcal = 144.0, prot = 17.0, carb = 3.0, fat = 9.0),
        Food("soyaChunks", "Soya chunks, dry", cat = "Protein", serving = "50 g", kcal = 172.0, prot = 26.0, carb = 16.0, fat = 0.5),
        Food("greekYogurt", "Greek yogurt", cat = "Protein", serving = "100 g", kcal = 59.0, prot = 10.0, carb = 3.6, fat = 0.4),
        Food("curd", "Curd / plain yogurt", cat = "Protein", serving = "100 g", kcal = 61.0, prot = 3.5, carb = 4.7, fat = 3.3),
        Food("milk", "Milk, whole", cat = "Protein", serving = "250 ml", kcal = 149.0, prot = 7.7, carb = 12.0, fat = 8.0, ml = 250.0),
        Food("cottageCheese", "Cottage cheese", cat = "Protein", serving = "100 g", kcal = 98.0, prot = 11.0, carb = 3.4, fat = 4.3),
        Food("salmon", "Salmon", cat = "Protein", serving = "100 g", kcal = 208.0, prot = 20.0, carb = 0.0, fat = 13.0),
        Food("tuna", "Tuna, canned in water", cat = "Protein", serving = "100 g", kcal = 116.0, prot = 26.0, carb = 0.0, fat = 1.0),
        Food("beefMince", "Beef mince, lean", cat = "Protein", serving = "100 g", kcal = 250.0, prot = 26.0, carb = 0.0, fat = 15.0),
        Food("prawns", "Prawns", cat = "Protein", serving = "100 g", kcal = 99.0, prot = 24.0, carb = 0.2, fat = 0.3),
        Food("dal", "Dal, cooked", cat = "Protein", serving = "1 cup", kcal = 200.0, prot = 12.0, carb = 33.0, fat = 3.0),
        Food("rajma", "Rajma / kidney beans", cat = "Protein", serving = "1 cup", kcal = 225.0, prot = 15.0, carb = 40.0, fat = 1.0),
        Food("chickpeas", "Chickpeas, cooked", cat = "Protein", serving = "1 cup", kcal = 269.0, prot = 15.0, carb = 45.0, fat = 4.0),
        // Carbs
        Food("rice", "White rice, cooked", cat = "Carbs", serving = "1 cup", kcal = 205.0, prot = 4.3, carb = 45.0, fat = 0.4),
        Food("brownRice", "Brown rice, cooked", cat = "Carbs", serving = "1 cup", kcal = 218.0, prot = 5.0, carb = 46.0, fat = 1.6),
        Food("roti", "Roti / chapati", cat = "Carbs", serving = "1", kcal = 104.0, prot = 3.0, carb = 20.0, fat = 2.0),
        Food("bread", "Bread", cat = "Carbs", serving = "1 slice", kcal = 79.0, prot = 3.0, carb = 14.0, fat = 1.0),
        Food("oats", "Oats, dry", cat = "Carbs", serving = "50 g", kcal = 190.0, prot = 6.6, carb = 33.0, fat = 3.4),
        Food("potato", "Potato, boiled", cat = "Carbs", serving = "100 g", kcal = 87.0, prot = 2.0, carb = 20.0, fat = 0.1),
        Food("sweetPotato", "Sweet potato", cat = "Carbs", serving = "100 g", kcal = 90.0, prot = 2.0, carb = 21.0, fat = 0.2),
        Food("pasta", "Pasta, cooked", cat = "Carbs", serving = "1 cup", kcal = 220.0, prot = 8.0, carb = 43.0, fat = 1.3),
        Food("idli", "Idli", cat = "Carbs", serving = "1", kcal = 58.0, prot = 2.0, carb = 12.0, fat = 0.4),
        Food("dosa", "Dosa, plain", cat = "Carbs", serving = "1", kcal = 133.0, prot = 3.0, carb = 22.0, fat = 4.0),
        Food("banana", "Banana", cat = "Carbs", serving = "1 medium", kcal = 105.0, prot = 1.3, carb = 27.0, fat = 0.4),
        Food("apple", "Apple", cat = "Carbs", serving = "1 medium", kcal = 95.0, prot = 0.5, carb = 25.0, fat = 0.3),
        // Fats
        Food("peanutButter", "Peanut butter", cat = "Fats", serving = "1 tbsp", kcal = 94.0, prot = 4.0, carb = 3.0, fat = 8.0),
        Food("almonds", "Almonds", cat = "Fats", serving = "30 g", kcal = 173.0, prot = 6.0, carb = 6.0, fat = 15.0),
        Food("oliveOil", "Olive oil", cat = "Fats", serving = "1 tbsp", kcal = 119.0, prot = 0.0, carb = 0.0, fat = 13.5),
        Food("ghee", "Ghee", cat = "Fats", serving = "1 tsp", kcal = 45.0, prot = 0.0, carb = 0.0, fat = 5.0),
        Food("avocado", "Avocado", cat = "Fats", serving = "half", kcal = 160.0, prot = 2.0, carb = 9.0, fat = 15.0),
        // Veg
        Food("mixedVeg", "Mixed veg / sabzi", cat = "Veg", serving = "100 g", kcal = 35.0, prot = 2.0, carb = 7.0, fat = 0.3),
        Food("spinach", "Spinach, cooked", cat = "Veg", serving = "100 g", kcal = 23.0, prot = 2.9, carb = 3.6, fat = 0.4),
        Food("broccoli", "Broccoli", cat = "Veg", serving = "100 g", kcal = 35.0, prot = 2.4, carb = 7.0, fat = 0.4),
        Food("salad", "Salad, undressed", cat = "Veg", serving = "1 bowl", kcal = 25.0, prot = 1.5, carb = 5.0, fat = 0.2),
        // Other — `ml` is fluid that counts toward the day's water (see Nutrition.fluid). Alcohol
        // entries deliberately carry no `ml`: it's a diuretic, so counting it toward hydration
        // would be actively wrong (kept faithful to the legacy comment/behavior).
        Food("water", "Water", cat = "Other", serving = "250 ml", kcal = 0.0, prot = 0.0, carb = 0.0, fat = 0.0, ml = 250.0),
        Food("coffee", "Black coffee / tea", cat = "Other", serving = "1 cup", kcal = 2.0, prot = 0.3, carb = 0.0, fat = 0.0, ml = 200.0),
        Food("chai", "Chai with milk & sugar", cat = "Other", serving = "1 cup", kcal = 105.0, prot = 2.5, carb = 14.0, fat = 4.0, ml = 200.0),
        // sugarG set only where the whole serving's carbs ARE its added sugar (a sweetened drink) —
        // see Food.sugarG's own doc comment for why every other item here is left null, not guessed.
        Food("softDrink", "Soft drink", cat = "Other", serving = "330 ml", kcal = 139.0, prot = 0.0, carb = 35.0, fat = 0.0, ml = 330.0, sugarG = 35.0),
        Food("beer", "Beer", cat = "Other", serving = "330 ml", kcal = 143.0, prot = 1.6, carb = 11.0, fat = 0.0),
        Food("wine", "Wine", cat = "Other", serving = "150 ml", kcal = 125.0, prot = 0.1, carb = 4.0, fat = 0.0),
        Food("spirit", "Spirit, neat", cat = "Other", serving = "30 ml", kcal = 70.0, prot = 0.0, carb = 0.0, fat = 0.0)
    )

    val FOOD_CATS = listOf("Protein", "Carbs", "Veg", "Fats", "Other")

    fun waterTarget(profile: Profile?): Int {
        if (profile == null) return 0
        // round100(bodyweight_kg * 35 + 500 * daysPerWeek / 7)
        val raw = profile.bodyweight * 35.0 + 500.0 * profile.daysPerWeek / 7.0
        return (raw / 100.0).roundToInt() * 100
    }

    fun fluid(entries: List<Pair<Meal, Double?>>): Int {
        // fluid(entries) = round(Σ ml × qty)
        var totalFluid = 0.0
        for ((meal, ml) in entries) {
            if (ml != null) {
                totalFluid += ml * meal.qty
            }
        }
        return totalFluid.roundToInt()
    }

    data class Macros(val kcal: Int, val protein: Int, val carbs: Int, val fat: Int) {
        operator fun plus(other: Macros) =
            Macros(kcal + other.kcal, protein + other.protein, carbs + other.carbs, fat + other.fat)
    }

    /**
     * Add up the macros behind some (foodId, servings) pairs — port of the legacy `totals()`.
     *
     * An id that does not resolve against [FOODS] contributes nothing rather than a crash or a NaN:
     * a food deleted from the table should not poison every total that ever included it. Rounded
     * once at the end, not per item, so five half-servings don't drift from one whole one.
     */
    fun macros(entries: List<Pair<String, Double>>): Macros {
        var kcal = 0.0
        var protein = 0.0
        var carbs = 0.0
        var fat = 0.0
        for ((foodId, qty) in entries) {
            val f = FOODS.find { it.id == foodId } ?: continue
            kcal += (f.kcal ?: 0.0) * qty
            protein += (f.prot ?: 0.0) * qty
            carbs += (f.carb ?: 0.0) * qty
            fat += (f.fat ?: 0.0) * qty
        }
        return Macros(kcal.roundToInt(), protein.roundToInt(), carbs.roundToInt(), fat.roundToInt())
    }

    /**
     * Everything eaten — including a chai's real calories, which is the point of counting at all.
     *
     * @JvmName is required, not decoration: List<Meal> and List<Pair<String, Double>> both erase to
     * plain List at the JVM level, so without a distinct bytecode name this overload and the one
     * above collide as the same signature and the module fails to compile.
     */
    @JvmName("macrosOfMeals")
    fun macros(meals: List<Meal>): Macros = macros(meals.map { it.foodId to it.qty })

    // ── fat loss: the target, and the scale that corrects it ─────────────────────────────────
    //
    // Ported from the legacy `nutrition.js`, with one deliberate EXTENSION: the legacy app only ever
    // supported gaining (`GOAL_RATE` is +0.25%/wk for strength and hypertrophy, 0 for endurance —
    // there is no fat-loss entry anywhere in it). Losing needed a phase of its own.
    //
    // The phase is kept SEPARATE from `Profile.goal`, which is the training scheme (sets and reps —
    // see Planner.SCHEME_TABLE). Overloading one field would mean picking "lose fat" silently
    // rewrote someone's rep ranges, which is a different decision that nobody asked for.

    /** Four weeks — long enough for the scale to outrun water weight, short enough to be about now. */
    const val WINDOW_DAYS = 28

    /** Cutting, holding, or gaining. Stored as a setting, not on the Profile row — no migration. */
    enum class Phase { CUT, MAINTAIN, GAIN }

    fun phaseOf(raw: String?): Phase = when (raw?.lowercase()) {
        "cut" -> Phase.CUT
        "gain" -> Phase.GAIN
        else -> Phase.MAINTAIN
    }

    /** Rough daily calories per kg, by how often you train. Sedentary is ~28. */
    private val KCAL_PER_KG = mapOf(2 to 30.0, 3 to 32.0, 4 to 33.0, 5 to 35.0, 6 to 36.0)

    /**
     * What the phase does to that maintenance estimate.
     *
     * A 20% deficit is the usual sustainable cut — big enough to show on the scale inside a month,
     * small enough that training quality and lean mass survive it. Deeper deficits lose more muscle
     * per kg of fat, which is the opposite of what someone lifting wants.
     */
    private val PHASE_ADJUST = mapOf(Phase.CUT to 0.80, Phase.MAINTAIN to 1.0, Phase.GAIN to 1.10)

    /**
     * Protein per kg of bodyweight. Higher on a cut, and that is the one number here that is not
     * arbitrary: in a deficit, protein is what decides whether the weight leaving is fat or muscle.
     */
    private val PROTEIN_PER_KG = mapOf(Phase.CUT to 2.2, Phase.MAINTAIN to 1.8, Phase.GAIN to 1.8)

    /** Fat floor in g/kg — below this you are making your hormones worse to hit a macro. */
    private const val FAT_PER_KG = 0.8

    /**
     * Weekly change worth aiming for, as a fraction of bodyweight.
     *
     * -0.5%/wk cutting: the well-established rate that preserves lean mass. Faster is mostly a
     * bigger share of the loss coming out of muscle. +0.25%/wk gaining, from the legacy.
     */
    private val GOAL_RATE = mapOf(Phase.CUT to -0.005, Phase.MAINTAIN to 0.0, Phase.GAIN to 0.0025)

    /** Energy in a kg of bodyweight change. The standard figure; close enough over a month. */
    private const val KCAL_PER_KG_MASS = 7700.0

    /** Below this, a suggested correction is inside the noise of a bathroom scale. */
    private const val MIN_SUGGESTION = 100

    private fun round5(n: Double) = (n / 5.0).roundToInt() * 5

    data class Targets(val kcal: Int, val protein: Int, val carbs: Int, val fat: Int)

    /**
     * Daily targets from the profile already filled in for training.
     *
     * A STARTING POINT, exactly like `Planner.startingLoad()` — a population average applied to one
     * person. [suggestion] is the correction, and the scale is the only thing that can make it.
     *
     * `kcalTarget` on the profile, if set, replaces the formula. Protein and fat stay tied to
     * bodyweight either way; only carbs move, because carbs are the macro you actually eat more or
     * less of when the number changes.
     */
    fun targets(profile: Profile?, phase: Phase = Phase.MAINTAIN): Targets? {
        if (profile == null) return null
        val bw = profile.bodyweight
        val kcal = profile.kcalTarget
            ?: round5(bw * (KCAL_PER_KG[profile.daysPerWeek] ?: 32.0) * (PHASE_ADJUST[phase] ?: 1.0))
        val protein = (bw * (PROTEIN_PER_KG[phase] ?: 1.8)).roundToInt()
        val fat = (bw * FAT_PER_KG).roundToInt()
        // Carbs are whatever calories are left once protein and fat are paid for.
        val carbs = maxOf(0, ((kcal - protein * 4 - fat * 9) / 4.0).roundToInt())
        return Targets(kcal, protein, carbs, fat)
    }

    data class WeightTrend(val now: Double?, val changeKg: Double?, val days: Int, val points: Int)

    /**
     * Bodyweight over the last [days], and how much of it moved.
     *
     * The only honest check on the calorie target: the formula is a guess, the scale is a
     * measurement. Deliberately does NOT auto-adjust anything.
     *
     * Sorted here rather than trusted. Reading "latest" and "earliest" by position off an array that
     * arrived any other way — a restored backup, a hand-edited import — would not produce a slightly
     * wrong number, it would report the direction BACKWARDS: losing 800g reads as gaining it, and
     * every piece of advice built on top inverts with it.
     */
    fun weightTrend(weights: List<Weight>, today: LocalDate, days: Int = 28): WeightTrend {
        val cutoff = today.minusDays((days - 1).toLong()).toString()
        val ordered = weights.sortedBy { it.at }
        val window = ordered.filter { it.at >= cutoff }
        return WeightTrend(
            now = ordered.lastOrNull()?.kg,
            changeKg = if (window.size > 1) {
                Math.round((window.last().kg - window.first().kg) * 10) / 10.0
            } else null,
            days = days,
            points = window.size,
        )
    }

    data class Suggestion(
        val from: Int,
        val to: Int,
        val delta: Int,
        /** What you have actually been eating — the number that produced the observed change. */
        val eating: Int,
        val eatingDelta: Int,
        val observedRatePerWeek: Double,
        val goalRatePerWeek: Double,
        val reason: String,
    )

    /**
     * What the scale says your calorie target should actually be.
     *
     * This is the real feedback loop, and the only thing in the app that can genuinely tell you
     * whether your deficit is working: if you are losing slower than intended, the target was too
     * high, by exactly the energy the missing mass would have taken.
     *
     * Returns null whenever it should keep its mouth shut, which is most of the time:
     *   - fewer than 7 days of food logged, or fewer than 2 weigh-ins
     *   - under 10 days between the first and last weigh-in
     *   - the log is obviously incomplete, so the average it would correct is fiction
     *   - the correction is smaller than scale noise
     *
     * Deliberately a SUGGESTION, not an auto-adjustment. A week of water weight would otherwise walk
     * the target somewhere silly, and you are the one who knows you were ill, on holiday, or
     * carrying a fortnight of salt.
     *
     * @param loggedKcal one entry per day that had ANY food logged
     * @param weighIns (dayKey, kg), at least two, for the span check
     */
    fun suggestion(
        profile: Profile?,
        phase: Phase,
        loggedKcal: List<Int>,
        weighIns: List<Pair<String, Double>>,
    ): Suggestion? {
        val t = targets(profile, phase) ?: return null
        if (loggedKcal.size < 7 || weighIns.size < 2) return null

        val ordered = weighIns.sortedBy { it.first }
        val first = LocalDate.parse(ordered.first().first)
        val last = LocalDate.parse(ordered.last().first)
        val spanDays = java.time.temporal.ChronoUnit.DAYS.between(first, last).toInt()
        if (spanDays < 10) return null

        val avg = (loggedKcal.sum().toDouble() / loggedKcal.size).roundToInt()
        // Under-logging by a quarter is a logging problem, not a metabolism one. Correcting the
        // target for it would tell someone eating 3000 and logging 1800 to eat even less on a cut,
        // built entirely on a number that is simply wrong.
        if (avg < t.kcal * 0.75) return null

        val observedRate = ((ordered.last().second - ordered.first().second) / spanDays) * 7
        val goalRate = (GOAL_RATE[phase] ?: 0.0) * (profile?.bodyweight ?: 0.0)
        val delta = ((goalRate - observedRate) * KCAL_PER_KG_MASS) / 7
        if (Math.abs(delta) < MIN_SUGGESTION) return null

        // Anchored to what you ATE, not to the target you were given: the average intake is the
        // number that produced the observed change, so it is the only one that means anything.
        // Eating 2,200 and staying flat says maintenance is 2,200, whatever the formula claimed.
        val to = round5(avg + delta)
        // Clamped anyway — one month of scale data should nudge, not lurch.
        val bounded = round5(maxOf(t.kcal * 0.75, minOf(t.kcal * 1.25, to.toDouble())))
        if (bounded == t.kcal) return null

        val losing = phase == Phase.CUT
        val rateWord = "%.2f".format(Math.abs(observedRate))
        val goalWord = "%.2f".format(Math.abs(goalRate))
        return Suggestion(
            from = t.kcal,
            to = bounded,
            delta = bounded - t.kcal,
            eating = avg,
            eatingDelta = bounded - avg,
            observedRatePerWeek = Math.round(observedRate * 100) / 100.0,
            goalRatePerWeek = Math.round(goalRate * 100) / 100.0,
            reason = when {
                losing && observedRate >= 0 ->
                    "not losing yet — the scale is ${if (observedRate == 0.0) "flat" else "up ${rateWord} kg a week"}, against the ${goalWord} kg a week loss this is aiming for"
                losing ->
                    "losing ${rateWord} kg a week, against the ${goalWord} this is aiming for"
                observedRate < goalRate ->
                    "gaining ${if (observedRate <= 0) "nothing" else "$rateWord kg a week"}, slower than the ${goalWord} this is aiming for"
                else ->
                    "gaining ${rateWord} kg a week, faster than the ${goalWord} this is aiming for"
            },
        )
    }

    /**
     * The one line worth saying about the last four weeks, in priority order: fix the missing data
     * first, then call out a log that contradicts the scale, then the direction of travel.
     *
     * Nothing here is a health claim — it reads back what was logged against the phase that was
     * picked. There is no body-fat percentage anywhere in it, because nothing in this app measures
     * body fat: a scale measures mass, and mass is not composition.
     */
    fun coachLine(
        profile: Profile?,
        phase: Phase,
        loggedKcal: List<Int>,
        trend: WeightTrend,
    ): String {
        val t = targets(profile, phase)
        if (loggedKcal.size < 3) {
            return "Log a few more days of food and this starts telling you something."
        }
        val drift = trend.changeKg
            ?: return "${loggedKcal.size} days of food logged. Weigh yourself so it has something to check the calories against."
        val avg = (loggedKcal.sum().toDouble() / loggedKcal.size).roundToInt()
        if (t == null) return "Averaging $avg kcal a day over ${loggedKcal.size} days."

        // Weight moving the wrong way on a big deficit is not a metabolism result, it is missing
        // food. Say that before anything else, because every other reading is computed off a number
        // that is simply wrong.
        if (phase == Phase.CUT && drift > 0.5 && avg < t.kcal * 0.8) {
            return "Up ${drift} kg, but only $avg kcal a day logged against a ${t.kcal} target. " +
                "That means food is going unlogged — the advice is only as good as what goes in."
        }
        return when {
            phase == Phase.CUT && drift >= 0 ->
                "Averaging $avg kcal against a ${t.kcal} target, and ${if (drift == 0.0) "flat" else "up $drift kg"}. " +
                    "To lose, this has to come down — or the logging has to get tighter."
            phase == Phase.CUT ->
                "Averaging $avg kcal, down ${Math.abs(drift)} kg over ${trend.days} days. That is working."
            phase == Phase.GAIN && drift <= 0 ->
                "Averaging $avg kcal and ${if (drift == 0.0) "flat" else "down ${Math.abs(drift)} kg"}. " +
                    "To gain, eat more than this — the target says ${t.kcal}."
            else ->
                "Averaging $avg kcal against a ${t.kcal} target, ${if (drift > 0) "up $drift" else "$drift"} kg."
        }
    }

    // ── sugar: a target, and an honest read of what's actually known ────────────────────────
    //
    // There is no per-gram added-sugar figure for most of the catalogue above — inventing one
    // for rice, banana, or chai would be exactly the fabricated-precision this app's own house
    // style refuses everywhere else. This tracks only what [Food.sugarG] genuinely knows, and
    // says so, rather than silently treating "unknown" as "zero."

    /** WHO guidance: free sugars under 10% of total daily energy, at 4 kcal/g. Needs a real
     *  calorie target to convert against — see [targets]. */
    fun sugarTargetGrams(profile: Profile?, phase: Phase = Phase.MAINTAIN): Int? {
        val t = targets(profile, phase) ?: return null
        return ((t.kcal * 0.10) / 4.0).roundToInt()
    }

    data class SugarStatus(val knownGrams: Int, val unknownServings: Int)

    /** Sums only the foods with a real [Food.sugarG]; separately counts servings of anything
     *  logged that has none, so the UI can say "X g known (Y items not sugar-tracked)" instead of
     *  quietly implying those items contributed zero. */
    fun sugarStatus(meals: List<Meal>): SugarStatus {
        var known = 0.0
        var unknown = 0
        for (meal in meals) {
            val g = FOODS.find { it.id == meal.foodId }?.sugarG
            if (g != null) known += g * meal.qty else unknown++
        }
        return SugarStatus(known.roundToInt(), unknown)
    }
}
