package com.example.domain

import com.example.data.*
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object HealthCoachEngine {
    enum class Tier(val value: Int) {
        ACTIONABLE_NOW(1),
        DATA_COLLECTION(2),
        GOING_WELL(3)
    }

    val DOMAIN_ORDER = listOf("training", "nutrition", "hormonalLifestyle", "hydration", "skinRoutine")

    enum class ActionState {
        OFFERED, STARTED, COMPLETED, SKIPPED, POSTPONED, CANCELLED
    }

    // Same window NotificationDecisionEngine's own cooldown already uses for "offered recently".
    private const val POSTPONE_MINUTES = 120L

    data class Candidate(
        val domain: String,
        val actionId: String,
        val title: String,
        val reason: String,
        val tier: Tier
    )

    data class Context(
        val now: LocalDateTime,
        val todayRow: DayRow?,
        val recentMeals: List<Meal>,
        val recentLogEntries: List<LogEntry>,
        val recentOutcomes: List<ActionOutcome>,
        val totalHistoricalLogs: Int = 0,
        val primaryGoal: String = "hydration",
        val profile: Profile? = null
    )

    /**
     * Is this exact action currently suppressed by the user's own last choice on it?
     * Completed/skipped hide it for the rest of the calendar day; postponed hides it only for
     * POSTPONE_MINUTES; cancelled (or no prior outcome) never suppresses.
     */
    fun isSuppressed(actionId: String, ctx: Context): Boolean {
        val last = ctx.recentOutcomes.filter { it.actionId == actionId }.maxByOrNull { it.at } ?: return false
        val lastAt = runCatching { LocalDateTime.parse(last.at) }.getOrNull() ?: return false
        return when (last.event) {
            ActionState.COMPLETED.name, ActionState.SKIPPED.name -> lastAt.toLocalDate() == ctx.now.toLocalDate()
            ActionState.POSTPONED.name -> ChronoUnit.MINUTES.between(lastAt, ctx.now) < POSTPONE_MINUTES
            else -> false // CANCELLED, STARTED, OFFERED, or unrecognized -> never suppresses
        }
    }

    fun candidates(ctx: Context): List<Candidate> {
        val list = mutableListOf<Candidate>()
        
        // NO_EVIDENCE / NEW_USER State
        val hasNoEvidence = ctx.totalHistoricalLogs == 0
        if (hasNoEvidence) {
            val title = when (ctx.primaryGoal) {
                "training" -> "Start your training baseline"
                "hydration" -> "Log your first hydration"
                "skinRoutine" -> "Start your skincare log"
                "hormonalLifestyle" -> "Start tracking your recovery"
                else -> "Let's build your baseline."
            }
            
            list.add(Candidate(
                domain = ctx.primaryGoal, // Use their chosen goal as the domain
                actionId = "build_baseline",
                title = title,
                reason = "Your coach doesn't know enough about your routine yet. Start with what you actually do today.",
                tier = Tier.DATA_COLLECTION
            ))
            // Return only the baseline builder so we don't spam fake actionable recs
            return list
        }
        
        // Hydration Logic: real fluid ml logged today vs Nutrition.waterTarget(), not "any food logged"
        val waterEntries = ctx.recentMeals.map { meal -> meal to Nutrition.FOODS.find { it.id == meal.foodId }?.ml }
        val haveMl = Nutrition.fluid(waterEntries)
        val targetMl = Nutrition.waterTarget(ctx.profile)
        if (targetMl == 0 || ctx.recentMeals.isEmpty()) {
            list.add(Candidate("hydration", "hydrate_now", "Log Hydration", "We don't have enough intake data yet. Log water to personalize hydration guidance.", Tier.DATA_COLLECTION))
        } else if (haveMl < targetMl) {
            list.add(Candidate("hydration", "hydrate_now", "Drink water now", "You've had ${haveMl}ml of your ${targetMl}ml target today.", Tier.ACTIONABLE_NOW))
        } else {
            list.add(Candidate("hydration", "hydrate_maintain", "Hydration target met", "Your hydration is on track.", Tier.GOING_WELL))
        }

        // Eating. Asked as a question, because that is what it is: the app does not know and cannot
        // guess. Fluids are excluded deliberately — a food with `ml` is drink, already counted by the
        // hydration candidate above, and a day of nothing but water is not a day of eating. (Milk has
        // both ml and macros; it counts as drink here, which is the conservative reading: it will ask
        // about food you may have had rather than assume a meal you did not log.)
        val ateToday = ctx.recentMeals.any { meal ->
            Nutrition.FOODS.find { it.id == meal.foodId }?.ml == null
        }
        if (!ateToday) {
            list.add(
                Candidate(
                    "nutrition", "meal_log", "What did you have today?",
                    "No food is logged today. Tell the app what you ate and it can start reading your intake instead of guessing at it.",
                    Tier.DATA_COLLECTION,
                ),
            )
        } else {
            list.add(
                Candidate(
                    "nutrition", "meal_logged", "Meals tracked",
                    "You logged what you ate today.",
                    Tier.GOING_WELL,
                ),
            )
        }

        // Skin Routine Logic
        if (ctx.todayRow?.skin == null) {
            list.add(Candidate("skinRoutine", "skin_log", "Log Skincare Routine", "We need consistency to see what works for your face.", Tier.DATA_COLLECTION))
        } else {
            list.add(Candidate("skinRoutine", "skin_good", "Skin Tracked", "You logged your skin routine today.", Tier.GOING_WELL))
        }

        // Training Logic
        val trainingDone = ctx.recentLogEntries.any { it.at.startsWith(ctx.now.toLocalDate().toString()) }
        if (!trainingDone) {
            list.add(Candidate("training", "train_today", "Hit the gym", "It's a good day to get a session in.", Tier.ACTIONABLE_NOW))
        } else {
            list.add(Candidate("training", "train_rest", "Rest and Recover", "You trained today. Great job.", Tier.GOING_WELL))
        }
        
        // Hormonal Lifestyle
        if (ctx.todayRow?.sleeps == null) {
            list.add(Candidate("hormonalLifestyle", "hormone_sleep", "Track Sleep", "Sleep is a primary driver of natural testosterone.", Tier.DATA_COLLECTION))
        } else {
            list.add(Candidate("hormonalLifestyle", "hormone_good", "Hormonal Lifestyle On Track", "Your sleep data is providing solid evidence.", Tier.GOING_WELL))
        }

        return list
    }

    fun selectNextBestAction(ctx: Context): Candidate? {
        val all = candidates(ctx).filter { !isSuppressed(it.actionId, ctx) }
        return all.minWithOrNull(
            compareBy<Candidate> { it.tier.value }
                .thenBy { DOMAIN_ORDER.indexOf(it.domain).let { idx -> if (idx == -1) 999 else idx } }
        )
    }
}

