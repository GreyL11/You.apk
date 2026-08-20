package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun getProfile(): Flow<Profile?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun getProfileSync(): Profile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: Profile)
}

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entry ORDER BY at ASC")
    suspend fun getAllSync(): List<LogEntry>

    @Query("SELECT * FROM log_entry WHERE exId = :exId ORDER BY at ASC")
    suspend fun getHistorySync(exId: String): List<LogEntry>

    @Insert
    suspend fun insert(entry: LogEntry)

    @Query("DELETE FROM log_entry WHERE id NOT IN (SELECT id FROM log_entry ORDER BY at DESC LIMIT 500)")
    suspend fun enforceCap()
}

@Dao
interface VerdictDao {
    @Query("SELECT * FROM verdict ORDER BY at ASC")
    suspend fun getAllSync(): List<Verdict>

    @Insert
    suspend fun insert(verdict: Verdict)

    @Query("DELETE FROM verdict WHERE id NOT IN (SELECT id FROM verdict ORDER BY at DESC LIMIT 200)")
    suspend fun enforceCap()
}

@Dao
interface RoundDao {
    @Query("SELECT * FROM round ORDER BY at ASC")
    suspend fun getAllSync(): List<Round>

    @Insert
    suspend fun insert(round: Round)

    @Query("DELETE FROM round WHERE id NOT IN (SELECT id FROM round ORDER BY at DESC LIMIT 500)")
    suspend fun enforceCap()
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meal ORDER BY at ASC")
    suspend fun getAllSync(): List<Meal>

    @Query("SELECT * FROM meal WHERE at LIKE :dateString || '%'")
    suspend fun getByDateSync(dateString: String): List<Meal>

    @Insert
    suspend fun insert(meal: Meal)

    @Query("DELETE FROM meal WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM meal WHERE id NOT IN (SELECT id FROM meal ORDER BY at DESC LIMIT 3000)")
    suspend fun enforceCap()
}

@Dao
interface FoodDao {
    @Query("SELECT * FROM food")
    suspend fun getAllSync(): List<Food>

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun getSync(id: String): Food?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: Food)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight ORDER BY at ASC")
    suspend fun getAllSync(): List<Weight>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(weight: Weight)

    @Query("DELETE FROM weight WHERE at NOT IN (SELECT at FROM weight ORDER BY at DESC LIMIT 400)")
    suspend fun enforceCap()
}

@Dao
interface DayRowDao {
    @Query("SELECT * FROM day_row ORDER BY dayKey ASC")
    suspend fun getAllSync(): List<DayRow>

    @Query("SELECT * FROM day_row WHERE dayKey = :dayKey")
    suspend fun getSync(dayKey: String): DayRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dayRow: DayRow)

    @Query("DELETE FROM day_row WHERE dayKey NOT IN (SELECT dayKey FROM day_row ORDER BY dayKey DESC LIMIT 420)")
    suspend fun enforceCap()
}

@Dao
interface CheckEntityDao {
    @Query("SELECT * FROM check_entity ORDER BY at ASC")
    suspend fun getAllSync(): List<CheckEntity>

    @Insert
    suspend fun insert(check: CheckEntity)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_message ORDER BY at ASC")
    suspend fun getAllSync(): List<ChatMessage>

    @Insert
    suspend fun insert(message: ChatMessage)

    @Query("DELETE FROM chat_message WHERE id NOT IN (SELECT id FROM chat_message ORDER BY at DESC LIMIT 200)")
    suspend fun enforceCap()
}

@Dao
interface ActionOutcomeDao {
    @Query("SELECT * FROM action_outcome ORDER BY at ASC")
    suspend fun getAllSync(): List<ActionOutcome>

    @Query("SELECT * FROM action_outcome WHERE actionId = :actionId ORDER BY at DESC LIMIT 1")
    suspend fun getLastEventSync(actionId: String): ActionOutcome?

    @Insert
    suspend fun insert(outcome: ActionOutcome)

    @Query("DELETE FROM action_outcome WHERE id NOT IN (SELECT id FROM action_outcome ORDER BY at DESC LIMIT 1000)")
    suspend fun enforceCap()
}

@Dao
interface FaceCaptureDao {
    @Query("SELECT * FROM face_capture")
    suspend fun getAllSync(): List<FaceCapture>

    @Insert
    suspend fun insert(capture: FaceCapture)

    @Query("DELETE FROM face_capture WHERE id NOT IN (SELECT id FROM face_capture ORDER BY id DESC LIMIT 300)")
    suspend fun enforceCap()

    @Query("DELETE FROM face_capture")
    suspend fun deleteAll()
}

@Dao
interface LabResultDao {
    @Query("SELECT * FROM lab_result ORDER BY at ASC")
    suspend fun getAllSync(): List<LabResult>

    @Query("SELECT * FROM lab_result WHERE marker = :marker ORDER BY at ASC")
    suspend fun getByMarkerSync(marker: String): List<LabResult>

    @Insert
    suspend fun insert(result: LabResult)

    @Query("DELETE FROM lab_result WHERE id = :id")
    suspend fun delete(id: Int)
}
