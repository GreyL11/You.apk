package com.example.domain

import com.example.data.DayRow
import com.example.data.LogEntry
import java.time.LocalDate

/**
 * The closed loop's decision step: given everything already observed and derived, what should this
 * person actually do today?
 *
 * Structured, deterministic and explainable on purpose. Every decision carries the real factors it
 * was made from, so the UI can answer "why this?" from the same values the branch used rather than
 * from a model's after-the-fact narration. A language model may reword any of this; it never
 * decides it.
 *
 * Nothing here invents data. Where evidence is missing the decision says so via
 * [ReadinessEngine.Confidence] and falls back to the conservative option, which for training is a
 * normal session and for cardio is the lowest-cost useful mode — never intervals, never nothing.
 */
object DailyDecisionEngine {
    enum class TrainingChoice { FULL_SESSION, REDUCED_SESSION, RECOVERY_SESSION, REST }

    data class TrainingDecision(
        val choice: TrainingChoice,
        /** Which movement patterns today's session should prioritize. Empty for REST. */
        val focusPatterns: List<MovementPattern>,
        /** Plain-language label: "Lower body", "Upper body", "Full body", "Recovery", "Rest". */
        val focusLabel: String,
        val reasons: List<String>,
        /** May this session's loads progress upward today? */
        val allowProgression: Boolean,
    )

    data class CardioDecision(
        val mode: Cardio.Mode,
        val minutes: Int?,
        val effort: String,
        val reasons: List<String>,
    )

    data class Decision(
        val readiness: ReadinessEngine.Reading,
        val training: TrainingDecision,
        val cardio: CardioDecision,
        val topPriority: String,
        val confidence: ReadinessEngine.Confidence,
    )

    data class Inputs(
        val today: LocalDate,
        val readiness: ReadinessEngine.Reading,
        val logs: List<LogEntry>,
        val dayRows: List<DayRow>,
        val bottleneck: GoalGapEngine.Dimension?,
        /** Has a session already been logged today? Changes the question from "what should I do" to
         *  "what's left to do". */
        val trainedToday: Boolean,
    )

    // ── training ─────────────────────────────────────────────────────────────────────────────

    /**
     * Which patterns to prioritize: the longest-neglected ones that weren't just trained. Falls
     * back to a full-body focus when there isn't enough history to call anything neglected, which
     * is the honest answer rather than an arbitrary "chest day".
     */
    private fun pickFocus(logs: List<LogEntry>, today: LocalDate): Pair<List<MovementPattern>, String> {
        if (!TrainingHistory.hasEnoughHistory(logs)) {
            return TrainingHistory.BALANCED_PATTERNS to "Full body"
        }
        val recency = TrainingHistory.patternRecency(logs, today)
        val recent = TrainingHistory.recentlyTrainedPatterns(logs, today).toSet()

        // Never-trained patterns rank above everything: a pattern with no history at all is the
        // biggest real gap there is, and sorting by "days since" can't see it.
        val neverTrained = TrainingHistory.BALANCED_PATTERNS.filter { it !in recency }
        val candidates = if (neverTrained.isNotEmpty()) neverTrained
        else TrainingHistory.BALANCED_PATTERNS
            .filter { it !in recent }
            .sortedByDescending { recency[it] ?: Int.MAX_VALUE }

        // Everything was trained in the last couple of days -- that IS the answer, and it means
        // today is not a day to hit any of it again hard.
        if (candidates.isEmpty()) return emptyList<MovementPattern>() to "Recovery"

        val lower = candidates.filter { it in TrainingHistory.LOWER_BODY }
        val upper = candidates.filter { it !in TrainingHistory.LOWER_BODY }
        // Whichever half the most-neglected pattern belongs to leads the session, then the rest of
        // that half joins it -- a real session, not one isolated movement.
        return if (candidates.first() in TrainingHistory.LOWER_BODY) {
            lower to "Lower body"
        } else {
            upper to "Upper body"
        }
    }

    fun decideTraining(i: Inputs): TrainingDecision {
        val level = i.readiness.level
        val streak = TrainingHistory.consecutiveTrainingDays(i.logs, i.today)
        val recentDifficulty = TrainingHistory.recentDifficulty(i.logs)
        val (focus, label) = pickFocus(i.logs, i.today)

        // Hard safety floor: very low readiness never yields a training prescription, regardless of
        // what's neglected. A neglected pattern is not a reason to train through real fatigue.
        if (level == ReadinessEngine.Level.VERY_LOW) {
            return TrainingDecision(
                TrainingChoice.REST, emptyList(), "Rest",
                listOf("Readiness is very low today") + i.readiness.factors,
                allowProgression = false,
            )
        }
        if (level == ReadinessEngine.Level.LOW) {
            return TrainingDecision(
                TrainingChoice.RECOVERY_SESSION, emptyList(), "Recovery",
                listOf("Readiness is low today") + i.readiness.factors,
                allowProgression = false,
            )
        }

        // Everything is freshly trained -- nothing to prioritize without doubling up.
        if (focus.isEmpty()) {
            return TrainingDecision(
                TrainingChoice.RECOVERY_SESSION, emptyList(), "Recovery",
                listOf("Every movement pattern has been trained in the last couple of days"),
                allowProgression = false,
            )
        }

        val reasons = mutableListOf<String>()
        val recency = TrainingHistory.patternRecency(i.logs, i.today)
        val neglected = TrainingHistory.neglectedPatterns(i.logs, i.today).filter { it in focus }
        if (neglected.isNotEmpty() && TrainingHistory.hasEnoughHistory(i.logs)) {
            reasons.add("${label.lowercase()} movements are the longest since you trained them")
        }
        focus.firstOrNull()?.let { p ->
            recency[p]?.let { reasons.add("last trained ${patternLabel(p)} $it days ago") }
        }
        if (!TrainingHistory.hasEnoughHistory(i.logs)) {
            reasons.add("not much training history yet, so this starts with balanced full-body work")
        }

        // A long unbroken streak earns a reduced session even at good readiness -- accumulated days
        // are real load whether or not any single signal has caught up to them yet.
        val reduced = streak >= ReadinessEngine.LONG_STREAK ||
            level == ReadinessEngine.Level.MODERATE
        if (streak >= ReadinessEngine.LONG_STREAK) reasons.add("$streak consecutive training days")
        if (level == ReadinessEngine.Level.MODERATE) reasons.add("readiness is moderate rather than high")

        // Progression needs BOTH real readiness and no recent too-hard feedback. Either one alone
        // is not enough to justify adding load.
        val allowProgression = ReadinessEngine.allowsHardWork(level) &&
            (recentDifficulty == null || recentDifficulty < TrainingHistory.TOO_HARD)
        if (!allowProgression && recentDifficulty != null && recentDifficulty >= TrainingHistory.TOO_HARD) {
            reasons.add("recent sessions felt hard, so loads hold rather than increase")
        }

        return TrainingDecision(
            if (reduced) TrainingChoice.REDUCED_SESSION else TrainingChoice.FULL_SESSION,
            focus, label, reasons, allowProgression,
        )
    }

    fun patternLabel(p: MovementPattern): String = when (p) {
        MovementPattern.HORIZONTAL_PUSH -> "horizontal pushing"
        MovementPattern.VERTICAL_PUSH -> "overhead pushing"
        MovementPattern.HORIZONTAL_PULL -> "rowing"
        MovementPattern.VERTICAL_PULL -> "vertical pulling"
        MovementPattern.SQUAT -> "squatting"
        MovementPattern.HINGE -> "hinging"
        MovementPattern.LUNGE -> "single-leg work"
        MovementPattern.ISOLATION -> "isolation work"
    }

    // ── cardio ───────────────────────────────────────────────────────────────────────────────

    /**
     * Generates real candidate modes and rejects the ones today's state can't justify, rather than
     * mapping readiness straight onto a mode. The rejection reasons are the explanation.
     */
    fun decideCardio(i: Inputs, training: TrainingDecision): CardioDecision {
        val level = i.readiness.level
        val base = Cardio.base(i.dayRows, i.today)
        val weekMinutes = Cardio.weeklyMinutes(i.dayRows, i.today)
        val daysSince = Cardio.daysSinceLast(i.dayRows, i.today)
        val soreness = i.dayRows.find { it.dayKey == i.today.toString() }?.soreness
        val reasons = mutableListOf<String>()

        // Hard constraints first -- these are floors, not preferences, so they're applied before
        // any "what would be most useful" reasoning.
        val ceiling: Cardio.Mode = when {
            level == ReadinessEngine.Level.VERY_LOW -> Cardio.Mode.NONE
            level == ReadinessEngine.Level.LOW -> Cardio.Mode.LIGHT_RECOVERY
            soreness != null && soreness >= ReadinessEngine.HIGH_SORENESS -> Cardio.Mode.EASY_WALK
            level == ReadinessEngine.Level.MODERATE -> Cardio.Mode.AEROBIC_BASE
            // Intervals are never the automatic answer for someone still building a base, however
            // good today feels -- aerobic development comes first and costs less to recover from.
            base == Cardio.Base.BEGINNER || base == Cardio.Base.INSUFFICIENT_DATA -> Cardio.Mode.AEROBIC_BASE
            base == Cardio.Base.DEVELOPING -> Cardio.Mode.MODERATE_CONDITIONING
            else -> Cardio.Mode.INTERVALS
        }

        if (level == ReadinessEngine.Level.VERY_LOW) {
            return CardioDecision(
                Cardio.Mode.NONE, null, "",
                listOf("Readiness is very low — today is for recovery, not added load"),
            )
        }
        if (level == ReadinessEngine.Level.LOW) {
            reasons.add("readiness is low today, so this stays gentle")
        }
        if (soreness != null && soreness >= ReadinessEngine.HIGH_SORENESS) {
            reasons.add("you reported significant soreness ($soreness/10), so nothing hard today")
        }
        if (base == Cardio.Base.BEGINNER) reasons.add("your aerobic base is still developing, so steady work comes before intervals")
        if (base == Cardio.Base.INSUFFICIENT_DATA) reasons.add("not enough logged cardio yet to push intensity, so this starts steady")

        // A hard leg session plus hard cardio on the same day is a real double cost. Cap rather
        // than cancel: easy movement after legs is fine, conditioning on top of it is not.
        val trainedLegsToday = training.focusPatterns.any { it in TrainingHistory.LOWER_BODY } &&
            training.choice != TrainingChoice.REST
        val legCapped = if (trainedLegsToday && Cardio.cost(ceiling) > Cardio.cost(Cardio.Mode.EASY_WALK)) {
            reasons.add("today's session already loads your legs, so cardio stays easy")
            Cardio.Mode.EASY_WALK
        } else ceiling

        // Now the useful-value question, under that ceiling: how much has actually been done?
        val wanted = when {
            daysSince == null -> Cardio.Mode.AEROBIC_BASE.also { reasons.add("no cardio logged yet — starting the aerobic base") }
            daysSince >= 7 -> Cardio.Mode.AEROBIC_BASE.also { reasons.add("it's been $daysSince days since your last cardio") }
            weekMinutes >= 180 -> Cardio.Mode.LIGHT_RECOVERY.also { reasons.add("you've already done $weekMinutes minutes this week") }
            else -> Cardio.Mode.AEROBIC_BASE
        }

        val mode = if (Cardio.cost(wanted) <= Cardio.cost(legCapped)) wanted else legCapped
        if (training.choice == TrainingChoice.RECOVERY_SESSION && Cardio.cost(mode) <= Cardio.cost(Cardio.Mode.EASY_WALK)) {
            reasons.add("easy movement supports recovery rather than adding to fatigue")
        }

        return CardioDecision(mode, Cardio.minutes(mode), Cardio.effort(mode), reasons)
    }

    // ── the whole decision ───────────────────────────────────────────────────────────────────

    fun decide(i: Inputs): Decision {
        val training = decideTraining(i)
        val cardio = decideCardio(i, training)
        return Decision(
            readiness = i.readiness,
            training = training,
            cardio = cardio,
            topPriority = topPriority(i, training),
            confidence = i.readiness.confidence,
        )
    }

    /** The single thing that matters most today. One, deliberately — a list of five priorities is
     *  not a priority. Recovery outranks everything, since every other habit depends on it. */
    private fun topPriority(i: Inputs, training: TrainingDecision): String = when {
        training.choice == TrainingChoice.REST -> "Rest and recovery"
        training.choice == TrainingChoice.RECOVERY_SESSION -> "Recovery"
        i.bottleneck == GoalGapEngine.Dimension.SLEEP_CONSISTENCY -> "Sleep"
        i.bottleneck == GoalGapEngine.Dimension.RECOVERY -> "Recovery"
        i.trainedToday -> "You've trained — protect recovery for tomorrow"
        i.bottleneck == GoalGapEngine.Dimension.NUTRITION_CONSISTENCY -> "Logging what you eat"
        i.bottleneck == GoalGapEngine.Dimension.HYDRATION -> "Hydration"
        else -> "${training.focusLabel} training"
    }
}
