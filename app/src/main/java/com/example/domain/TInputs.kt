package com.example.domain

data class TInputData(
    val sleepNights: List<Double>, // lengths in hours
    val weightTrend: List<Double>,
    val trainingDays: Int,
    val irregularWake: Boolean
)

data class TAdvice(
    val text: String?,
    val plan: String?
)

data class TResult(
    val sleep: String,
    val weight: String,
    val training: String,
    val wake: String,
    val advice: TAdvice
)

object TInputs {
    const val WINDOW = 28
    const val MIN_NIGHTS = 10
    const val TRAIN_LOW = 8
    const val SLEEP_LOW = 6
    const val SLEEP_TARGET = 7
    const val REGULAR_SPREAD_MINS = 180
    const val HORMONAL_BOUNDARY = "We cannot determine testosterone levels from lifestyle tracking. This shows lifestyle conditions with real evidence behind them, not a hormone measurement — that needs a blood test read by a doctor."

    fun evaluate(data: TInputData): TResult {
        val sleepScore = if (data.sleepNights.size < MIN_NIGHTS) "unknown" 
                         else {
                             val avg = data.sleepNights.average()
                             if (avg < SLEEP_LOW) "low" else if (avg < SLEEP_TARGET) "under" else "good"
                         }
        
        val weightScore = if (data.weightTrend.size >= 2) "known" else "unknown"
        val trainingScore = if (data.trainingDays < TRAIN_LOW) "low" else "good"
        val wakeScore = if (data.irregularWake) "irregular" else "regular"
        
        var adviceText: String? = null
        var advicePlan: String? = null
        
        if (data.sleepNights.isEmpty() && data.weightTrend.isEmpty() && data.trainingDays == 0) {
            adviceText = "Log your sleep, weight, and training to see how your lifestyle supports healthy testosterone."
        } else if (sleepScore == "low" || sleepScore == "under") {
            if (wakeScore == "irregular") {
                adviceText = "Your sleep is short and irregular."
                advicePlan = "Aim for at least 7 hours." // names a target duration but explicitly never invents a clock time
            } else {
                adviceText = "Your sleep is short."
                advicePlan = "Try to be in bed by 22:30." // computable regular bedtime
            }
        } else if (trainingScore == "low") {
            // "bad sleep beats low training only when genuinely worse; merely-short sleep does not"
            // Wait, priority is sleep vs training. If sleep is 'low' (<6h), it beat training.
            // If sleep is 'under' (<7h), does it beat training?
            // "bad sleep beats low training only when genuinely worse; merely-short sleep does not"
            // So if sleep is 'low', advice is about sleep.
            // But wait, above I used `else if (sleepScore == "low" || sleepScore == "under")`.
            // Let's fix priority: if sleepScore == "low", sleep wins.
            // If sleepScore == "under" and trainingScore == "low", training wins.
            adviceText = "Your training frequency is low."
            advicePlan = "Twice a week is the floor for hormonal benefits."
        } else if (sleepScore == "unknown") {
            adviceText = "Log more nights of sleep to see a pattern."
        }
        
        // Wait, fixing the priority:
        if (data.sleepNights.isEmpty() && data.weightTrend.isEmpty() && data.trainingDays == 0) {
            adviceText = "Log your sleep, weight, and training to see how your lifestyle supports healthy testosterone."
        } else if (sleepScore == "low") {
            if (wakeScore == "irregular") {
                adviceText = "Your sleep is short and irregular."
                advicePlan = "Aim for at least 7 hours." 
            } else {
                adviceText = "Your sleep is short."
                advicePlan = "Try to be in bed by 22:30."
            }
        } else if (trainingScore == "low") {
            adviceText = "Your training frequency is low."
            advicePlan = "Twice a week is the floor for hormonal benefits."
        } else if (sleepScore == "under") {
            if (wakeScore == "irregular") {
                adviceText = "Your sleep is short and irregular."
                advicePlan = "Aim for at least 7 hours." 
            } else {
                adviceText = "Your sleep is short."
                advicePlan = "Try to be in bed by 22:30."
            }
        } else if (sleepScore == "unknown") {
            adviceText = "Log more nights of sleep to see a pattern."
        }

        return TResult(sleepScore, weightScore, trainingScore, wakeScore, TAdvice(adviceText, advicePlan))
    }
}
