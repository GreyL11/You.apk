package com.example.domain

import com.example.data.LogEntry
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TrainingHistoryTest {
    private val today = LocalDate.parse("2026-08-20")

    private fun log(exId: String, day: String, sets: Int = 3, difficulty: Int? = null) =
        LogEntry(exId = exId, at = "${day}T10:00:00", reps = 8, sets = sets, load = 50.0, faultEvents = "[]", correctedFrom = null, difficulty = difficulty)

    @Test
    fun `days since a pattern reads the real most recent session, not the first`() {
        val logs = listOf(log("bench", "2026-08-10"), log("bench", "2026-08-18"))
        assertEquals(2, TrainingHistory.daysSincePattern(logs, MovementPattern.HORIZONTAL_PUSH, today))
    }

    @Test
    fun `a never-trained pattern is null, never a large number standing in for never`() {
        val logs = listOf(log("bench", "2026-08-18"))
        assertNull(TrainingHistory.daysSincePattern(logs, MovementPattern.SQUAT, today))
    }

    @Test
    fun `consecutive days counts only an unbroken current streak up to today`() {
        val logs = listOf(log("bench", "2026-08-20"), log("row", "2026-08-19"), log("squat", "2026-08-18"))
        assertEquals(3, TrainingHistory.consecutiveTrainingDays(logs, today))
    }

    @Test
    fun `a gap breaks the streak rather than being counted through`() {
        // Trained today and 3 days running before a gap -- the streak is today only.
        val logs = listOf(log("bench", "2026-08-20"), log("row", "2026-08-17"), log("squat", "2026-08-16"))
        assertEquals(1, TrainingHistory.consecutiveTrainingDays(logs, today))
    }

    @Test
    fun `nothing logged today means no current streak`() {
        val logs = listOf(log("bench", "2026-08-19"), log("row", "2026-08-18"))
        assertEquals(0, TrainingHistory.consecutiveTrainingDays(logs, today))
    }

    @Test
    fun `a pattern trained yesterday counts as recently trained`() {
        val logs = listOf(log("ohp", "2026-08-19"))
        assertTrue(TrainingHistory.recentlyTrainedPatterns(logs, today).contains(MovementPattern.VERTICAL_PUSH))
    }

    @Test
    fun `a pattern trained a week ago is not recently trained`() {
        val logs = listOf(log("ohp", "2026-08-13"))
        assertFalse(TrainingHistory.recentlyTrainedPatterns(logs, today).contains(MovementPattern.VERTICAL_PUSH))
    }

    @Test
    fun `long-neglected and never-trained patterns both count as neglected`() {
        val logs = listOf(log("bench", "2026-08-19"), log("squat", "2026-08-01"))
        val neglected = TrainingHistory.neglectedPatterns(logs, today)
        assertTrue("squat 19 days ago is neglected", neglected.contains(MovementPattern.SQUAT))
        assertTrue("never-hinged is neglected", neglected.contains(MovementPattern.HINGE))
        assertFalse("bench yesterday is not neglected", neglected.contains(MovementPattern.HORIZONTAL_PUSH))
    }

    @Test
    fun `balance claims wait for real history rather than calling everything neglected on day one`() {
        val logs = listOf(log("bench", "2026-08-19"))
        assertFalse(TrainingHistory.hasEnoughHistory(logs))
        val enough = (10..19).map { log("bench", "2026-08-$it") }
        assertTrue(TrainingHistory.hasEnoughHistory(enough))
    }

    @Test
    fun `recent difficulty averages per day so one long day cannot dominate`() {
        // Day A: eight hard sets. Day B: one easy set. Per-day averaging gives (3+1)/2 = 2.0;
        // pooling every set would give ~2.8 and wrongly read as "recent sessions felt hard".
        val logs = (1..8).map { log("bench", "2026-08-19", difficulty = 3) } + listOf(log("row", "2026-08-20", difficulty = 1))
        assertEquals(2.0, TrainingHistory.recentDifficulty(logs)!!, 0.001)
    }

    @Test
    fun `difficulty is null when nobody answered, never a neutral middle value`() {
        val logs = listOf(log("bench", "2026-08-19"), log("row", "2026-08-20"))
        assertNull(TrainingHistory.recentDifficulty(logs))
    }

    @Test
    fun `unmapped exercise ids never contribute to any pattern recency`() {
        val logs = listOf(log("someUnknownLift", "2026-08-20"))
        assertTrue(TrainingHistory.patternRecency(logs, today).isEmpty())
    }
}
