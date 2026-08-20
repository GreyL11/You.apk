package com.example.domain

/**
 * "What should I do right now?" — the same [PersonalState] the daily mission is built from, narrowed
 * to the one thing worth doing at this hour.
 *
 * Answers stay short and concrete because a vague answer to this question is useless. Every branch
 * reads real state: what's already been logged today, what the decision engine chose, what time it
 * is. Nothing here re-decides anything — it selects which part of the existing decision is
 * actionable now.
 */
object NextActionEngine {
    data class Answer(val action: String, val detail: String?)

    /** Rough day segments. Deliberately coarse: the app knows the hour, not the person's schedule,
     *  so pretending to know "your gym window" would be invention. */
    private fun isMorning(hour: Int) = hour in 5..11
    private fun isEvening(hour: Int) = hour >= 19 || hour < 5

    fun answer(state: PersonalState, hour: Int, moodLoggedToday: Boolean, ateToday: Boolean): Answer {
        val d = state.decision

        // Rest days have exactly one honest answer, and it isn't a list of optimizations.
        if (d.training.choice == DailyDecisionEngine.TrainingChoice.REST) {
            return Answer(
                "Take today off",
                d.training.reasons.firstOrNull()?.let { "Today is a rest day: $it." }
                    ?: "Today is a rest day.",
            )
        }

        if (isMorning(hour)) {
            if (!moodLoggedToday) {
                return Answer(
                    "Complete today's check-in",
                    "A minute of energy, soreness and sleep makes every recommendation today actually yours.",
                )
            }
            if (!ateToday) {
                return Answer("Eat a protein-containing meal", "Then log it, so intake reads from real food rather than a guess.")
            }
        }

        if (!state.trainedToday && d.training.choice != DailyDecisionEngine.TrainingChoice.RECOVERY_SESSION) {
            return Answer(
                "Train: ${d.training.focusLabel}",
                d.training.reasons.firstOrNull()?.replaceFirstChar { it.uppercase() },
            )
        }

        if (d.training.choice == DailyDecisionEngine.TrainingChoice.RECOVERY_SESSION && !state.trainedToday) {
            return Answer("Keep today easy", d.training.reasons.firstOrNull()?.replaceFirstChar { it.uppercase() })
        }

        // Trained already -- cardio is the remaining prescription, if today has one worth doing.
        if (d.cardio.mode != Cardio.Mode.NONE) {
            val mins = d.cardio.minutes
            return Answer(
                "${Cardio.label(d.cardio.mode)}${if (mins != null) " — $mins min" else ""}",
                d.cardio.effort.ifBlank(null),
            )
        }

        if (isEvening(hour)) {
            return Answer("Protect tonight's sleep", "It's the single biggest input to tomorrow's readiness.")
        }

        return Answer("Nothing hard left today", "Today's training is done. Keep the rest of the day easy.")
    }

    private fun String.ifBlank(fallback: String?): String? = if (this.isBlank()) fallback else this
}
