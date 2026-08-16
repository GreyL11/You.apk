package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.FaceCapture
import com.example.domain.FaceMetrics
import com.example.domain.FaceScan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Reuses the existing FaceCapture entity/DAO as-is (see AppDatabase.kt) — no new table, no
 *  schema/version bump. `data` is FaceScan's JSON encoding of one real, measured FaceMetrics. */
class FaceScanViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)

    private val _history = MutableStateFlow<List<FaceScan.Record>>(emptyList())
    val history: StateFlow<List<FaceScan.Record>> = _history

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            val rows = db.faceCaptureDao().getAllSync()
            _history.value = rows.map { FaceScan.fromJson(it.id, it.data) }.sortedByDescending { it.at }
        }
    }

    /** Suspend (not launched internally) so the caller can await the save before navigating to
     *  the result screen, which reloads history fresh from Room rather than sharing view-model
     *  instances across nav destinations. */
    suspend fun saveCapture(metrics: FaceMetrics) {
        val at = LocalDateTime.now().toString()
        db.faceCaptureDao().insert(FaceCapture(data = FaceScan.toJson(at, valid = true, metrics = metrics)))
        db.faceCaptureDao().enforceCap()
        loadHistory()
    }
}
