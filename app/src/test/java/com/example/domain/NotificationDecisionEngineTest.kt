package com.example.domain

import com.example.data.ActionOutcome
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class NotificationDecisionEngineTest {

    @Test
    fun shouldNotify_falseIfQuietHours() {
        val nba = HealthCoachEngine.Candidate("hydration", "action", "Title", "Reason", HealthCoachEngine.Tier.ACTIONABLE_NOW)
        val now = LocalDateTime.now()
        val result = NotificationDecisionEngine.shouldNotify(nba, emptyList(), now, isQuietHours = true)
        assertFalse("Should not notify during quiet hours", result)
    }

    @Test
    fun shouldNotify_falseIfGoingWell() {
        val nba = HealthCoachEngine.Candidate("hydration", "action", "Title", "Reason", HealthCoachEngine.Tier.GOING_WELL)
        val now = LocalDateTime.now()
        val result = NotificationDecisionEngine.shouldNotify(nba, emptyList(), now, isQuietHours = false)
        assertFalse("Should not notify for GOING_WELL tier", result)
    }

    @Test
    fun shouldNotify_falseIfCompletedToday() {
        val nba = HealthCoachEngine.Candidate("hydration", "action", "Title", "Reason", HealthCoachEngine.Tier.ACTIONABLE_NOW)
        val now = LocalDateTime.of(2023, 10, 25, 12, 0)
        val outcomes = listOf(
            ActionOutcome(domain = "hydration", event = HealthCoachEngine.ActionState.COMPLETED.name, at = "2023-10-25T10:00:00", actionId = "action")
        )
        val result = NotificationDecisionEngine.shouldNotify(nba, outcomes, now, isQuietHours = false)
        assertFalse("Should not notify if completed today", result)
    }
    
    @Test
    fun shouldNotify_trueIfActionableAndNotCompleted() {
        val nba = HealthCoachEngine.Candidate("hydration", "action", "Title", "Reason", HealthCoachEngine.Tier.ACTIONABLE_NOW)
        val now = LocalDateTime.of(2023, 10, 25, 12, 0)
        val result = NotificationDecisionEngine.shouldNotify(nba, emptyList(), now, isQuietHours = false)
        assertTrue("Should notify if actionable and no recent completion or cooldown", result)
    }
}
