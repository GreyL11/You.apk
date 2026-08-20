package com.example.domain

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.data.AppDatabase
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads sleep, steps and weight out of Health Connect and files them as the app's own rows.
 *
 * Read-only, and only the three record types the coach actually reasons over. Asking for heart rate
 * or nutrition because the API offers them would be collecting data with no consumer, which is the
 * thing the rest of this app refuses to do in the other direction (never report a verdict from data
 * it does not have).
 *
 * All the deciding lives in [HealthImport], which is pure and tested. This class is the plumbing:
 * availability, permissions, queries, writes.
 */
class HealthConnectSync(private val context: Context) {

    enum class Availability {
        /** No Health Connect on this device and no way to install it (below API 26, or a fork). */
        UNAVAILABLE,

        /** Present but too old to talk to — the user has to update it before permissions can be asked. */
        UPDATE_REQUIRED,

        AVAILABLE,
    }

    fun availability(): Availability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.UPDATE_REQUIRED
        else -> Availability.UNAVAILABLE
    }

    private fun client(): HealthConnectClient? =
        if (availability() == Availability.AVAILABLE) HealthConnectClient.getOrCreate(context) else null

    /** Which of [PERMISSIONS] the user has actually granted — they are granted one at a time. */
    suspend fun granted(): Set<String> = withContext(Dispatchers.IO) {
        client()?.permissionController?.getGrantedPermissions() ?: emptySet()
    }

    suspend fun hasAnyPermission(): Boolean = granted().any { it in PERMISSIONS }

    /**
     * Pull the last [TInputs.WINDOW] days and merge.
     *
     * Partial grants are normal and fine: someone may share sleep and refuse weight. Each record type
     * is read only if its own permission is present, and a missing one simply contributes nothing
     * rather than failing the run.
     */
    suspend fun importInto(
        db: AppDatabase,
        today: LocalDate = LocalDate.now(),
    ): ImportSummary = withContext(Dispatchers.IO) {
        val client = client() ?: return@withContext ImportSummary()
        val allowed = granted()
        val start = HealthImport.windowStart(today)
        val filter = TimeRangeFilter.after(start)

        val existingRows = db.dayRowDao().getAllSync().associateBy { it.dayKey }
        var summary = ImportSummary()

        if (READ_SLEEP in allowed) {
            val sessions = client
                .readRecords(ReadRecordsRequest(SleepSessionRecord::class, filter))
                .records
                .map { it.startTime to it.endTime }
            val (rows, sleepSummary) = HealthImport.mergeSleep(
                HealthImport.toSleepImport(sessions),
                existingRows,
            )
            rows.forEach { db.dayRowDao().insert(it) }
            summary = summary.copy(
                sleepNightsAdded = sleepSummary.sleepNightsAdded,
                sleepNightsKept = sleepSummary.sleepNightsKept,
            )
        }

        if (READ_STEPS in allowed) {
            val records = client
                .readRecords(ReadRecordsRequest(StepsRecord::class, filter))
                .records
                .map { it.startTime to it.count }
            // Re-read: a sleep merge above may have just created rows for the same days.
            val rowsNow = db.dayRowDao().getAllSync().associateBy { it.dayKey }
            val (rows, added) = HealthImport.mergeSteps(HealthImport.toStepImports(records), rowsNow)
            rows.forEach { db.dayRowDao().insert(it) }
            summary = summary.copy(stepDaysAdded = added)
        }

        if (READ_WEIGHT in allowed) {
            val readings = client
                .readRecords(ReadRecordsRequest(WeightRecord::class, filter))
                .records
                .map { it.time to it.weight.inKilograms }
            val (rows, added) = HealthImport.mergeWeights(
                HealthImport.toWeightImports(readings),
                db.weightDao().getAllSync(),
            )
            rows.forEach { db.weightDao().insert(it) }
            db.weightDao().enforceCap()
            summary = summary.copy(weighInsAdded = added)
        }

        db.dayRowDao().enforceCap()
        summary
    }

    companion object {
        val READ_SLEEP: String = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val READ_STEPS: String = HealthPermission.getReadPermission(StepsRecord::class)
        val READ_WEIGHT: String = HealthPermission.getReadPermission(WeightRecord::class)

        /** What the app asks for. Read-only, three types, nothing speculative. */
        val PERMISSIONS = setOf(READ_SLEEP, READ_STEPS, READ_WEIGHT)
    }
}
