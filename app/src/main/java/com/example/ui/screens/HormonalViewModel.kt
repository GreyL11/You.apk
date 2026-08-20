package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.LabResult
import com.example.data.SettingsManager
import com.example.domain.Explain
import com.example.domain.ExplainStatus
import com.example.domain.GeminiClient
import com.example.domain.TInputs
import com.example.domain.TResult
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The lifestyle read, and the words that go with it.
 *
 * The split is the whole design: [TInputs] decides what the record says and what to do about it, and
 * the model only ever words it. If the model is missing, refuses, or quotes a number that is not in
 * the evidence, the deterministic line shows instead and nothing is lost.
 */
data class HormonalUiState(
    val read: TResult? = null,
    val factors: List<TInputs.Factor> = emptyList(),
    /** Real measured values, typed in from a blood test. Newest last. */
    val labs: List<LabResult> = emptyList(),
    /** Model prose when it validated, the engine's own sentence when it did not. */
    val explanation: String? = null,
    /** Why the words are the app's rather than the model's, when that is the case. */
    val explanationNote: String? = null,
    val loading: Boolean = true,
)

class HormonalViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val gemini = GeminiClient(SettingsManager(application))

    private val _state = MutableStateFlow(HormonalUiState())
    val state: StateFlow<HormonalUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val read = TInputs.read(
                rows = db.dayRowDao().getAllSync(),
                weights = db.weightDao().getAllSync(),
                logs = db.logEntryDao().getAllSync(),
                rounds = db.roundDao().getAllSync(),
                today = LocalDate.now(),
            )
            val labs = db.labResultDao().getAllSync()
            _state.value = HormonalUiState(
                read = read,
                factors = TInputs.factors(read),
                labs = labs,
                loading = true,
            )

            val plain = read.advice.text
            val result = Explain.explainDecision(evidence(read), plain) { ev, feedback ->
                gemini.explain(ev, feedback)
            }
            _state.value = HormonalUiState(
                read = read,
                factors = TInputs.factors(read),
                labs = labs,
                explanation = if (plain == null && result.status != ExplainStatus.OK) null
                else result.display,
                explanationNote = when (result.status) {
                    ExplainStatus.UNVERIFIED ->
                        "The written answer quoted figures that are not in your record, so it was " +
                            "discarded. This is the app's own reading."
                    else -> null
                },
                loading = false,
            )
        }
    }

    /**
     * Record a value read off a real blood test.
     *
     * The only path by which a hormone number enters this app. Nothing derives one, nothing estimates
     * one, and the unit is stored exactly as printed on the report rather than converted — a silent
     * ng/dL to nmol/L conversion is how a number ends up off by a factor of 28.8.
     */
    fun logLab(marker: String, value: Double, unit: String, at: String, note: String? = null) {
        viewModelScope.launch {
            db.labResultDao().insert(
                LabResult(at = at, marker = marker, value = value, unit = unit, note = note),
            )
            refresh()
        }
    }

    /**
     * What the model is allowed to see. Deliberately only the numbers behind this one read — no chat
     * history, no profile, no meals. Every quantity here is one [com.example.domain.Validate] can
     * check a claim against.
     *
     * Nulls are dropped rather than sent as zeros: a domain with nothing logged must not read as a
     * domain measured at zero.
     *
     * ponytail: the median wake time is sent as hour and minute so a truthful "waking at 06:00"
     * validates. That puts a 0 in the index on the hour, which the validator will then accept for any
     * "0" claim in the answer. Bounded and deliberate — bind claims to their referents (or add a
     * clock-aware claim kind) if that looseness ever matters.
     */
    private fun evidence(r: TResult): Map<String, Any?> = buildMap {
        put("windowDays", TInputs.WINDOW)
        put("targetHours", TInputs.SLEEP_TARGET)
        put("trainingFloorDays", TInputs.TRAIN_LOW)
        put("minimumNightsToRead", TInputs.MIN_NIGHTS)

        put(
            "sleep",
            if (r.sleep.verdict == "unknown") {
                mapOf(
                    "status" to "not enough logged",
                    "nightsLogged" to r.sleep.nights,
                )
            } else {
                buildMap {
                    put("mainSleepAvgHours", r.sleep.avg)
                    put("nightsLogged", r.sleep.nights)
                    put("verdict", r.sleep.verdict)
                    r.sleep.totalAvg?.let {
                        put("totalWithNapsAvgHours", it)
                        put("daysWithANap", r.sleep.napDays)
                        put(
                            "napNote",
                            "The verdict is taken from the main sleep only. A short night plus a " +
                                "nap is not a long night.",
                        )
                    }
                }
            },
        )

        put(
            "weight",
            if (r.weight.verdict == "known") {
                mapOf(
                    "changeKgOverWindow" to r.weight.kg,
                    "weighIns" to r.weight.points,
                    "note" to "Direction only. The app knows kilos, not body fat, so this is not " +
                        "judged as good or bad.",
                )
            } else {
                mapOf("status" to "not enough weigh-ins", "weighIns" to r.weight.points)
            },
        )

        put(
            "training",
            mapOf("daysInWindow" to r.training.days, "verdict" to r.training.verdict),
        )

        r.wake?.let { w ->
            put(
                "wake",
                mapOf(
                    "medianClock" to w.median,
                    "medianHour" to w.median.substringBefore(':').toInt(),
                    "medianMinute" to w.median.substringAfter(':').toInt(),
                    "spreadHours" to w.spreadHours,
                    "nights" to w.nights,
                    "regular" to w.regular,
                ),
            )
        } ?: put("wake", mapOf("status" to "no wake times logged"))

        put("decidedAction", r.advice.text)
        put("plan", r.advice.plan)
        put("limitation", TInputs.HORMONAL_BOUNDARY)
    }
}
