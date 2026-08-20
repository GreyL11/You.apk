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
        val allLogs = db.logEntryDao().getAllSync()
        val recentLogEntries = allLogs.filter { it.at.startsWith(dayKey) }
        val recentOutcomes = db.actionOutcomeDao().getAllSync()
        val allMeals = db.mealDao().getAllSync()
        val allDayRows = db.dayRowDao().getAllSync()
        val totalHistorical = allMeals.size + allLogs.size + allDayRows.size + recentOutcomes.size
        val profile = db.profileDao().getProfileSync()

        // The SAME assembler TodayViewModel uses. Rebuilding the engine chain here by hand is how
        // the notification and the in-app answer would silently diverge on any future edit.
        val state = PersonalStateBuilder.build(
            now = now, profile = profile, allLogs = allLogs, allDayRows = allDayRows, allMeals = allMeals,
        )

        val ctx = HealthCoachEngine.Context(
            now = now,
            todayRow = todayRow,
            recentMeals = recentMeals,
            recentLogEntries = recentLogEntries,
            recentOutcomes = recentOutcomes,
            totalHistoricalLogs = totalHistorical,
            profile = profile,
            bottleneck = state.bottleneck?.let { HealthCoachEngine.coachDomainFor(it) },
            pushPullBalance = state.pushPull.balance,
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
                    actionId = nba.actionId,
                    domain = nba.domain
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
