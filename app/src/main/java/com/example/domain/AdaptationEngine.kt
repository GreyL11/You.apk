package com.example.domain

import com.example.data.ActionOutcome
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object AdaptationEngine {
    data class BehavioralProfile(
        val preferredHydrationHour: Int?,
        val preferredSkinHour: Int?,
        val frequentlySkippedDomains: List<String>,
        val consistentlyCompletedDomains: List<String>
    )

    fun buildProfile(outcomes: List<ActionOutcome>): BehavioralProfile {
        if (outcomes.isEmpty()) {
            return BehavioralProfile(null, null, emptyList(), emptyList())
        }

        // Group by domain
        val completed = outcomes.filter { it.event == HealthCoachEngine.ActionState.COMPLETED.name }
        val skipped = outcomes.filter { it.event == HealthCoachEngine.ActionState.SKIPPED.name || it.event == HealthCoachEngine.ActionState.POSTPONED.name }

        // Preferred hours
        val hydrationHours = completed.filter { it.domain == "hydration" }.mapNotNull { parseHour(it.at) }
        val skinHours = completed.filter { it.domain == "skinRoutine" }.mapNotNull { parseHour(it.at) }

        val prefHydration = if (hydrationHours.size >= 3) mostFrequent(hydrationHours) else null
        val prefSkin = if (skinHours.size >= 3) mostFrequent(skinHours) else null

        // Domain completion rates
        val domainStats = mutableMapOf<String, Pair<Int, Int>>() // Domain -> (CompletedCount, TotalCount)
        
        outcomes.forEach { 
            val stats = domainStats.getOrDefault(it.domain, Pair(0, 0))
            val isCompleted = if (it.event == HealthCoachEngine.ActionState.COMPLETED.name) 1 else 0
            domainStats[it.domain] = Pair(stats.first + isCompleted, stats.second + 1)
        }

        val frequentlySkipped = domainStats.filter { it.value.second >= 3 && (it.value.first.toDouble() / it.value.second) <= 0.3 }.map { it.key }
        val consistentlyCompleted = domainStats.filter { it.value.second >= 3 && (it.value.first.toDouble() / it.value.second) >= 0.8 }.map { it.key }

        return BehavioralProfile(
            preferredHydrationHour = prefHydration,
            preferredSkinHour = prefSkin,
            frequentlySkippedDomains = frequentlySkipped,
            consistentlyCompletedDomains = consistentlyCompleted
        )
    }

    private fun parseHour(at: String): Int? {
        return try {
            LocalDateTime.parse(at).hour
        } catch (e: Exception) {
            null
        }
    }

    private fun mostFrequent(list: List<Int>): Int? {
        return list.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }
}
