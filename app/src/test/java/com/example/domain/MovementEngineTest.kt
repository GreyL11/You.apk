package com.example.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the state machine that replaced the old hardcoded single-exercise RepCounter: valid
 * reps, too-fast rejected reps, fault hold-frames, and invisible/missing-landmark gating — the
 * exact invariants exercises.js's own step() is built to guarantee.
 */
class MovementEngineTest {

    // squat: primary=KNEE via angle(hip,knee,ankle); repStart=168 (near-straight), repEnd=95
    // (deep). Placing ankle opposite hip (through knee) gives ~180°; perpendicular gives ~90°.
    private val topKnee = mapOf(
        Joint.SHOULDER to Pt(0.0, -2.0, 1.0),
        Joint.HIP to Pt(0.0, -1.0, 1.0),
        Joint.KNEE to Pt(0.0, 0.0, 1.0),
        Joint.ANKLE to Pt(0.0, 1.0, 1.0), // ~180 degrees at the knee
    )
    private val bottomKnee = mapOf(
        Joint.SHOULDER to Pt(0.0, -2.0, 1.0),
        Joint.HIP to Pt(0.0, -1.0, 1.0),
        Joint.KNEE to Pt(0.0, 0.0, 1.0),
        Joint.ANKLE to Pt(1.0, 0.0, 1.0), // ~90 degrees at the knee
    )

    private fun frame(joints: Map<Joint, Pt>, tMs: Long, view: String = "side") = MovementFrame(joints, joints, tMs, view)

    @Test
    fun `a full down-and-up counts exactly one real rep`() {
        val engine = MovementEngine("squat")
        engine.step(frame(topKnee, 0))
        engine.step(frame(bottomKnee, 100))
        engine.step(frame(bottomKnee, 133)) // ema=104.4, atEnd=true
        engine.step(frame(topKnee, 700))
        val r = engine.step(frame(topKnee, 733)) // ema=167.9, atStart=true
        assertEquals(1, r.reps)
        assertTrue(r.repCompleted)
        assertEquals("start", r.phase)
    }

    @Test
    fun `kit moved too fast is rejected, not counted as a rep`() {
        val engine = MovementEngine("squat")
        engine.step(frame(topKnee, 0))
        engine.step(frame(bottomKnee, 100))
        engine.step(frame(bottomKnee, 133)) // ema=104.4, atEnd=true
        engine.step(frame(topKnee, 300)) 
        engine.step(frame(topKnee, 333))
        engine.step(frame(topKnee, 366))
        val r = engine.step(frame(topKnee, 400)) // only 300ms — under MIN_REP_MS(500)
        assertEquals(0, r.reps)
        assertFalse(r.repCompleted)
        assertEquals(1, engine.rejected)
    }

    @Test
    fun `a fault fires only after HOLD_FRAMES consecutive frames, not on a single one`() {
        val engine = MovementEngine("squat")
        // Big torso lean (shoulder far off to the side of the hip) on every frame, held position
        // (top-of-rep angle) so the primary-joint visibility gate passes throughout.
        val leaning = topKnee + mapOf(Joint.SHOULDER to Pt(2.0, -1.2, 1.0)) // hip=(0,-1): atan2(2.0,0.2)=84 deg, comfortably > squat's 55 deg threshold
        val r1 = engine.step(frame(leaning, 0))
        val r2 = engine.step(frame(leaning, 33))
        val r3 = engine.step(frame(leaning, 66))
        assertTrue("fault must not fire on frame 1", r1.faults.none { it.id == "torso" })
        assertTrue("fault must not fire on frame 2 (only 2 consecutive)", r2.faults.none { it.id == "torso" })
        assertTrue("fault must fire on frame 3 (HOLD_FRAMES=3 reached)", r3.faults.any { it.id == "torso" })
        assertEquals(1, engine.faultCounts["torso"])
        assertEquals(1, engine.faultEvents.size)
    }

    @Test
    fun `a fault stops re-firing every frame once counted, until it resets`() {
        val engine = MovementEngine("squat")
        val leaning = topKnee + mapOf(Joint.SHOULDER to Pt(1.0, -1.7, 1.0))
        engine.step(frame(leaning, 0)); engine.step(frame(leaning, 33))
        val r3 = engine.step(frame(leaning, 66))
        val r4 = engine.step(frame(leaning, 99))
        assertTrue(r3.faults.any { it.id == "torso" })
        assertTrue("the same hold-frames window must not double-fire on frame 4", r4.faults.none { it.id == "torso" })
    }

    @Test
    fun `a missing required joint reports invisible rather than a fabricated angle`() {
        val engine = MovementEngine("squat")
        val blindKnee = topKnee + mapOf(Joint.KNEE to Pt(0.0, 0.0, 0.1)) // below MIN_JOINT_VIS(0.5)
        val r = engine.step(frame(blindKnee, 0))
        assertFalse(r.visible)
        assertTrue(r.missing.contains(Joint.KNEE))
        assertTrue(r.faults.isEmpty())
        assertEquals(0, r.reps)
    }

    @Test
    fun `low-visibility required joints are reported missing on a different exercise too`() {
        // curl's primary is ELBOW -> needs {shoulder, elbow, wrist}. Note: a joint key ABSENT
        // from the map (as opposed to present with low visibility) defaults to visible=1.0 here,
        // faithfully matching legacy's own `P[k]?.visibility ?? 1` — real ML Kit/MediaPipe output
        // always has all landmarks present, just sometimes with a low score, so that's the case
        // this test actually simulates.
        val engine = MovementEngine("curl")
        val blind = mapOf(
            Joint.SHOULDER to Pt(0.0, -1.0, 1.0),
            Joint.ELBOW to Pt(0.0, 0.0, 0.1),
            Joint.WRIST to Pt(0.0, 1.0, 0.1),
            Joint.HIP to Pt(0.0, -2.0, 1.0),
        )
        val r = engine.step(frame(blind, 0))
        assertFalse(r.visible)
        assertTrue(r.missing.containsAll(listOf(Joint.ELBOW, Joint.WRIST)))
    }

    @Test
    fun `bodyweight and loaded exercises are told apart correctly by loadRatio`() {
        assertTrue(EXERCISES.getValue("pushup").loadRatio == 0.0)
        assertTrue(EXERCISES.getValue("squat").loadRatio > 0.0)
    }
}
