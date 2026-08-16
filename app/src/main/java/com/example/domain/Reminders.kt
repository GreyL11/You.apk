package com.example.domain

object Reminders {
    fun hydrationHours(enabled: Boolean, startHour: Int, endHour: Int, intervalHours: Int): List<Int> {
        if (!enabled || startHour >= endHour || intervalHours <= 0) return emptyList()
        val hours = mutableListOf<Int>()
        var current = startHour
        while (current < endHour) {
            hours.add(current)
            current += intervalHours
        }
        return hours
    }
    
    fun hydrationId(hour: Int): Int {
        return idFor("hydration:$hour")
    }
    
    fun postponeId(actionId: String): Int {
        return idFor("postpone:$actionId")
    }
    
    private fun idFor(str: String): Int {
        var hash = 0
        for (i in str.indices) {
            hash = (hash * 31 + str[i].code)
        }
        return hash and 0x7FFFFFFF
    }
}
