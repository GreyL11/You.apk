package com.example.domain

import com.example.data.LogEntry
import org.junit.Assert.*
import org.junit.Test

class TrainingCoverageEngineTest {
    private fun log(exId: String, day: Int) =
        LogEntry(exId = exId, at = "2026-08-%02dT10:00:00".format(day), reps = 8, sets = 3, load = 50.0, faultEvents = "[]", correctedFrom = null)

    @Test
    fun `every catalogued compound and isolation exercise maps to a real movement pattern`() {
        // No exercise silently falls through to "no pattern" -- every id in the map must be real.
        for (exId in TrainingCoverageEngine.PATTERN_BY_EXERCISE.keys) {
            assertTrue("$exId is not a real exercise id", EXERCISES.containsKey(exId))
        }
    }

    @Test
    fun `volumeByPattern sums real sets and ignores exercises with no pattern mapping`() {
        val logs = listOf(log("bench", 1), log("bench", 3), log("curl", 2))
        val volumes = TrainingCoverageEngine.volumeByPattern(logs).associateBy { it.pattern }
        assertEquals(6, volumes.getValue(MovementPattern.HORIZONTAL_PUSH).sets) // 3+3
        assertEquals(2, volumes.getValue(MovementPattern.HORIZONTAL_PUSH).sessions)
        assertEquals(3, volumes.getValue(MovementPattern.ISOLATION).sets)
    }

    @Test
    fun `fewer than MIN_SESSIONS_PER_SIDE on either side refuses to call an imbalance`() {
        // One heavy bench session against zero rows -- not evidence of an imbalance yet.
        val reading = TrainingCoverageEngine.pushPullBalance(listOf(log("bench", 1)))
        assertEquals(TrainingCoverageEngine.Balance.INSUFFICIENT_DATA, reading.balance)
    }

    @Test
    fun `real push-heavy logged history reads pull as needing work`() {
        val logs = (1..4).map { log("bench", it) } + (1..3).map { log("row", it + 10) }
        // 12 push sets vs 9 pull sets -> ratio 1.33 > 1.3 band
        val reading = TrainingCoverageEngine.pushPullBalance(logs)
        assertEquals(12, reading.pushSets)
        assertEquals(9, reading.pullSets)
        assertEquals(TrainingCoverageEngine.Balance.PULL_NEEDS_WORK, reading.balance)
    }

    @Test
    fun `real pull-heavy logged history reads push as needing work`() {
        val logs = (1..3).map { log("bench", it) } + (1..4).map { log("row", it + 10) }
        val reading = TrainingCoverageEngine.pushPullBalance(logs)
        assertEquals(TrainingCoverageEngine.Balance.PUSH_NEEDS_WORK, reading.balance)
    }

    @Test
    fun `comparable volume on both sides reads BALANCED, not a false imbalance`() {
        val logs = (1..3).map { log("bench", it) } + (1..3).map { log("row", it + 10) }
        val reading = TrainingCoverageEngine.pushPullBalance(logs)
        assertEquals(reading.pushSets, reading.pullSets)
        assertEquals(TrainingCoverageEngine.Balance.BALANCED, reading.balance)
    }

    @Test
    fun `isolation volume never counts toward push or pull totals`() {
        // Heavy curl/lateral-raise volume alone should never manufacture a push or pull reading.
        val logs = (1..10).map { log("curl", it) } + (1..10).map { log("lateralRaise", it) }
        val reading = TrainingCoverageEngine.pushPullBalance(logs)
        assertEquals(0, reading.pushSets)
        assertEquals(0, reading.pullSets)
        assertEquals(TrainingCoverageEngine.Balance.INSUFFICIENT_DATA, reading.balance)
    }
}
