package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Profile::class,
        LogEntry::class,
        Verdict::class,
        Round::class,
        Meal::class,
        Food::class,
        Weight::class,
        DayRow::class,
        CheckEntity::class,
        ChatMessage::class,
        ActionOutcome::class,
        FaceCapture::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun verdictDao(): VerdictDao
    abstract fun roundDao(): RoundDao
    abstract fun mealDao(): MealDao
    abstract fun foodDao(): FoodDao
    abstract fun weightDao(): WeightDao
    abstract fun dayRowDao(): DayRowDao
    abstract fun checkEntityDao(): CheckEntityDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun actionOutcomeDao(): ActionOutcomeDao
    abstract fun faceCaptureDao(): FaceCaptureDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gym_trainer_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
