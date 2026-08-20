package com.example.domain

import com.example.data.DayRow
import com.example.data.LogEntry
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/**
 * The actual intelligence, not a UI label. Every test here builds real logged history and asserts on
 * the decision that history genuinely warrants.
 */
class DailyDecisionEngineTest {
    private val today = LocalDate.parse("2026-08-20")

    private fun log(exId: String, day: String, sets: Int = 3, difficulty: Int? = null) =
        LogEntry(exId = exId, at = "${day}T10:00:00", reps = 8, sets = sets, load = 50.0, faultEvents = "[]", correctedFrom = null, difficulty = difficulty)

    private fun readiness(
        level: ReadinessEngine.Level,
        confidence: ReadinessEngine.Confidence = ReadinessEngine.Confidence.HIGH,
        vararg factors: String,
    ) = ReadinessEngine.Reading(level, confidence, factors.toList())

    private fun inputs(
        readiness: ReadinessEngine.Reading,
        logs: List<LogEntry> = emptyList(),
        dayRows: List<DayRow> = emptyList(),
        bottleneck: GoalGapEngine.Dimension? = null,
        trainedToday: Boolean = false,
    ) = DailyDecisionEngine.Inputs(today, readiness, logs, dayRows, bottleneck, trainedToday)

    private fun row(day: String, soreness: Int? = null, cardio: List<Cardio.Session> = emptyList()) = DayRow(
        dayKey = day, mood = null, bed = null, wake = null, sleeps = null,
        plans = if (cardio.isEmpty()) null else Cardio.toJson(cardio), skin = null, soreness = soreness,
    )

    /** Two weeks of real upper-body-only training -- enough history for balance claims, with legs
     *  genuinely never trained. The spec's central scenario. */
    private fun upperBodyOnlyHistory() = listOf(
        log("bench", "2026-08-19"), log("ohp", "2026-08-18"), log("row", "2026-08-17"),
        log("bench", "2026-08-15"), log("latPulldown", "2026-08-14"), log("ohp", "2026-08-13"),
        log("bench", "2026-08-12"), log("row", "2026-08-11"),
    )

    // ── training: never repeat what was just trained ──────────────────────────────────────────

    @Test
    fun `shoulders trained yesterday is never the automatic prescription again today`() {
        // A full week of nothing but overhead pressing: vertical push is both the most recent AND
        // would be the "usual" answer for a fixed split. It must not be chosen.
        val logs = (13..19).map { log("ohp", "2026-08-$it") }
        val d = DailyDecisionEngine.decideTraining(inputs(readiness(ReadinessEngine.Level.GOOD), logs))
        assertFalse(
            "vertical push was trained yesterday and must not be today's focus",
            d.focusPatterns.contains(MovementPattern.VERTICAL_PUSH),
        )
    }

    @Test
    fun `repeatedly neglected lower body becomes the priority once recovery allows`() {
        val d = DailyDecisionEngine.decideTraining(inputs(readiness(ReadinessEngine.Level.GOOD), upperBodyOnlyHistory()))
        assertEquals("Lower body", d.focusLabel)
        assertTrue(d.focusPatterns.any { it in TrainingHistory.LOWER_BODY })
        assertEquals(DailyDecisionEngine.TrainingChoice.FULL_SESSION, d.choice)
    }

    @Test
    fun `the reason names the real gap rather than asserting it without evidence`() {
        val d = DailyDecisionEngine.decideTraining(inputs(readiness(ReadinessEngine.Level.GOOD), upperBodyOnlyHistory()))
        assertTrue("must explain itself", d.reasons.isNotEmpty())
        assertTrue(d.reasons.any { it.contains("lower body") })
    }

    @Test
    fun `thin history starts with balanced full-body work and says so`() {
        val logs = listOf(log("bench", "2026-08-19"))
        val d = DailyDecisionEngine.decideTraining(inputs(readiness(ReadinessEngine.Level.GOOD), logs))
        assertEquals("Full body", d.focusLabel)
        assertTrue(d.reasons.any { it.contains("not much training history") })
    }

    @Test
    fun `when everything was just trained, today is recovery rather than doubling up`() {
        // Every balanced pattern hit within the last two days.
        val logs = listOf(
            log("bench", "2026-08-20"), log("ohp", "2026-08-20"), log("row", "2026-08-19"),
            log("latPulldown", "2026-08-19"), log("squat", "2026-08-19"), log("rdl", "2026-08-18"),
            log("lunge", "2026-08-18"),
        ) + (10..16).map { log("bench", "2026-08-$it") }
        val d = DailyDecisionEngine.decideTraining(inputs(readiness(ReadinessEngine.Level.GOOD), logs))
        assertEquals(DailyDecisionEngine.TrainingChoice.RECOVERY_SESSION, d.choice)
    }

    // ── training: readiness is a hard floor ──────────────────────────────────────────────────

    @Test
    fun `very low readiness is rest, no matter how neglected something is`() {
        val d = DailyDecisionEngine.decideTraining(
            inputs(readiness(ReadinessEngine.Level.VERY_LOW, factors = arrayOf("sleep well below your normal")), upperBodyOnlyHistory()),
        )
        assertEquals(DailyDecisionEngine.TrainingChoice.REST, d.choice)
        assertTrue(d.focusPatterns.isEmpty())
        assertFalse(d.allowProgression)
    }

    @Test
    fun `low readiness yields a recovery session, never a normal one`() {
        val d = DailyDecisionEngine.decideTraining(inputs(readiness(ReadinessEngine.Level.LOW), upperBodyOnlyHistory()))
        assertEquals(DailyDecisionEngine.TrainingChoice.RECOVERY_SESSION, d.choice)
        assertFalse(d.allowProgression)
    }

    @Test
    fun `moderate readiness still trains, but reduced`() {
        val d = DailyDecisionEngine.decideTraining(inputs(readiness(ReadinessEngine.Level.MODERATE), upperBodyOnlyHistory()))
        assertEquals(DailyDecisionEngine.TrainingChoice.REDUCED_SESSION, d.choice)
        assertEquals("Lower body", d.focusLabel)
    }

    @Test
    fun `a long unbroken streak reduces the session even at good readiness`() {
        val logs = upperBodyOnlyHistory() + (17..20).map { log("curl", "2026-08-$it") }
        val d = DailyDecisionEngine.decideTraining(inputs(readiness(ReadinessEngine.Level.GOOD), logs))
        assertEquals(DailyDecisionEngine.TrainingChoice.REDUCED_SESSION, d.choice)
        assertTrue(d.reasons.any { it.contains("consecutive training days") })
    }

    // ── progression gating ──────────────────────────────────────────────────────────────────

    @Test
    fun `good readiness with no hard-feedback history allows cautious progression`() {
        val d = DailyDecisionEngine.decideTraining(inputs(readiness(ReadinessEngine.Level.GOOD), upperBodyOnlyHistory()))
        assertTrue(d.allowProgression)
    }

    @Test
    fun `repeated too-hard feedback blocks progression even at good readiness`() {
        val logs = upperBodyOnlyHistory() + listOf(
            log("bench", "2026-08-19", difficulty = 3), log("ohp", "2026-08-18", difficulty = 3),
            log("row", "2026-08-17", difficulty = 3),
        )
        val d = DailyDecisionEngine.decideTraining(inputs(readiness(ReadinessEngine.Level.GOOD), logs))
        assertFalse("recent sessions felt hard -- loads must not increase", d.allowProgression)
        assertTrue(d.reasons.any { it.contains("felt hard") })
    }

    @Test
    fun `poor recovery blocks aggressive progression regardless of easy feedback`() {
        val logs = upperBodyOnlyHistory() + listOf(log("bench", "2026-08-19", difficulty = 1))
        val d = DailyDecisionEngine.decideTraining(inputs(readiness(ReadinessEngine.Level.LOW), logs))
        assertFalse(d.allowProgression)
    }

    // ── cardio ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `poor readiness never yields automatic intervals`() {
        val i = inputs(readiness(ReadinessEngine.Level.LOW), upperBodyOnlyHistory())
        val c = DailyDecisionEngine.decideCardio(i, DailyDecisionEngine.decideTraining(i))
        assertNotEquals(Cardio.Mode.INTERVALS, c.mode)
        assertTrue(Cardio.cost(c.mode) <= Cardio.cost(Cardio.Mode.LIGHT_RECOVERY))
    }

    @Test
    fun `very low readiness prescribes no cardio at all, and explains why`() {
        val i = inputs(readiness(ReadinessEngine.Level.VERY_LOW), upperBodyOnlyHistory())
        val c = DailyDecisionEngine.decideCardio(i, DailyDecisionEngine.decideTraining(i))
        assertEquals(Cardio.Mode.NONE, c.mode)
        assertNull(c.minutes)
        assertTrue(c.reasons.isNotEmpty())
    }

    @Test
    fun `a beginner aerobic base gets steady work, never intervals, even at excellent readiness`() {
        val i = inputs(readiness(ReadinessEngine.Level.EXCELLENT), upperBodyOnlyHistory())
        val c = DailyDecisionEngine.decideCardio(i, DailyDecisionEngine.decideTraining(i))
        assertNotEquals(Cardio.Mode.INTERVALS, c.mode)
        assertTrue(c.reasons.any { it.contains("aerobic base") || it.contains("steady") })
    }

    @Test
    fun `severe soreness rejects hard conditioning`() {
        val rows = listOf(row(today.toString(), soreness = 9))
        val i = inputs(readiness(ReadinessEngine.Level.EXCELLENT), upperBodyOnlyHistory(), rows)
        val c = DailyDecisionEngine.decideCardio(i, DailyDecisionEngine.decideTraining(i))
        assertTrue(Cardio.cost(c.mode) <= Cardio.cost(Cardio.Mode.EASY_WALK))
        assertTrue(c.reasons.any { it.contains("soreness") })
    }

    @Test
    fun `a hard leg day caps cardio to easy movement rather than stacking cost`() {
        // Legs are the neglected focus today, so the session loads them -- cardio must stay easy.
        val i = inputs(readiness(ReadinessEngine.Level.EXCELLENT), upperBodyOnlyHistory())
        val training = DailyDecisionEngine.decideTraining(i)
        assertEquals("Lower body", training.focusLabel)
        val c = DailyDecisionEngine.decideCardio(i, training)
        assertTrue(Cardio.cost(c.mode) <= Cardio.cost(Cardio.Mode.EASY_WALK))
        assertTrue(c.reasons.any { it.contains("legs") })
    }

    @Test
    fun `a long cardio gap is named as the reason for prescribing it`() {
        val rows = listOf(row("2026-08-05", cardio = listOf(Cardio.Session(Cardio.Mode.AEROBIC_BASE, 25))))
        // Upper-body focus today, so no leg cap interferes with the cardio read.
        val logs = (13..19).map { log("squat", "2026-08-$it") }
        val i = inputs(readiness(ReadinessEngine.Level.GOOD), logs, rows)
        val c = DailyDecisionEngine.decideCardio(i, DailyDecisionEngine.decideTraining(i))
        assertTrue(c.reasons.any { it.contains("15 days since your last cardio") })
    }

    @Test
    fun `already-high weekly cardio volume pulls today back to light movement`() {
        val rows = (14..20).map {
            row("2026-08-$it", cardio = listOf(Cardio.Session(Cardio.Mode.AEROBIC_BASE, 40)))
        }
        val logs = (13..19).map { log("squat", "2026-08-$it") }
        val i = inputs(readiness(ReadinessEngine.Level.GOOD), logs, rows)
        val c = DailyDecisionEngine.decideCardio(i, DailyDecisionEngine.decideTraining(i))
        assertEquals(Cardio.Mode.LIGHT_RECOVERY, c.mode)
        assertTrue(c.reasons.any { it.contains("minutes this week") })
    }

    @Test
    fun `no cardio ever logged starts the aerobic base rather than staying silent`() {
        val logs = (13..19).map { log("squat", "2026-08-$it") }
        val i = inputs(readiness(ReadinessEngine.Level.GOOD), logs)
        val c = DailyDecisionEngine.decideCardio(i, DailyDecisionEngine.decideTraining(i))
        assertEquals(Cardio.Mode.AEROBIC_BASE, c.mode)
        assertNotNull(c.minutes)
        assertTrue(c.effort.isNotBlank())
    }

    // ── the whole decision ──────────────────────────────────────────────────────────────────

    @Test
    fun `a rest day's top priority is recovery, not a training label`() {
        val d = DailyDecisionEngine.decide(inputs(readiness(ReadinessEngine.Level.VERY_LOW), upperBodyOnlyHistory()))
        assertEquals("Rest and recovery", d.topPriority)
    }

    @Test
    fun `a real sleep bottleneck outranks the training label as today's top priority`() {
        val d = DailyDecisionEngine.decide(
            inputs(readiness(ReadinessEngine.Level.GOOD), upperBodyOnlyHistory(), bottleneck = GoalGapEngine.Dimension.SLEEP_CONSISTENCY),
        )
        assertEquals("Sleep", d.topPriority)
    }

    @Test
    fun `with no bottleneck the top priority is the real training focus`() {
        val d = DailyDecisionEngine.decide(inputs(readiness(ReadinessEngine.Level.GOOD), upperBodyOnlyHistory()))
        assertEquals("Lower body training", d.topPriority)
    }

    @Test
    fun `the decision always carries its own confidence rather than implying certainty`() {
        val d = DailyDecisionEngine.decide(
            inputs(readiness(ReadinessEngine.Level.MODERATE, ReadinessEngine.Confidence.LOW), upperBodyOnlyHistory()),
        )
        assertEquals(ReadinessEngine.Confidence.LOW, d.confidence)
    }

    @Test
    fun `insufficient readiness data still produces a usable conservative decision`() {
        val d = DailyDecisionEngine.decide(
            inputs(ReadinessEngine.Reading(ReadinessEngine.Level.INSUFFICIENT_DATA, ReadinessEngine.Confidence.INSUFFICIENT, emptyList())),
        )
        // Not rest, not intervals -- something real and safe, with honest confidence.
        assertNotEquals(DailyDecisionEngine.TrainingChoice.REST, d.training.choice)
        assertNotEquals(Cardio.Mode.INTERVALS, d.cardio.mode)
        assertEquals(ReadinessEngine.Confidence.INSUFFICIENT, d.confidence)
    }

    @Test
    fun `no decision path ever prescribes intervals below good readiness`() {
        // Exhaustive over readiness levels -- a safety floor should hold for every one, not just
        // the ones a test happened to pick.
        ReadinessEngine.Level.entries.forEach { level ->
            val d = DailyDecisionEngine.decide(inputs(readiness(level), upperBodyOnlyHistory()))
            if (!ReadinessEngine.allowsHardWork(level)) {
                assertNotEquals("$level must never yield intervals", Cardio.Mode.INTERVALS, d.cardio.mode)
            }
        }
    }
}
