package com.example.domain

import com.example.data.DayRow
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cardio, stored on the existing [DayRow.plans] JSON column — no new table and no further schema
 * bump, the same reasoning [FaceScan] uses for FaceCapture.data.
 *
 * Modes are ordered by real recovery cost, which is what makes them comparable at all: the decision
 * engine's whole job is picking the highest-value mode whose cost today's recovery can actually
 * afford. Durations are conservative starting points for someone building an aerobic base, not
 * performance targets.
 */
object Cardio {
    enum class Mode {
        /** No cardio today, on purpose — a real decision, not an absence of one. */
        NONE,
        LIGHT_RECOVERY,
        EASY_WALK,
        AEROBIC_BASE,
        MODERATE_CONDITIONING,
        INTERVALS,
    }

    /** Relative recovery cost, 0 (none) to 4 (highest). Used to compare candidates, never shown
     *  as a number to the user. */
    fun cost(mode: Mode): Int = when (mode) {
        Mode.NONE -> 0
        Mode.LIGHT_RECOVERY -> 1
        Mode.EASY_WALK -> 1
        Mode.AEROBIC_BASE -> 2
        Mode.MODERATE_CONDITIONING -> 3
        Mode.INTERVALS -> 4
    }

    fun label(mode: Mode): String = when (mode) {
        Mode.NONE -> "No cardio today"
        Mode.LIGHT_RECOVERY -> "Light recovery movement"
        Mode.EASY_WALK -> "Easy walk"
        Mode.AEROBIC_BASE -> "Aerobic base — brisk walk or easy treadmill"
        Mode.MODERATE_CONDITIONING -> "Moderate conditioning"
        Mode.INTERVALS -> "Intervals"
    }

    /** How hard, in plain words a person can actually act on — never a heart-rate zone, since the
     *  app has no heart-rate data and a made-up bpm target would be worse than a description. */
    fun effort(mode: Mode): String = when (mode) {
        Mode.NONE -> ""
        Mode.LIGHT_RECOVERY -> "Very easy — you should feel better afterwards, not tired"
        Mode.EASY_WALK -> "Easy, comfortable pace"
        Mode.AEROBIC_BASE -> "Conversational — you could talk, but not sing"
        Mode.MODERATE_CONDITIONING -> "Working, but controlled and sustainable"
        Mode.INTERVALS -> "Hard efforts with full recoveries between"
    }

    /** Minutes. Null for NONE. Deliberately a single conservative figure per mode rather than a
     *  fitted progression: this app has no real cardio-fitness measurement to fit against yet. */
    fun minutes(mode: Mode): Int? = when (mode) {
        Mode.NONE -> null
        Mode.LIGHT_RECOVERY -> 15
        Mode.EASY_WALK -> 20
        Mode.AEROBIC_BASE -> 25
        Mode.MODERATE_CONDITIONING -> 20
        Mode.INTERVALS -> 15
    }

    /** A cardio session that actually happened, as logged by the person. */
    data class Session(
        val mode: Mode,
        val minutes: Int,
        /** 1 (easy) .. 3 (hard), as reported. Null means not answered. */
        val effortRating: Int? = null,
    )

    fun toJson(sessions: List<Session>): String = JSONArray().apply {
        sessions.forEach { s ->
            put(JSONObject().apply {
                put("mode", s.mode.name)
                put("minutes", s.minutes)
                s.effortRating?.let { put("effort", it) }
            })
        }
    }.toString()

    /** Robust against a malformed or absent blob, and against the legacy `plans` shape this column
     *  previously held — an unreadable value yields no sessions rather than a crash or a fake one. */
    fun fromJson(data: String?): List<Session> {
        if (data.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(data)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val mode = runCatching { Mode.valueOf(o.optString("mode")) }.getOrNull() ?: return@mapNotNull null
                val minutes = o.optInt("minutes", 0)
                if (minutes <= 0) return@mapNotNull null
                Session(mode, minutes, if (o.has("effort")) o.optInt("effort") else null)
            }
        }.getOrDefault(emptyList())
    }

    // ── history ──────────────────────────────────────────────────────────────────────────────

    /** Every logged cardio session with its day, most recent first. */
    fun history(rows: List<DayRow>): List<Pair<String, Session>> =
        rows.flatMap { row -> fromJson(row.plans).map { row.dayKey to it } }
            .sortedByDescending { it.first }

    /** Days since the last cardio session of any kind. Null when there has never been one. */
    fun daysSinceLast(rows: List<DayRow>, today: LocalDate): Int? {
        val last = history(rows).firstOrNull()?.first ?: return null
        return ChronoUnit.DAYS.between(LocalDate.parse(last), today).toInt()
    }

    /** Total real logged cardio minutes in the trailing 7 days including [today]. */
    fun weeklyMinutes(rows: List<DayRow>, today: LocalDate): Int {
        val start = today.minusDays(6).toString()
        return history(rows).filter { it.first >= start && it.first <= today.toString() }
            .sumOf { it.second.minutes }
    }

    /** Sessions in the trailing 7 days. */
    fun weeklySessions(rows: List<DayRow>, today: LocalDate): Int {
        val start = today.minusDays(6).toString()
        return history(rows).count { it.first >= start && it.first <= today.toString() }
    }

    /**
     * How developed this person's cardio actually is, from what they've logged — never from an
     * assumed starting point. Deliberately three coarse bands: the app has minutes and self-reported
     * effort, which honestly supports "beginner / developing / established" and nothing finer.
     */
    enum class Base { BEGINNER, DEVELOPING, ESTABLISHED, INSUFFICIENT_DATA }

    /** Sessions needed over the trailing month before any base claim is made at all. */
    const val MIN_SESSIONS_FOR_BASE = 4

    fun base(rows: List<DayRow>, today: LocalDate): Base {
        val monthStart = today.minusDays(27).toString()
        val recent = history(rows).filter { it.first >= monthStart && it.first <= today.toString() }
        if (recent.size < MIN_SESSIONS_FOR_BASE) return Base.INSUFFICIENT_DATA
        // Real aerobic volume only -- a month of 15-minute recovery walks is not an aerobic base,
        // so LIGHT_RECOVERY/NONE minutes don't count toward building one.
        val aerobicMinutes = recent
            .filter { cost(it.second.mode) >= cost(Mode.EASY_WALK) }
            .sumOf { it.second.minutes }
        val perWeek = aerobicMinutes / 4.0
        return when {
            perWeek >= 120 -> Base.ESTABLISHED
            perWeek >= 50 -> Base.DEVELOPING
            else -> Base.BEGINNER
        }
    }
}
