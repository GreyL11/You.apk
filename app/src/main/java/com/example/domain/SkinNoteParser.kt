package com.example.domain

object SkinNoteParser {
    /**
     * Parses a free-text Gemini response and extracts inferred habits.
     * Searches for simple boolean flags like "moisturise: true" or "spf: false".
     */
    fun parseNotes(response: String): Map<String, Boolean> {
        val extracted = mutableMapOf<String, Boolean>()
        val lines = response.lines()
        for (line in lines) {
            val lower = line.lowercase()
            for (habit in Skin.HABITS) {
                val key = habit.id.lowercase()
                if (lower.contains(key)) {
                    if (lower.contains("$key: true") || lower.contains("$key=true")) {
                        extracted[habit.id] = true
                    } else if (lower.contains("$key: false") || lower.contains("$key=false")) {
                        extracted[habit.id] = false
                    }
                }
            }
        }
        return extracted
    }
}
