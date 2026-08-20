package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.domain.Skin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Loads the real, already-logged data Skin's association engine needs — no separate skin table,
 *  it reads the same DayRow/Meal/LogEntry rows every other screen does. */
class SkinViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)

    private val _advice = MutableStateFlow<Skin.Advice?>(null)
    val advice: StateFlow<Skin.Advice?> = _advice

    private val _associations = MutableStateFlow<List<Skin.Association>>(emptyList())
    val associations: StateFlow<List<Skin.Association>> = _associations

    private val _adherence = MutableStateFlow<Skin.Adherence?>(null)
    val adherence: StateFlow<Skin.Adherence?> = _adherence

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val dayRows = db.dayRowDao().getAllSync()
            val meals = db.mealDao().getAllSync()
            val logs = db.logEntryDao().getAllSync()
            _advice.value = Skin.advice(dayRows, meals, logs)
            _associations.value = Skin.associations(dayRows, meals, logs)
            _adherence.value = Skin.routineAdherence(dayRows)
        }
    }
}
