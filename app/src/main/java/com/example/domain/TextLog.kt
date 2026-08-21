package com.example.domain

/**
 * Logging a whole day by typing it, instead of filling in six sheets.
 *
 * "did bench 4x8 at 60, squats 3x10 60kg, 20 min treadmill, slept badly, energy 5" is what a person
 * will actually write after a session. This turns that into the same rows the app already stores, so
 * every engine downstream ([TrainingHistory], [ReadinessEngine], [DailyDecisionEngine], [Cardio])
 * keeps working unchanged — the intelligence did not need rebuilding, only the way data gets in.
 *
 * THE CONTAINMENT RULE, same as [MealParse]: the model's job is TRANSLATION, never judgement. It
 * maps words onto ids that already exist in [EXERCISES] and [Cardio.Mode], and numbers the person
 * actually said. Every field it returns is checked against a real catalogue or a real range here
 * before it can be written, and nothing is written until the person has seen it.
 *
 * WHY THAT IS ENOUGH: a wrong exercise id is a *visible* wrong answer — "Bench Press 4×8" you never
 * did is obvious on screen and one tap to drop. This is deliberately the same containment idea as
 * [Validate], applied to ids and ranges instead of claims.
 *
 * WHAT IT MUST NEVER DO: infer anything the person did not say. Absent difficulty stays null, absent
 * energy stays null. An unreported field is not a neutral 5 — [ReadinessEngine] reads null as "not
 * answered" and says so, and a filled-in default would manufacture evidence for a day nobody rated.
 */

/** One logged lift. [sets]/[reps]/[load] as stated; [difficulty] only if they actually said. */
data class ParsedSet(
    val exId: String,
    /** The catalogue's own name, so the confirm list shows what was understood, not a raw id. */
    val label: String,
    val sets: Int,
    val reps: Int,
    val load: Double,
    val difficulty: Int? = null,
)

data class ParsedCardio(
    val mode: Cardio.Mode,
    val minutes: Int,
    val effortRating: Int? = null,
)

/** Everything a check-in can carry. Every field independently nullable — saying "energy was 4"
 *  should log energy 4 and claim nothing whatsoever about stress. */
data class ParsedCheckIn(
    val energy: Int? = null,
    val soreness: Int? = null,
    val stress: Int? = null,
    val mood: Int? = null,
    val refreshed: Boolean? = null,
) {
    /** Nothing was actually reported — every field came back null after validation. */
    val isEmpty: Boolean
        get() = energy == null && soreness == null && stress == null && mood == null && refreshed == null
}

data class TextLogResult(
    val sets: List<ParsedSet> = emptyList(),
    val cardio: List<ParsedCardio> = emptyList(),
    val checkIn: ParsedCheckIn? = null,
    val meals: List<ParsedMeal> = emptyList(),
    val weightKg: Double? = null,
    /** Words the model returned that are not real ids. Shown, never silently dropped — knowing it
     *  missed the leg press is the difference between an incomplete log and a wrong one. */
    val unknown: List<String> = emptyList(),
) {
    /** Did it understand anything at all? Drives "nothing recognisable in that" messaging. */
    val isEmpty: Boolean
        get() = sets.isEmpty() && cardio.isEmpty() && meals.isEmpty() && weightKg == null &&
            (checkIn == null || checkIn.isEmpty)
}

object TextLog {
    /** Sane bounds. Outside these it is far more likely a misparse than a real set. */
    const val MAX_SETS = 30
    const val MAX_REPS = 200
    const val MAX_LOAD_KG = 500.0
    const val MAX_CARDIO_MINUTES = 600
    const val MIN_WEIGHT_KG = 25.0
    const val MAX_WEIGHT_KG = 400.0

    /** The id list handed to the model. Real ids and real names, straight off the catalogue. */
    fun exerciseCatalogue(): String =
        EXERCISES.values.joinToString("\n") { "${it.id} = ${it.name} (${it.group})" }

    fun cardioCatalogue(): String =
        Cardio.Mode.entries.filter { it != Cardio.Mode.NONE }
            .joinToString("\n") { "${it.name} = ${Cardio.label(it)}" }

    /** A 1..10 self-report, or null. Anything outside the scale is a misparse, not a clamp — a
     *  "12" silently becoming 10 would record a rating that was never given. */
    private fun scale10(v: Int?): Int? = if (v != null && v in 1..10) v else null

    /** A 1..3 report (easy/moderate/hard). Same reasoning as [scale10]. */
    private fun scale3(v: Int?): Int? = if (v != null && v in 1..3) v else null

    /**
     * Validate raw model output against the real catalogues and ranges.
     *
     * Every argument is what the model claimed; everything returned has been checked. Anything
     * unrecognised lands in [TextLogResult.unknown] rather than being dropped or coerced.
     */
    fun validate(
        rawSets: List<RawSet> = emptyList(),
        rawCardio: List<RawCardio> = emptyList(),
        rawCheckIn: RawCheckIn? = null,
        rawMeals: List<Pair<String, Double>> = emptyList(),
        rawWeightKg: Double? = null,
    ): TextLogResult {
        val unknown = mutableListOf<String>()

        val sets = rawSets.mapNotNull { r ->
            val def = EXERCISES[r.exId]
            if (def == null) {
                unknown.add(r.exId)
                return@mapNotNull null
            }
            // Reps are the one genuinely required number: "I did bench" with no reps is not a set,
            // and inventing 8 would fabricate volume every engine downstream then reasons over.
            val reps = r.reps
            if (reps == null || reps <= 0 || reps > MAX_REPS) {
                unknown.add(def.name)
                return@mapNotNull null
            }
            val sets = (r.sets ?: 1).coerceIn(1, MAX_SETS)
            // Load absent means bodyweight, which is a real answer for pushups and pullups; a
            // negative or absurd load is a misparse and the whole set is refused rather than guessed.
            val load = r.load ?: 0.0
            if (load < 0.0 || load > MAX_LOAD_KG) {
                unknown.add(def.name)
                return@mapNotNull null
            }
            ParsedSet(def.id, def.name, sets, reps, load, scale3(r.difficulty))
        }

        val cardio = rawCardio.mapNotNull { r ->
            val mode = Cardio.Mode.entries.firstOrNull { it.name == r.mode }
            if (mode == null || mode == Cardio.Mode.NONE) {
                unknown.add(r.mode)
                return@mapNotNull null
            }
            val minutes = r.minutes
            if (minutes == null || minutes <= 0 || minutes > MAX_CARDIO_MINUTES) {
                unknown.add(Cardio.label(mode))
                return@mapNotNull null
            }
            ParsedCardio(mode, minutes, scale3(r.effortRating))
        }

        val checkIn = rawCheckIn?.let {
            ParsedCheckIn(
                energy = scale10(it.energy),
                soreness = scale10(it.soreness),
                stress = scale10(it.stress),
                mood = scale10(it.mood),
                refreshed = it.refreshed,
            )
        }

        // Meals reuse the food table's own validator rather than repeating its rules here.
        val mealResult = if (rawMeals.isEmpty()) null else MealParse.validate(rawMeals)
        mealResult?.unknown?.let { unknown.addAll(it) }

        val weight = rawWeightKg?.takeIf { it in MIN_WEIGHT_KG..MAX_WEIGHT_KG }

        return TextLogResult(
            sets = sets,
            cardio = cardio,
            checkIn = checkIn,
            meals = mealResult?.items ?: emptyList(),
            weightKg = weight,
            unknown = unknown.distinct(),
        )
    }

    /** Raw, pre-validation shapes — exactly what the response schema promises, nothing more. */
    data class RawSet(val exId: String, val sets: Int?, val reps: Int?, val load: Double?, val difficulty: Int?)
    data class RawCardio(val mode: String, val minutes: Int?, val effortRating: Int?)
    data class RawCheckIn(val energy: Int?, val soreness: Int?, val stress: Int?, val mood: Int?, val refreshed: Boolean?)

    /** A short, plain-language summary of what was understood, for the confirm step and for the
     *  chat reply. Built from validated data only, so it can never describe something unlogged. */
    fun summary(r: TextLogResult): String {
        if (r.isEmpty) return "Nothing I could log in that."
        val parts = mutableListOf<String>()
        r.sets.forEach { s ->
            val load = if (s.load > 0) " @ ${if (s.load % 1.0 == 0.0) s.load.toInt().toString() else s.load.toString()}kg" else " (bodyweight)"
            parts.add("${s.label} ${s.sets}×${s.reps}$load")
        }
        r.cardio.forEach { c -> parts.add("${Cardio.label(c.mode)}, ${c.minutes} min") }
        r.meals.forEach { m -> parts.add("${m.label} ×${if (m.qty % 1.0 == 0.0) m.qty.toInt().toString() else m.qty.toString()}") }
        r.weightKg?.let { parts.add("weight ${it}kg") }
        r.checkIn?.let { c ->
            listOfNotNull(
                c.energy?.let { "energy $it/10" },
                c.mood?.let { "mood $it/10" },
                c.soreness?.let { "soreness $it/10" },
                c.stress?.let { "stress $it/10" },
                c.refreshed?.let { if (it) "woke refreshed" else "woke unrefreshed" },
            ).forEach { parts.add(it) }
        }
        return parts.joinToString("\n") { "• $it" }
    }
}
