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
    val todayRow: DayRow? = null,
    val todayTraining: Planner.Session? = null,
    val todayLogEntries: List<LogEntry> = emptyList(),
    val waterTarget: Int = 0,
    val waterIntake: Int = 0,
    val hasCompletedTraining: Boolean = false
)

class TodayViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    
    private val settingsManager = SettingsManager(application)
    
    private val _dashboardState = MutableStateFlow(TodayDashboardState())
    val dashboardState: StateFlow<TodayDashboardState> = _dashboardState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
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
            
            val tp = profile?.let { TrainingProfile(bodyweight = it.bodyweight, daysPerWeek = it.daysPerWeek, goal = it.goal) } ?: TrainingProfile()
            val jsDay = if (now.dayOfWeek == java.time.DayOfWeek.SUNDAY) 0 else now.dayOfWeek.value
            val todayTraining = com.example.domain.Planner.today(jsDay, tp) { exId ->
                val history = allLogs.filter { it.exId == exId }
                if (history.isNotEmpty()) history.last().load else null
            }
            
            val foodEntries = recentMeals.map { m -> m to Nutrition.FOODS.find { it.id == m.foodId }?.ml }
            
            _dashboardState.value = TodayDashboardState(
                nextBestAction = nba,
                profile = profile,
                todayMeals = recentMeals,
                todayRow = todayRow,
                todayTraining = todayTraining,
                todayLogEntries = recentLogs,
                waterTarget = Nutrition.waterTarget(profile),
                waterIntake = Nutrition.fluid(foodEntries),
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

    /**
     * Log one real food entry — the Android equivalent of legacy's `logFood()` (www/app.js),
     * which is itself just `store.appendMeal({at, foodId, qty})`. `foodId` must be a real id from
     * `Nutrition.FOODS`; `qty` is servings (1 = one serving, 0.5/2 = half/double), not calories or
     * macros — those are derived later by whatever reads the log, never invented here.
     *
     * No ActionOutcome is recorded: general food logging isn't tied to any HealthCoachEngine
     * candidate domain (only "hydration" is, via logHydration), so inventing one here would be a
     * fabricated coach-outcome event for an action that was never offered.
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

            val progression = Coach.evaluateSession(
                history = history,
                currentReps = reps,
                currentLoad = load,
                currentFaultCount = effectiveFaultCount,
                exId = exId,
                profile = profile,
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

    fun logSleep(hours: Double) {
        viewModelScope.launch {
            val dayKey = LocalDateTime.now().toLocalDate().toString()
            val existing = db.dayRowDao().getSync(dayKey)
            val sleepJson = """[{"duration":$hours}]"""
            
            db.dayRowDao().insert(
                (existing ?: DayRow(dayKey, null, null, null, null, null, null)).copy(
                    sleeps = sleepJson
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
