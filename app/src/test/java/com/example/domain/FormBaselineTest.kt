package com.example.domain

import com.example.data.LogEntry
import org.junit.Assert.*
import org.junit.Test

/**
 * Learning what is normal for one body — and the one thing it must never learn away.
 *
 * The safety tests here are the point of the file. Everything else is a convenience; suppressing a
 * safety cue because someone always does it would be the single most harmful thing this app could
 * do, and it is exactly what a naive "stop nagging about repeated faults" rule would produce.
 */
class FormBaselineTest {
    /** One logged session: `reps` reps, with `faultId` fired on `fires` of them. */
    private fun session(exId: String, reps: Int, faultId: String, fires: Int) = LogEntry(
        exId = exId, at = "2026-08-01T10:00:00", reps = reps, sets = 1, load = 60.0,
        faultEvents = (1..fires).joinToString(",", "[", "]") { """{"rep":$it,"id":"$faultId"}""" },
        correctedFrom = null,
    )

    private fun sessions(n: Int, exId: String, reps: Int, faultId: String, fires: Int) =
        List(n) { session(exId, reps, faultId, fires) }

    // ── the refusal that matters ─────────────────────────────────────────────────────────────

    @Test
    fun `a safety fault is never baselined away, however habitual`() {
        // Knees caving on every rep of twenty sessions. "You always do it" is a reason to say it
        // LOUDER, not to go quiet -- 'valgus' is in Exercises' SAFETY table for squat.
        assertTrue("valgus must be a safety fault for this test to mean anything", isSafetyFault("squat", "valgus"))
        val history = sessions(20, "squat", reps = 10, faultId = "valgus", fires = 10)
        assertTrue("a safety cue must survive any amount of habit", FormBaseline.shouldCue(history, "squat", "valgus"))
        assertTrue(
            "and must never appear in the habitual list the live screen filters by",
            FormBaseline.habitualFaults(history, "squat").none { it.faultId == "valgus" },
        )
    }

    @Test
    fun `an efficiency fault done on every rep for long enough becomes your normal`() {
        val ex = EXERCISES.getValue("squat")
        val efficiency = ex.faults.first { !isSafetyFault("squat", it.id) }
        val history = sessions(8, "squat", reps = 10, faultId = efficiency.id, fires = 10)
        val p = FormBaseline.pattern(history, "squat", efficiency.id)
        assertTrue("8 consistent sessions is a body, not a mood", p.habitual)
        assertFalse(FormBaseline.shouldCue(history, "squat", efficiency.id))
    }

    // ── it must not decide this too early ────────────────────────────────────────────────────

    @Test
    fun `too few sessions means keep cueing, however consistent they were`() {
        val efficiency = EXERCISES.getValue("squat").faults.first { !isSafetyFault("squat", it.id) }
        val history = sessions(FormBaseline.MIN_SESSIONS - 1, "squat", reps = 10, faultId = efficiency.id, fires = 10)
        val p = FormBaseline.pattern(history, "squat", efficiency.id)
        assertFalse("under the session floor, nothing is claimed", p.habitual)
        assertTrue(FormBaseline.shouldCue(history, "squat", efficiency.id))
    }

    @Test
    fun `a brand new lifter is cued on everything`() {
        val efficiency = EXERCISES.getValue("squat").faults.first { !isSafetyFault("squat", it.id) }
        assertTrue(FormBaseline.shouldCue(emptyList(), "squat", efficiency.id))
        assertEquals(emptyList<FormBaseline.Pattern>(), FormBaseline.habitualFaults(emptyList(), "squat"))
    }

    @Test
    fun `an occasional slip under fatigue is still worth correcting`() {
        val efficiency = EXERCISES.getValue("squat").faults.first { !isSafetyFault("squat", it.id) }
        // Fires on 2 of 10 reps every session: real, correctable, not how they move.
        val history = sessions(10, "squat", reps = 10, faultId = efficiency.id, fires = 2)
        assertFalse(FormBaseline.pattern(history, "squat", efficiency.id).habitual)
        assertTrue(FormBaseline.shouldCue(history, "squat", efficiency.id))
    }

    @Test
    fun `something changing is not something settled, even at the same average`() {
        val efficiency = EXERCISES.getValue("squat").faults.first { !isSafetyFault("squat", it.id) }
        // Half the sessions at 100%, half at 0% -- pooled average 0.5, but this is a pattern
        // CHANGING (a cue that worked, an injury developing), which must keep being reported.
        val history = sessions(5, "squat", 10, efficiency.id, 10) + sessions(5, "squat", 10, efficiency.id, 0)
        val p = FormBaseline.pattern(history, "squat", efficiency.id)
        assertFalse("an unstable pattern is not a baseline", p.habitual)
        assertTrue(FormBaseline.shouldCue(history, "squat", efficiency.id))
    }

    // ── it reads only this lifter's own history of this lift ─────────────────────────────────

    @Test
    fun `another lift's sessions do not establish this lift's normal`() {
        val efficiency = EXERCISES.getValue("squat").faults.first { !isSafetyFault("squat", it.id) }
        val history = sessions(20, "bench", reps = 10, faultId = efficiency.id, fires = 10)
        assertFalse(
            "bench sessions say nothing about how you squat",
            FormBaseline.pattern(history, "squat", efficiency.id).habitual,
        )
    }

    @Test
    fun `a session with no reps logged is skipped, not counted as clean`() {
        val efficiency = EXERCISES.getValue("squat").faults.first { !isSafetyFault("squat", it.id) }
        val real = sessions(8, "squat", reps = 10, faultId = efficiency.id, fires = 10)
        val withJunk = real + session("squat", reps = 0, faultId = efficiency.id, fires = 0)
        // Counting a zero-rep row as a clean session would dilute the rate with a logging artefact.
        assertEquals(
            FormBaseline.pattern(real, "squat", efficiency.id).sessions,
            FormBaseline.pattern(withJunk, "squat", efficiency.id).sessions,
        )
    }

    // ── recording is untouched ───────────────────────────────────────────────────────────────

    @Test
    fun `baselining changes what is SAID, never what was recorded`() {
        val efficiency = EXERCISES.getValue("squat").faults.first { !isSafetyFault("squat", it.id) }
        val history = sessions(8, "squat", reps = 10, faultId = efficiency.id, fires = 10)
        // The fault is habitual, so it stops being cued...
        assertFalse(FormBaseline.shouldCue(history, "squat", efficiency.id))
        // ...but every one of those fires is still in the log, so Coach's own fault rate is intact.
        assertEquals(10, Coach.faultCountOf(history.first()))
        assertEquals(10, Coach.faultEventsOf(history.first()).size)
    }

    @Test
    fun `the explanation names a real percentage and a real session count`() {
        val efficiency = EXERCISES.getValue("squat").faults.first { !isSafetyFault("squat", it.id) }
        val history = sessions(8, "squat", reps = 10, faultId = efficiency.id, fires = 10)
        val text = FormBaseline.explain(FormBaseline.pattern(history, "squat", efficiency.id))
        assertTrue(text, text.contains("100%"))
        assertTrue(text, text.contains("8 sessions"))
    }

    // ── the shared JSON reader ───────────────────────────────────────────────────────────────

    @Test
    fun `fault events parse into rep and id pairs, and junk yields nothing`() {
        val e = session("squat", reps = 3, faultId = "torso", fires = 2)
        val parsed = Coach.faultEventsOf(e)
        assertEquals(listOf("torso", "torso"), parsed.map { it.faultId })
        assertEquals(listOf(1, 2), parsed.map { it.rep })

        val junk = e.copy(faultEvents = "not json")
        assertEquals(emptyList<FaultEvent>(), Coach.faultEventsOf(junk))

        // An event missing its id is dropped rather than becoming a fault with an empty name.
        val partial = e.copy(faultEvents = """[{"rep":1}]""")
        assertEquals(emptyList<FaultEvent>(), Coach.faultEventsOf(partial))
    }
}
