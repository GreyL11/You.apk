package com.example.domain

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Kotlin port of legacy's www/planner.js — session scheduling, starting loads, and (the actual
// P0-3 gap this file exists to close) real plate-loadout snapping. Pure: no Room, no Android
// import. Fixes the audit's "load handling is effectively hardcoded/zero" and "plate-loadout
// snapping is unbuilt" findings.

data class TrainingProfile(
    val bodyweight: Double = 75.0,
    val trainingAge: String = "beginner", // beginner | intermediate | advanced
    val goal: String = "hypertrophy",     // strength | hypertrophy | endurance
    val daysPerWeek: Int = 3,
    val equipment: List<String> = listOf("barbell", "dumbbell", "cable", "bodyweight"),
    val injuries: List<String> = emptyList(),
    val bar: Double = 20.0,
    val plates: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
)

data class Loadout(val bar: Double, val perSide: List<Pair<Double, Int>>, val actual: Double, val exact: Boolean, val under: Boolean = false)

object Planner {
    /**
     * The plate denominations a profile-editing UI offers as choices -- port of the legacy
     * `app.js` PLATE_SIZES list. Not every gym's actual set: it is the CANDIDATE list a person
     * picks their real one from. [TrainingProfile.plates] is what they picked, and can be any
     * subset of this.
     */
    val PLATE_SIZES = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    private fun barOf(p: TrainingProfile) = p.bar
    private fun platesOf(p: TrainingProfile) = if (p.plates.isNotEmpty()) p.plates else TrainingProfile().plates

    /** The smallest total change possible on a barbell: one plate per side, so twice the smallest. */
    fun barbellStep(profile: TrainingProfile): Double = 2 * platesOf(profile).min()

    /** The nearest weight this gym's bar can actually be loaded to — port of `achievableLoad()`. */
    fun achievableLoad(target: Double, profile: TrainingProfile): Double {
        val bar = barOf(profile)
        if (target <= bar) return bar
        val step = barbellStep(profile)
        return Math.round((target - bar) / step) * step + bar
    }

    /** What to hang on each end, biggest plates first — port of `loadout()`. */
    fun loadout(total: Double, profile: TrainingProfile): Loadout {
        val bar = barOf(profile)
        if (total < bar) return Loadout(bar, emptyList(), bar, total == bar, under = true)
        var left = (total - bar) / 2
        val perSide = mutableListOf<Pair<Double, Int>>()
        for (kg in platesOf(profile).sortedDescending()) {
            val n = floor((left + 1e-9) / kg).toInt()
            if (n > 0) { perSide.add(kg to n); left -= n * kg }
        }
        val actual = Math.round((total - left * 2) * 100) / 100.0
        return Loadout(bar, perSide, actual, exact = left < 1e-9, under = false)
    }

    /** "Bar + 20 + 1.25 per side" — port of `loadoutText()`. */
    fun loadoutText(total: Double, profile: TrainingProfile): String {
        val l = loadout(total, profile)
        if (l.under) return "Less than the ${l.bar.fmt()} kg bar"
        if (l.perSide.isEmpty()) return "Empty bar"
        val discs = l.perSide.flatMap { (kg, n) -> List(n) { kg.fmt() } }.joinToString(" + ")
        return "Bar + $discs per side"
    }

    private fun Double.fmt(): String = if (this % 1 == 0.0) this.toInt().toString() else this.toString()

    private val EXPERIENCE = mapOf("beginner" to 1.0, "intermediate" to 1.35, "advanced" to 1.7)

    data class Scheme(val sets: Int, val reps: Int)
    private val SCHEME_TABLE = mapOf(
        "strength" to mapOf("compound" to Scheme(5, 5), "isolation" to Scheme(3, 8)),
        "hypertrophy" to mapOf("compound" to Scheme(4, 8), "isolation" to Scheme(3, 12)),
        "endurance" to mapOf("compound" to Scheme(3, 15), "isolation" to Scheme(3, 20)),
    )

    private fun round2(kg: Double) = max(0.0, Math.round(kg / 2.5) * 2.5)

    /** First-guess working weight. 0 means the lift is bodyweight — port of `startingLoad()`. */
    fun startingLoad(exId: String, profile: TrainingProfile): Double {
        val ex = EXERCISES[exId] ?: return 0.0
        if (ex.loadRatio == 0.0) return 0.0
        val raw = round2(profile.bodyweight * ex.loadRatio * (EXPERIENCE[profile.trainingAge] ?: 1.0))
        return if (ex.equipment == "barbell") achievableLoad(raw, profile) else raw
    }

    fun scheme(exId: String, profile: TrainingProfile): Scheme {
        val ex = EXERCISES[exId] ?: return Scheme(3, 10)
        val table = SCHEME_TABLE[profile.goal] ?: SCHEME_TABLE.getValue("hypertrophy")
        return if (ex.compound) table.getValue("compound") else table.getValue("isolation")
    }

    /** Heavy lower-body compounds jump 5kg/session; else 2.5 — never smaller than the bar can
     *  actually change by. Port of `increment()`. */
    fun increment(exId: String, profile: TrainingProfile): Double {
        val ex = EXERCISES[exId] ?: return 2.5
        val base = if (ex.compound && ex.group == "Legs") 5.0 else 2.5
        return if (ex.equipment == "barbell") max(base, barbellStep(profile)) else base
    }

    fun restSeconds(exId: String): Int = if (EXERCISES[exId]?.compound == true) 180 else 75

    /** Lifts you can actually do: the gear exists and nothing you're nursing rules them out. */
    fun available(profile: TrainingProfile): List<String> =
        EXERCISES.filter { (_, ex) -> profile.equipment.contains(ex.equipment) }
            .filter { (_, ex) -> ex.avoidFor.none { profile.injuries.contains(it) } }
            .keys.toList()

    // ── splits ───────────────────────────────────────────────────────────────────────────
    data class SplitSession(val name: String, val groups: List<String>)
    private val SPLITS: Map<Int, List<SplitSession>> = mapOf(
        2 to listOf(SplitSession("Full body A", listOf("Legs", "Chest", "Back", "Triceps")), SplitSession("Full body B", listOf("Legs", "Back", "Shoulders", "Biceps"))),
        3 to listOf(
            SplitSession("Full body A", listOf("Legs", "Chest", "Back", "Triceps")),
            SplitSession("Full body B", listOf("Legs", "Back", "Shoulders", "Biceps")),
            SplitSession("Full body C", listOf("Legs", "Chest", "Back", "Shoulders")),
        ),
        4 to listOf(
            SplitSession("Upper A", listOf("Chest", "Back", "Shoulders", "Triceps")),
            SplitSession("Lower A", listOf("Legs", "Legs", "Legs")),
            SplitSession("Upper B", listOf("Back", "Chest", "Biceps", "Shoulders")),
            SplitSession("Lower B", listOf("Legs", "Legs", "Legs")),
        ),
        5 to listOf(
            SplitSession("Push", listOf("Chest", "Shoulders", "Triceps", "Triceps")),
            SplitSession("Pull", listOf("Back", "Back", "Biceps", "Biceps")),
            SplitSession("Legs", listOf("Legs", "Legs", "Legs")),
            SplitSession("Upper", listOf("Chest", "Back", "Shoulders", "Biceps")),
            SplitSession("Lower", listOf("Legs", "Legs", "Legs")),
        ),
        6 to listOf(
            SplitSession("Push A", listOf("Chest", "Shoulders", "Triceps")),
            SplitSession("Pull A", listOf("Back", "Back", "Biceps")),
            SplitSession("Legs A", listOf("Legs", "Legs", "Legs")),
            SplitSession("Push B", listOf("Chest", "Shoulders", "Triceps")),
            SplitSession("Pull B", listOf("Back", "Back", "Biceps")),
            SplitSession("Legs B", listOf("Legs", "Legs", "Legs")),
        ),
    )
    private val TRAINING_DAYS: Map<Int, List<Int>> = mapOf(
        2 to listOf(1, 4), 3 to listOf(1, 3, 5), 4 to listOf(1, 2, 4, 5),
        5 to listOf(1, 2, 3, 5, 6), 6 to listOf(1, 2, 3, 4, 5, 6),
    )

    data class PlannedExercise(val exId: String, val name: String, val sets: Int, val reps: Int, val load: Double)
    data class Session(val name: String, val exercises: List<PlannedExercise>)

    /** Turn one split session into concrete lifts — port of `buildSession()`. `startingLoadOverride`
     *  lets a caller substitute the real persisted last-used load (Room, not modeled here) in
     *  place of the formula guess; omitted, it falls back to `startingLoad()` exactly like legacy
     *  falls back to `store.getLoad(exId, startingLoad(exId))`. */
    fun buildSession(session: SplitSession, profile: TrainingProfile, rotation: Int, loadOverride: (String) -> Double? = { null }): Session {
        val pool = available(profile)
        val chosen = mutableListOf<String>()
        session.groups.forEachIndexed { slot, group ->
            val candidates = pool.filter { EXERCISES[it]?.group == group && it !in chosen }
                .sortedByDescending { EXERCISES[it]?.compound == true }
            if (candidates.isEmpty()) return@forEachIndexed
            chosen.add(candidates[(rotation + slot) % candidates.size])
        }
        return Session(
            name = session.name,
            exercises = chosen.map { exId ->
                val s = scheme(exId, profile)
                PlannedExercise(exId, EXERCISES.getValue(exId).name, s.sets, s.reps, loadOverride(exId) ?: startingLoad(exId, profile))
            },
        )
    }

    /** The whole week, indexed like `Calendar.DAY_OF_WEEK` (1=Sunday..7=Saturday) to match
     *  legacy's `getDay()` convention (0=Sunday) shifted by one for Kotlin's 1-based enum. */
    fun weekPlan(profile: TrainingProfile, loadOverride: (String) -> Double? = { null }): List<Session?> {
        val split = SPLITS[profile.daysPerWeek] ?: SPLITS.getValue(3)
        val days = TRAINING_DAYS[profile.daysPerWeek] ?: TRAINING_DAYS.getValue(3)
        val week = MutableList<Session?>(7) { null }
        days.forEachIndexed { i, weekday -> week[weekday] = buildSession(split[i % split.size], profile, i, loadOverride) }
        return week
    }

    /** `dayOfWeek0Sunday` matches JS's `Date.getDay()` (0=Sunday..6=Saturday). */
    fun today(dayOfWeek0Sunday: Int, profile: TrainingProfile, loadOverride: (String) -> Double? = { null }): Session? =
        weekPlan(profile, loadOverride)[dayOfWeek0Sunday]
}
