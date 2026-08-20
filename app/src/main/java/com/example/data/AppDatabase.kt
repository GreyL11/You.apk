package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        FaceCapture::class,
        LabResult::class
    ],
    version = 3,
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
    abstract fun labResultDao(): LabResultDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2: Health Connect step counts on the day row, and a table for lab results typed in
         * from a real blood test.
         *
         * Written out rather than falling back to a destructive migration. Someone using this app has
         * months of sleep, meals and sessions in it — dropping that to add two columns would destroy
         * the only thing the coach reasons over, and it would do it silently on upgrade.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `day_row` ADD COLUMN `steps` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lab_result` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`at` TEXT NOT NULL, " +
                        "`marker` TEXT NOT NULL, " +
                        "`value` REAL NOT NULL, " +
                        "`unit` TEXT NOT NULL, " +
                        "`note` TEXT)"
                )
            }
        }

        /**
         * v2 -> v3: the real self-reported signals the closed-loop coach needs and could not
         * honestly derive from anything already stored — per-set difficulty, and the daily
         * energy/soreness/stress/refreshed check-in.
         *
         * All nullable with no DEFAULT, deliberately: every existing row becomes "not answered"
         * rather than silently acquiring a neutral score the person never gave. ReadinessEngine
         * treats absent and average as different things, so a default here would manufacture
         * evidence for every day already in the database.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `log_entry` ADD COLUMN `difficulty` INTEGER")
                db.execSQL("ALTER TABLE `day_row` ADD COLUMN `energy` INTEGER")
                db.execSQL("ALTER TABLE `day_row` ADD COLUMN `soreness` INTEGER")
                db.execSQL("ALTER TABLE `day_row` ADD COLUMN `stress` INTEGER")
                db.execSQL("ALTER TABLE `day_row` ADD COLUMN `refreshed` INTEGER")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gym_trainer_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
