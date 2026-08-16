package com.example.domain

import com.example.data.ActionOutcome
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object NotificationDecisionEngine {
    
    // Cooldown in minutes before sending another notification for the SAME domain
    private const val COOLDOWN_MINUTES = 120 
    
    fun shouldNotify(
        candidate: HealthCoachEngine.Candidate,
        recentOutcomes: List<ActionOutcome>,
        now: LocalDateTime,
        isQuietHours: Boolean
    ): Boolean {
        // 1. Is the user inside quiet hours?
        if (isQuietHours) return false
        
        // 2. Is there a real actionable need?
        if (candidate.tier == HealthCoachEngine.Tier.GOING_WELL) return false
        
        val domainOutcomes = recentOutcomes.filter { it.domain == candidate.domain }
        
        // 3. Has the user already completed the action today? (Normally HealthCoachEngine filters this, but just in case)
        val completedToday = domainOutcomes.any { 
            it.event == HealthCoachEngine.ActionState.COMPLETED.name && 
            it.at.startsWith(now.toLocalDate().toString())
        }
        if (completedToday) return false
        
        // 4. Has the user repeatedly ignored it recently? (e.g. 3 postpones today)
        val skipsToday = domainOutcomes.count { 
            (it.event == HealthCoachEngine.ActionState.SKIPPED.name || it.event == HealthCoachEngine.ActionState.POSTPONED.name) &&
            it.at.startsWith(now.toLocalDate().toString())
        }
        if (skipsToday >= 3) return false
        
        // 5. Has a similar notification been sent recently? (Cooldown check)
        val lastNotified = domainOutcomes.lastOrNull { 
            it.event == HealthCoachEngine.ActionState.OFFERED.name 
        }
        if (lastNotified != null) {
            try {
                val lastTime = LocalDateTime.parse(lastNotified.at)
                val minutesSince = ChronoUnit.MINUTES.between(lastTime, now)
                if (minutesSince < COOLDOWN_MINUTES) {
                    return false
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }
        
        return true
    }
}
