package com.example.domain

import com.example.data.DayRow
import com.example.data.LogEntry
import com.example.data.Meal
import com.example.data.Profile
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Everything the app currently knows about this person, derived once from raw stored rows.
 *
 * This exists to be the single place the engine chain is assembled. Before it, TodayViewModel and
 * NotificationWorker each rebuilt the same HealthState → TrainingLoad → Recovery → Readiness →
 * GoalGap → Decision sequence by hand, which meant the in-app answer and the notification answer
 * could silently drift apart on any future edit. One assembler, two callers, one answer.
 *
 * Layering is deliberate and one-directional:
 *
 *     RAW ROWS (logs, dayRows, meals, profile)
 *         ↓  measured
 *     DERIVED STATE (health snapshot, load, cardio base, pattern recency)
 *         ↓  inferred, with confidence attached
 *     READINESS + BOTTLENECK
 *         ↓  decided
 *     DECISION
 *
 * Nothing lower ever reads from something higher, so an inference can never be mistaken for a
 * measurement.
 */
data class PersonalState(
    val today: LocalDate,
    val profile: Profile?,
    val health: HealthStateEngine.Snapshot,
    val load: TrainingLoadEngine.Reading,
    val recovery: RecoveryEngine.Reading,
    val readiness: ReadinessEngine.Reading,
    val bottleneck: GoalGapEngine.Dimension?,
    val pushPull: TrainingCoverageEngine.PushPullReading,
    val cardioBase: Cardio.Base,
    val cardioWeeklyMinutes: Int,
    val daysSinceCardio: Int?,
    val patternRecency: Map<MovementPattern, Int>,
    val consecutiveTrainingDays: Int,
    val wellbeing: WellbeingEngine.Reading,
    val decision: DailyDecisionEngine.Decision,
    val trainedToday: Boolean,
) {
    /** How much real evidence today's decision rests on — surfaced to the user, never hidden. */
    val confidence: ReadinessEngine.Confidence get() = readiness.confidence
}

object PersonalStateBuilder {
    /**
     * @param now current time; [today] is derived from it so callers can't accidentally pass a
     *  mismatched pair.
     */
    fun build(
        now: LocalDateTime,
        profile: Profile?,
        allLogs: List<LogEntry>,
        allDayRows: List<DayRow>,
        allMeals: List<Meal>,
    ): PersonalState {
        val today = now.toLocalDate()
        val dayKey = today.toString()
        val todayRow = allDayRows.find { it.dayKey == dayKey }
        val recentLogs = allLogs.filter { it.at.startsWith(dayKey) }
        val recentMeals = allMeals.filter { it.at.take(10) == dayKey }

        val health = HealthStateEngine.evaluate(
            HealthStateEngine.Inputs(
                now = now, todayRow = todayRow, recentMeals = recentMeals, allMeals = allMeals,
                recentLogEntries = recentLogs, allLogEntries = allLogs, allDayRows = allDayRows,
                profile = profile,
            ),
        )
        val load = TrainingLoadEngine.evaluate(allLogs, nowDayKey = dayKey)
        val recovery = RecoveryEngine.evaluate(health.sleep, load.state)
        val readiness = ReadinessEngine.evaluate(recovery, todayRow, allLogs, today)
        val bottleneck = GoalGapEngine.evaluate(health, recovery).bottleneck
        val trainedToday = recentLogs.isNotEmpty()

        val decision = DailyDecisionEngine.decide(
            DailyDecisionEngine.Inputs(
                today = today, readiness = readiness, logs = allLogs, dayRows = allDayRows,
                bottleneck = bottleneck, trainedToday = trainedToday,
            ),
        )

        return PersonalState(
            today = today,
            profile = profile,
            health = health,
            load = load,
            recovery = recovery,
            readiness = readiness,
            bottleneck = bottleneck,
            pushPull = TrainingCoverageEngine.pushPullBalance(allLogs),
            cardioBase = Cardio.base(allDayRows, today),
            cardioWeeklyMinutes = Cardio.weeklyMinutes(allDayRows, today),
            daysSinceCardio = Cardio.daysSinceLast(allDayRows, today),
            patternRecency = TrainingHistory.patternRecency(allLogs, today),
            consecutiveTrainingDays = TrainingHistory.consecutiveTrainingDays(allLogs, today),
            wellbeing = WellbeingEngine.evaluate(allDayRows.sortedBy { it.dayKey }.mapNotNull { it.mood }),
            decision = decision,
            trainedToday = trainedToday,
        )
    }
}
