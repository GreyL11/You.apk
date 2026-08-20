package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.domain.Coach
import com.example.domain.EXERCISES
import com.example.domain.HealthStateEngine
import com.example.domain.TrainingCoverageEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class TimeRange(val label: String, val days: Int)

data class ChartPoint(val date: LocalDate, val value: Double)

data class StrengthProgress(
    val exId: String,
    val name: String,
    val isBodyweight: Boolean,
    val history: List<ChartPoint>,
    val currentMax: Double,
    val previousMax: Double?
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val weightHistory: List<ChartPoint> = emptyList(),
    val filteredWeightHistory: List<ChartPoint> = emptyList(),
    val timeRanges: List<TimeRange> = listOf(
        TimeRange("28D", 28),
        TimeRange("21D", 21),
        TimeRange("14D", 14),
        TimeRange("7D", 7),
        TimeRange("TODAY", 1)
    ),
    val selectedTimeRange: TimeRange = TimeRange("28D", 28),
    val strengthProgress: List<StrengthProgress> = emptyList(),
    val filteredStrengthProgress: List<StrengthProgress> = emptyList(),
    val healthSnapshot: HealthStateEngine.Snapshot? = null,
    val pushPullReading: TrainingCoverageEngine.PushPullReading? = null,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        refresh()
    }

    fun setTimeRange(range: TimeRange) {
        val state = _uiState.value
        val cutoff = LocalDate.now().minusDays(range.days.toLong() - 1)
        
        _uiState.value = state.copy(
            selectedTimeRange = range,
            filteredWeightHistory = state.weightHistory.filter { !it.date.isBefore(cutoff) },
            filteredStrengthProgress = state.strengthProgress.map { prog ->
                prog.copy(
                    history = prog.history.filter { !it.date.isBefore(cutoff) }
                )
            }.filter { it.history.isNotEmpty() }
        )
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val weights = db.weightDao().getAllSync()
            val logs = db.logEntryDao().getAllSync()

            // Real per-domain state, and the real push/pull training-volume read — see
            // HealthStateEngine/TrainingCoverageEngine's own doc comments for why each state is
            // derived, never a fabricated score.
            val now = LocalDateTime.now()
            val dayKey = now.toLocalDate().toString()
            val allDayRows = db.dayRowDao().getAllSync()
            val allMeals = db.mealDao().getAllSync()
            val healthSnapshot = HealthStateEngine.evaluate(
                HealthStateEngine.Inputs(
                    now = now,
                    todayRow = allDayRows.find { it.dayKey == dayKey },
                    recentMeals = allMeals.filter { it.at.take(10) == dayKey },
                    allMeals = allMeals,
                    recentLogEntries = logs.filter { it.at.take(10) == dayKey },
                    allLogEntries = logs,
                    allDayRows = allDayRows,
                    profile = db.profileDao().getProfileSync(),
                ),
            )
            val pushPullReading = TrainingCoverageEngine.pushPullBalance(logs)


            // 1. Process Weight History
            val weightPoints = weights.mapNotNull { 
                try {
                    ChartPoint(LocalDate.parse(it.at.take(10)), it.kg)
                } catch (e: Exception) { null }
            }.sortedBy { it.date }
            
            // 2. Process Strength Progress
            // Group by exercise ID
            val logsByEx = logs.groupBy { it.exId }
            val strengthList = mutableListOf<StrengthProgress>()
            
            for ((exId, exLogs) in logsByEx) {
                val exDef = EXERCISES[exId] ?: continue
                val isBodyweight = exDef.loadRatio == 0.0
                
                // For each day, find the max 1RM or max reps
                val logsByDay = exLogs.groupBy { it.at.take(10) }
                val progressHistory = mutableListOf<ChartPoint>()
                
                for ((dayStr, dayLogs) in logsByDay) {
                    try {
                        val date = LocalDate.parse(dayStr)
                        val dayMax = if (isBodyweight) {
                            dayLogs.maxOf { it.reps.toDouble() }
                        } else {
                            dayLogs.maxOf { Coach.estimate1RM(it.reps, it.load) }
                        }
                        progressHistory.add(ChartPoint(date, dayMax))
                    } catch (e: Exception) { }
                }
                
                progressHistory.sortBy { it.date }
                
                if (progressHistory.isNotEmpty()) {
                    val currentMax = progressHistory.last().value
                    // To find previous max, let's look at what the max was a month ago (or just the previous data point)
                    // We'll define previous as the max from the start of the current history minus whatever...
                    // Let's just take the first entry in the 28D window, or if it's all new, the very first entry.
                    val thirtyDaysAgo = LocalDate.now().minusDays(30)
                    val oldPoints = progressHistory.filter { it.date.isBefore(LocalDate.now().minusDays(7)) }
                    val previousMax = oldPoints.lastOrNull()?.value ?: progressHistory.firstOrNull()?.value
                    
                    strengthList.add(StrengthProgress(
                        exId = exId,
                        name = exDef.name,
                        isBodyweight = isBodyweight,
                        history = progressHistory,
                        currentMax = currentMax,
                        previousMax = if (previousMax == currentMax && progressHistory.size == 1) null else previousMax
                    ))
                }
            }
            
            // Sort strength by most recent activity or highest volume? Let's sort by having most history
            strengthList.sortByDescending { it.history.size }
            
            val initialState = DashboardUiState(
                isLoading = false,
                weightHistory = weightPoints,
                strengthProgress = strengthList,
                healthSnapshot = healthSnapshot,
                pushPullReading = pushPullReading,
            )
            _uiState.value = initialState
            setTimeRange(initialState.selectedTimeRange)
        }
    }
}
