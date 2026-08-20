package com.example.ui.screens

import com.example.data.Profile
import com.example.domain.TrainingProfile
import org.json.JSONArray
import org.json.JSONException

/**
 * Reading and writing the JSON-list fields Room stores as plain strings — plates, equipment,
 * injuries.
 *
 * This is the fix for a real gap, not new plumbing for its own sake: [TodayViewModel] previously
 * built a [TrainingProfile] with only bodyweight/daysPerWeek/goal and silently dropped bar, plates,
 * equipment and injuries, always falling back to [TrainingProfile]'s defaults. That meant two things
 * quietly never worked — a plate breakdown always showed the DEFAULT plate set rather than yours,
 * and [com.example.domain.Planner]'s injury filter (real, tested, and correctly wired on the domain
 * side) had nothing to filter with, because no injury the person ever logged reached it.
 */
private fun parseStringList(json: String?, fallback: List<String>): List<String> {
    if (json.isNullOrBlank()) return fallback
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: JSONException) {
        fallback
    }
}

private fun parseDoubleList(json: String?, fallback: List<Double>): List<Double> {
    if (json.isNullOrBlank()) return fallback
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getDouble(it) }
    } catch (e: JSONException) {
        fallback
    }
}

fun Profile.toTrainingProfile(): TrainingProfile {
    val defaults = TrainingProfile()
    return TrainingProfile(
        bodyweight = bodyweight,
        goal = goal,
        daysPerWeek = daysPerWeek,
        equipment = parseStringList(equipment, defaults.equipment),
        injuries = parseStringList(injuries, defaults.injuries),
        bar = bar,
        plates = parseDoubleList(plates, defaults.plates),
    )
}

fun listToJson(values: List<String>): String = JSONArray(values).toString()
fun doubleListToJson(values: List<Double>): String = JSONArray(values).toString()
