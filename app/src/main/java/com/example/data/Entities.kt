package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val bodyweight: Double,
    val daysPerWeek: Int,
    val bar: Double,
    val plates: String, // JSON List<Double>
    val experience: Int,
    val goal: String,
    val equipment: String, // JSON List<String>
    val injuries: String, // JSON List<String>
    val kcalTarget: Int?,
    val poseModel: String?
)

@Entity(tableName = "log_entry")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val exId: String,
    val at: String,
    val reps: Int,
    val sets: Int?,
    val load: Double,
    val faultEvents: String, // JSON List<FaultEvent>
    val correctedFrom: Int?
)

@Entity(tableName = "verdict")
data class Verdict(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val at: String,
    val exId: String,
    val decision: String,
    val unit: String,
    val from: Double,
    val to: Double,
    val reason: String,
    val evidence: String // JSON VerdictEvidence
)

@Entity(tableName = "round")
data class Round(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val at: String,
    val stats: String? // JSON
)

@Entity(tableName = "meal")
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val at: String,
    val foodId: String,
    val qty: Double
)

@Entity(tableName = "food")
data class Food(
    @PrimaryKey val id: String,
    val name: String,
    val serving: Double,
    val cat: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val ml: Double?
)

@Entity(tableName = "weight")
data class Weight(
    @PrimaryKey val at: String, // e.g. "2023-10-25"
    val kg: Double
)

@Entity(tableName = "day_row")
data class DayRow(
    @PrimaryKey val dayKey: String,
    val mood: Int?,
    val bed: String?,
    val wake: String?,
    val sleeps: String?, // JSON List<SleepBlock>
    val plans: String?, // JSON List<Plan>
    val skin: String? // JSON SkinData
)

@Entity(tableName = "check_entity") // PHQ-9/GAD-7
data class CheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val at: String,
    val kind: String,
    val score: Int,
    val answers: String // JSON List<Int>
)

@Entity(tableName = "chat_message")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val role: String,
    val content: String,
    val at: String
)

@Entity(tableName = "action_outcome")
data class ActionOutcome(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val at: String,
    val actionId: String,
    val domain: String,
    val event: String
)

@Entity(tableName = "face_capture")
data class FaceCapture(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val data: String // JSON of the whole capture to easily store
)
