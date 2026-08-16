package com.example.domain

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import java.time.LocalDateTime

class NotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(context)
        val now = LocalDateTime.now()
        val dayKey = now.toLocalDate().toString()
        
        val todayRow = db.dayRowDao().getSync(dayKey)
        val recentMeals = db.mealDao().getByDateSync(dayKey)
        val recentLogEntries = db.logEntryDao().getAllSync().filter { it.at.startsWith(dayKey) }
        val recentOutcomes = db.actionOutcomeDao().getAllSync()
        
        val allMealsCount = db.mealDao().getAllSync().size
        val allLogsCount = db.logEntryDao().getAllSync().size
        val allRowsCount = db.dayRowDao().getAllSync().size
        val totalHistorical = allMealsCount + allLogsCount + allRowsCount + recentOutcomes.size
        
        val ctx = HealthCoachEngine.Context(
            now = now,
            todayRow = todayRow,
            recentMeals = recentMeals,
            recentLogEntries = recentLogEntries,
            recentOutcomes = recentOutcomes,
            totalHistoricalLogs = totalHistorical,
            profile = db.profileDao().getProfileSync()
        )
        
        val nba = HealthCoachEngine.selectNextBestAction(ctx) ?: return Result.success()
        
        // Quiet hours: e.g. 10 PM to 7 AM
        val hour = now.hour
        val isQuietHours = hour < 7 || hour >= 22
        
        if (NotificationDecisionEngine.shouldNotify(nba, recentOutcomes, now, isQuietHours)) {
            val controller = NotificationController(context)
            if (controller.hasPermission()) {
                controller.sendCoachNotification(
                    title = nba.title,
                    message = nba.reason,
                    actionId = nba.actionId
                )
                
                // Record that we offered it
                val outcome = com.example.data.ActionOutcome(
                    at = now.toString(),
                    actionId = nba.actionId,
                    domain = nba.domain,
                    event = HealthCoachEngine.ActionState.OFFERED.name
                )
                db.actionOutcomeDao().insert(outcome)
            }
        }
        
        return Result.success()
    }
}
