package com.example.domain

import com.example.data.ActionOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptationEngineTest {

    @Test
    fun buildProfile_requiresMinimumHistoryToLearnPreference() {
        val outcomes = listOf(
            ActionOutcome(domain = "hydration", event = HealthCoachEngine.ActionState.COMPLETED.name, at = "2023-10-25T14:00:00", actionId = "action"),
            ActionOutcome(domain = "hydration", event = HealthCoachEngine.ActionState.COMPLETED.name, at = "2023-10-26T14:30:00", actionId = "action")
        )
        val profile = AdaptationEngine.buildProfile(outcomes)
        // 2 is less than 3, so shouldn't have learned preferred hour
        assertEquals("Should not learn preferred hour with insufficient data", null, profile.preferredHydrationHour)
    }

    @Test
    fun buildProfile_learnsPreferredHourWhenEnoughHistory() {
        val outcomes = listOf(
            ActionOutcome(domain = "hydration", event = HealthCoachEngine.ActionState.COMPLETED.name, at = "2023-10-25T14:00:00", actionId = "action"),
            ActionOutcome(domain = "hydration", event = HealthCoachEngine.ActionState.COMPLETED.name, at = "2023-10-26T14:30:00", actionId = "action"),
            ActionOutcome(domain = "hydration", event = HealthCoachEngine.ActionState.COMPLETED.name, at = "2023-10-27T14:15:00", actionId = "action")
        )
        val profile = AdaptationEngine.buildProfile(outcomes)
        assertEquals("Should learn preferred hour when enough data", 14, profile.preferredHydrationHour)
    }
}
