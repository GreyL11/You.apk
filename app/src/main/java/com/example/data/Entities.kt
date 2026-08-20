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
    val correctedFrom: Int?,
    /** Real user-reported difficulty for this set, 1 (easy) .. 3 (hard) -- null means not asked/
     *  answered, never a guessed "moderate". The only input ReadinessEngine/progression logic has
     *  for "was this too hard" beyond what a rep-completion count alone can say. */
    val difficulty: Int? = null,
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
    val plans: String?, // JSON Cardio.Session list for the day -- see Cardio.kt toJson/fromJson
    val skin: String?, // JSON SkinData
    /**
     * Steps for the day, imported from Health Connect. Null means not imported, which is NOT zero
     * steps — the whole app treats absent and zero as different things, and a day the phone was left
     * on a desk is not a day nobody walked.
     */
    val steps: Int? = null,
    /**
     * Real self-reports from the daily check-in, 1..10 (soreness 1 = none, 10 = severe). Null means
     * not answered — never a neutral default, since "didn't answer" and "felt average" would drive
     * different readiness reads and only one of them is a fact.
     */
    val energy: Int? = null,
    val soreness: Int? = null,
    val stress: Int? = null,
    /** Did you wake up feeling rested? Null means not asked. */
    val refreshed: Boolean? = null,
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

/**
 * A real measured lab value, typed in from a blood test.
 *
 * This is the only place in the app a hormone number may exist, and it exists because a person read
 * it off their own results — nothing here is derived, estimated, or inferred from logged behaviour.
 * The lifestyle inputs on the hormonal screen sit BESIDE these, never instead of them.
 */
@Entity(tableName = "lab_result")
data class LabResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** Date of the draw, "yyyy-MM-dd" — the draw date matters, not when it was typed in. */
    val at: String,
    /** e.g. "totalTestosterone", "freeTestosterone", "shbg", "vitaminD". */
    val marker: String,
    val value: Double,
    /** As printed on the report: "ng/dL", "nmol/L", "pg/mL". Never converted behind your back. */
    val unit: String,
    val note: String? = null
)
