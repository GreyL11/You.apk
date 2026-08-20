package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.domain.Coach
import com.example.domain.FaultEvent
import com.example.domain.HealthCoachEngine
import com.example.domain.Planner
import com.example.domain.TrainingProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.domain.Nutrition
import java.time.LocalDateTime
import java.time.DayOfWeek

data class TodayDashboardState(
    val nextBestAction: HealthCoachEngine.Candidate? = null,
    val profile: Profile? = null,
    val todayMeals: List<Meal> = emptyList(),
    val todayMacros: Nutrition.Macros = Nutrition.Macros(0, 0, 0, 0),
    /** Cutting / holding / gaining — a nutrition phase, not the training scheme. */
    val phase: Nutrition.Phase = Nutrition.Phase.MAINTAIN,
    val targets: Nutrition.Targets? = null,
    val weightTrend: Nutrition.WeightTrend? = null,
    /** What the scale says the target should be. Null most of the time, deliberately. */
    val kcalSuggestion: Nutrition.Suggestion? = null,
    /** The one line about the last four weeks. */
    val nutritionCoachLine: String? = null,
    val sugarStatus: Nutrition.SugarStatus = Nutrition.SugarStatus(0, 0),
    val sugarTargetGrams: Int? = null,
    val todayRow: DayRow? = null,
    val todayTraining: Planner.Session? = null,
    val todayLogEntries: List<LogEntry> = emptyList(),
    val trainingProfile: TrainingProfile = TrainingProfile(),
    val waterTarget: Int = 0,
    val waterIntake: Int = 0,
    /** "35% below your normal" — null unless PersonalBaseline has enough evidence to say it. */
    val hydrationVsBaseline: String? = null,
    val hasCompletedTraining: Boolean = false
)

class TodayViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    
    private val settingsManager = SettingsManager(application)
    private val gemini = com.example.domain.GeminiClient(settingsManager)
    private val health = com.example.domain.HealthConnectSync(application)
    
    private val _dashboardState = MutableStateFlow(TodayDashboardState())
    val dashboardState: StateFlow<TodayDashboardState> = _dashboardState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // Retention caps that were defined on these DAOs but never actually invoked anywhere —
            // log_entry/verdict/action_outcome/chat_message grew unbounded. Enforced here, once per
            // refresh (already called after every mutation in this class), rather than duplicated
            // across every individual insert call site.
            db.logEntryDao().enforceCap()
            db.verdictDao().enforceCap()
            db.actionOutcomeDao().enforceCap()

            val now = LocalDateTime.now()
            val dayKey = now.toLocalDate().toString()

            // Query DB for context
            val todayRow = db.dayRowDao().getSync(dayKey)
            val recentMeals = db.mealDao().getByDateSync(dayKey) // simplistic hydration mock
            val allLogs = db.logEntryDao().getAllSync()
            val recentLogs = allLogs.filter { it.at.startsWith(dayKey) }
            val recentOutcomes = db.actionOutcomeDao().getAllSync()
            
            // For first-run experience: detect if we have *any* historical evidence
            val allMealsCount = db.mealDao().getAllSync().size
            val allLogsCount = allLogs.size
            val allRowsCount = db.dayRowDao().getAllSync().size
            val allOutcomesCount = recentOutcomes.size
            val totalHistorical = allMealsCount + allLogsCount + allRowsCount + allOutcomesCount
            val profile = db.profileDao().getProfileSync()

            val primaryGoal = settingsManager.getSetting("primary_goal", "hydration").first()

            // ── the fat-loss loop ────────────────────────────────────────────────────────────
            // Every day that had ANY food logged, as one kcal figure each. Days with nothing logged
            // are ABSENT from this list rather than present as a zero — a day you did not log is not
            // a day you did not eat, and averaging zeros in would invent a deficit that never
            // happened and then tell you to eat less on the strength of it.
            val phase = Nutrition.phaseOf(settingsManager.getSetting("nutrition_phase", "maintain").first())
            val allMeals = db.mealDao().getAllSync()
            val windowStart = now.toLocalDate().minusDays((Nutrition.WINDOW_DAYS - 1).toLong()).toString()
            val loggedKcal = allMeals
                .filter { it.at >= windowStart }
                .groupBy { it.at.take(10) }
                .map { (_, dayMeals) -> Nutrition.macros(dayMeals).kcal }
                .filter { it > 0 }
            val allWeights = db.weightDao().getAllSync()
            val trend = Nutrition.weightTrend(allWeights, now.toLocalDate())
            val targets = Nutrition.targets(profile, phase)
            val kcalSuggestion = Nutrition.suggestion(
                profile, phase, loggedKcal,
                allWeights.filter { it.at >= windowStart }.map { it.at to it.kg },
            )
            val nutritionCoachLine = Nutrition.coachLine(profile, phase, loggedKcal, trend)
            val ctx = HealthCoachEngine.Context(
                now = now,
                todayRow = todayRow,
                recentMeals = recentMeals,
                recentLogEntries = recentLogs,
                recentOutcomes = recentOutcomes,
                totalHistoricalLogs = totalHistorical,
                primaryGoal = primaryGoal ?: "hydration",
                profile = profile
            )
            
            val nba = HealthCoachEngine.selectNextBestAction(ctx)
            
            // The full conversion, not just bodyweight/days/goal -- dropping bar/plates/equipment/
            // injuries here silently meant every plate breakdown and every injury filter ran
            // against TrainingProfile()'s defaults instead of what this person actually has.
            val tp = profile?.toTrainingProfile() ?: TrainingProfile()
            val jsDay = if (now.dayOfWeek == java.time.DayOfWeek.SUNDAY) 0 else now.dayOfWeek.value
            val todayTraining = com.example.domain.Planner.today(jsDay, tp) { exId ->
                val history = allLogs.filter { it.exId == exId }
                if (history.isNotEmpty()) history.last().load else null
            }
            
            val foodEntries = recentMeals.map { m -> m to Nutrition.FOODS.find { it.id == m.foodId }?.ml }
            val waterIntake = Nutrition.fluid(foodEntries)

            // The user's own normal hydration, from every OTHER day logged — never compares today
            // against itself, and says nothing at all without enough real history behind it.
            val hydrationBaseline = com.example.domain.PersonalBaseline.dailyFluidMl(allMeals.filter { it.at.take(10) != dayKey })
            val hydrationVsBaseline = com.example.domain.PersonalBaseline.compare(waterIntake.toDouble(), hydrationBaseline)

            _dashboardState.value = TodayDashboardState(
                nextBestAction = nba,
                profile = profile,
                todayMeals = recentMeals,
                todayMacros = Nutrition.macros(recentMeals),
                phase = phase,
                targets = targets,
                weightTrend = trend,
                kcalSuggestion = kcalSuggestion,
                nutritionCoachLine = nutritionCoachLine,
                sugarStatus = Nutrition.sugarStatus(recentMeals),
                sugarTargetGrams = Nutrition.sugarTargetGrams(profile, phase),
                todayRow = todayRow,
                todayTraining = todayTraining,
                todayLogEntries = recentLogs,
                trainingProfile = tp,
                waterTarget = Nutrition.waterTarget(profile),
                waterIntake = waterIntake,
                hydrationVsBaseline = hydrationVsBaseline,
                hasCompletedTraining = recentOutcomes.any { it.actionId == "train_today" && it.event == HealthCoachEngine.ActionState.COMPLETED.name && it.at.startsWith(dayKey) }
            )
        }
    }

    fun skipAction(actionId: String, domain: String) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            db.actionOutcomeDao().insert(
                ActionOutcome(
                    at = now.toString(),
                    actionId = actionId,
                    domain = domain,
                    event = HealthCoachEngine.ActionState.SKIPPED.name
                )
            )
            refresh()
        }
    }

    /** "Later" on a Health Coach card: hides that specific action for POSTPONE_MINUTES, then it
     *  resurfaces on its own — unlike skipAction, which hides it for the rest of the day. */
    fun postponeAction(actionId: String, domain: String) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            db.actionOutcomeDao().insert(
                ActionOutcome(
                    at = now.toString(),
                    actionId = actionId,
                    domain = domain,
                    event = HealthCoachEngine.ActionState.POSTPONED.name
                )
            )
            refresh()
        }
    }

    fun logHydration(amount: Double) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            db.mealDao().insert(
                Meal(
                    at = now.toString(),
                    foodId = "water",
                    qty = amount
                )
            )
            db.actionOutcomeDao().insert(
                ActionOutcome(
                    at = now.toString(),
                    actionId = "hydrate_now",
                    domain = "hydration",
                    event = HealthCoachEngine.ActionState.COMPLETED.name
                )
            )
            refresh()
        }
    }

    /** Hands a sentence to the model and gets back rows to confirm. Nothing is written here. */
    suspend fun parseMeal(text: String): com.example.domain.MealParseResult? = gemini.parseMeal(text)

    /**
     * Write the rows the person actually confirmed.
     *
     * One outcome row for the batch, not one per food: they answered the "what did you have" question
     * once, and three COMPLETED events for one answer would distort every cooldown that reads them.
     */
    fun logFoods(items: List<com.example.domain.ParsedMeal>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val now = LocalDateTime.now().toString()
            items.forEach { db.mealDao().insert(Meal(at = now, foodId = it.foodId, qty = it.qty)) }
            db.mealDao().enforceCap()
            db.actionOutcomeDao().insert(
                ActionOutcome(
                    at = now,
                    actionId = "meal_log",
                    domain = "nutrition",
                    event = HealthCoachEngine.ActionState.COMPLETED.name
                )
            )
            refresh()
        }
    }

    /**
     * Pull anything new out of Health Connect, then re-read.
     *
     * Called on open rather than on a timer: the import is cheap, idempotent (hand-logged nights are
     * never overwritten — see HealthImport), and the coach is about to reason over whatever it finds,
     * so stale sleep here is a wrong recommendation.
     */
    fun syncHealthConnect() {
        viewModelScope.launch {
            if (!health.hasAnyPermission()) return@launch
            health.importInto(db)
            refresh()
        }
    }

    /**
     * Log one real food entry — the Android equivalent of legacy's `logFood()` (www/app.js),
     * which is itself just `store.appendMeal({at, foodId, qty})`. `foodId` must be a real id from
     * `Nutrition.FOODS`; `qty` is servings (1 = one serving, 0.5/2 = half/double), not calories or
     * macros — those are derived later by whatever reads the log, never invented here.
     */
    fun logFood(foodId: String, qty: Double) {
        viewModelScope.launch {
            db.mealDao().insert(
                Meal(
                    at = LocalDateTime.now().toString(),
                    foodId = foodId,
                    qty = qty
                )
            )
            // Answering "what did you have today?" is recorded like every other answer, so the
            // notification side knows it was answered rather than inferring it from the meal row.
            db.actionOutcomeDao().insert(
                ActionOutcome(
                    at = LocalDateTime.now().toString(),
                    actionId = "meal_log",
                    domain = "nutrition",
                    event = HealthCoachEngine.ActionState.COMPLETED.name
                )
            )
            refresh()
        }
    }

    fun logSkinRoutine(data: String) {
        viewModelScope.launch {
            val dayKey = LocalDateTime.now().toLocalDate().toString()
            val existing = db.dayRowDao().getSync(dayKey)
            
            db.dayRowDao().insert(
                (existing ?: DayRow(dayKey, null, null, null, null, null, null)).copy(
                    skin = data
                )
            )
            db.actionOutcomeDao().insert(
                ActionOutcome(
                    at = LocalDateTime.now().toString(),
                    actionId = "skin_log",
                    domain = "skinRoutine",
                    event = HealthCoachEngine.ActionState.COMPLETED.name
                )
            )
            refresh()
        }
    }

    /**
     * @param faultEvents real per-rep fault events from a live camera session (MovementEngine),
     *  if this session had one. Manual entry (no camera) supplies none — `faultCount` alone then
     *  describes a session with no per-rep detail, which is honest (there genuinely is none),
     *  never a fabricated breakdown.
     * @param profile drives plate-snapped progression (Coach/Planner); defaults to legacy's own
     *  DEFAULT_PROFILE-equivalent fallback since no screen currently writes the Profile table.
     */
    fun logTraining(
        exId: String,
        reps: Int,
        load: Double,
        faultCount: Int = 0,
        faultEvents: List<FaultEvent> = emptyList(),
        profile: TrainingProfile = TrainingProfile(),
    ) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val history = db.logEntryDao().getHistorySync(exId)
            val effectiveFaultCount = if (faultEvents.isNotEmpty()) faultEvents.size else faultCount

            // What today's plan asked of this lift. Without it, Coach cannot tell "missed the reps"
            // from "form broke down", and a clean 3-of-15 would earn a weight increase. Null when the
            // lift is not in today's plan — an unplanned lift has no target to have missed.
            val jsDayToday = if (now.dayOfWeek == DayOfWeek.SUNDAY) 0 else now.dayOfWeek.value
            val targetReps = Planner.today(jsDayToday, profile)
                ?.exercises?.firstOrNull { it.exId == exId }?.reps

            val progression = Coach.evaluateSession(
                history = history,
                currentReps = reps,
                currentLoad = load,
                currentFaultCount = effectiveFaultCount,
                exId = exId,
                profile = profile,
                targetReps = targetReps,
            )

            db.logEntryDao().insert(
                LogEntry(
                    at = now.toString(),
                    exId = exId,
                    reps = reps,
                    sets = 1,
                    load = load,
                    // Real {rep,id} pairs when a live session supplied them, matching legacy's
                    // faultEvents shape — never a placeholder "[{}]" standing in for a count.
                    faultEvents = faultEvents.joinToString(",", "[", "]") { "{\"rep\":${it.rep},\"id\":\"${it.faultId}\"}" },
                    correctedFrom = null,
                ),
            )

            // The progression verdict was previously computed and discarded — it now actually
            // reaches persistence, matching legacy's store.appendVerdict(), so "why did it hold
            // me at this weight" has a real, later-readable answer instead of nothing.
            db.verdictDao().insert(
                Verdict(
                    at = now.toString(),
                    exId = exId,
                    decision = when (progression.progression) {
                        Coach.Progression.INCREASE -> "progress"
                        Coach.Progression.HOLD -> "hold"
                        Coach.Progression.DELOAD -> "deload"
                    },
                    unit = if (progression.unit == Coach.Unit.REPS) "reps" else "kg",
                    from = if (progression.unit == Coach.Unit.REPS) reps.toDouble() else load,
                    to = progression.nextLoad,
                    reason = progression.reason,
                    evidence = "{\"faultCount\":$effectiveFaultCount,\"estimated1RM\":${progression.estimated1RM}}",
                ),
            )

            db.actionOutcomeDao().insert(
                ActionOutcome(
                    at = now.toString(),
                    actionId = "train_today",
                    domain = "training",
                    event = HealthCoachEngine.ActionState.COMPLETED.name,
                ),
            )
            refresh()
        }
    }

    /** Real suggested load for the next session of this exercise: the last logged load if one
     *  exists, else Planner's bodyweight-scaled starting guess — never a hardcoded literal.
     *  Bodyweight exercises (loadRatio 0) correctly return 0.0, which is a true fact about the
     *  exercise, not a placeholder standing in for an unfinished load-input feature. */
    suspend fun suggestedLoad(exId: String, profile: TrainingProfile = TrainingProfile()): Double {
        val last = db.logEntryDao().getHistorySync(exId).lastOrNull()
        return last?.load ?: Planner.startingLoad(exId, profile)
    }

    /**
     * A weigh-in. One per day, keyed by day, so weighing twice replaces rather than double-counts.
     *
     * Until this existed the ONLY way weight entered the app was a Health Connect import, which made
     * the whole scale-corrected calorie loop ([Nutrition.suggestion]) unreachable for anyone without
     * a connected smart scale — the app could compute the correction and had no way to be given the
     * measurement it corrects from.
     */
    /**
     * The efficiency faults this lifter has been shown to simply do on this lift, so the live screen
     * can stop cueing them. Safety faults are never included — see [com.example.domain.FormBaseline].
     */
    suspend fun habitualFaultIds(exId: String): Set<String> {
        val history = db.logEntryDao().getHistorySync(exId)
        return com.example.domain.FormBaseline.habitualFaults(history, exId).map { it.faultId }.toSet()
    }

    /** Cutting, holding or gaining. Changes the target and what the scale is checked against. */
    fun setPhase(phase: Nutrition.Phase) {
        viewModelScope.launch {
            settingsManager.setSetting("nutrition_phase", phase.name.lowercase())
            refresh()
        }
    }

    /**
     * Take the scale's correction as the new target.
     *
     * Only ever called from a button the person pressed — [Nutrition.suggestion] computes it, and
     * nothing applies it automatically. A month of scale data should nudge a number the person
     * chose, not silently move the goalposts under them.
     */
    fun acceptKcalSuggestion(kcal: Int) {
        viewModelScope.launch {
            val existing = db.profileDao().getProfileSync() ?: return@launch
            db.profileDao().insert(existing.copy(kcalTarget = kcal))
            refresh()
        }
    }

    fun logWeight(kg: Double) {
        viewModelScope.launch {
            val dayKey = LocalDateTime.now().toLocalDate().toString()
            db.weightDao().insert(Weight(at = dayKey, kg = kg))
            db.weightDao().enforceCap()
            refresh()
        }
    }

    /**
     * One sleep, as the two times it actually ran between.
     *
     * Stored as a block with real timestamps rather than a length, because a length is the only thing
     * a length can tell you: `TInputs.wakePattern` needs the END of the sleep to know whether there
     * is such a thing as your usual wake time, and therefore whether the app is allowed to name a
     * bedtime at all. The legacy `bed`/`wake` pair is written alongside for the screens that still
     * read those; where both exist, the block wins.
     *
     * Filed under the day it ENDED — you log a sleep after waking from it.
     */
    fun logSleep(bed: String, wake: String) {
        viewModelScope.launch {
            val today = LocalDateTime.now().toLocalDate()
            val bedTime = java.time.LocalTime.parse(bed)
            val wakeTime = java.time.LocalTime.parse(wake)
            val end = LocalDateTime.of(today, wakeTime)
            val start = LocalDateTime.of(
                if (bedTime.isAfter(wakeTime)) today.minusDays(1) else today,
                bedTime,
            )
            val dayKey = today.toString()
            val existing = db.dayRowDao().getSync(dayKey)
            val sleepJson = """[{"start":"$start","end":"$end"}]"""

            db.dayRowDao().insert(
                (existing ?: DayRow(dayKey, null, null, null, null, null, null)).copy(
                    sleeps = sleepJson,
                    bed = bed,
                    wake = wake,
                )
            )
            db.actionOutcomeDao().insert(
                ActionOutcome(
                    at = LocalDateTime.now().toString(),
                    actionId = "hormone_sleep",
                    domain = "hormonalLifestyle",
                    event = HealthCoachEngine.ActionState.COMPLETED.name
                )
            )
            refresh()
        }
    }
}
