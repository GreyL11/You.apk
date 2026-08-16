package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class ExercisesTest {
    @Test
    fun `movement-engine constants are exact`() {
        assertEquals(12.0, HYSTERESIS, 0.001)
        assertEquals(500L, MIN_REP_MS)
        assertEquals(3, HOLD_FRAMES)
        assertEquals(0.5, MIN_JOINT_VIS, 0.001)
        assertEquals(25.0, MIN_RANGE_DEG, 0.001)
    }

    @Test
    fun `catalogue has all 28 legacy exercises, not a placeholder subset`() {
        assertEquals(28, EXERCISES.size)
        for (id in listOf(
            "squat", "rdl", "lunge", "bench", "inclineBench", "declineBench", "dbBench", "inclineDbPress",
            "chestDip", "pushup", "deadlift", "row", "cableRow", "straightArmPulldown", "latPulldown",
            "ohp", "lateralRaise", "frontRaise", "rearDeltRaise", "cableLateralRaise", "cableFrontRaise",
            "curl", "hammerCurl", "cableCurl", "pushdown", "skullcrusher", "overheadExtension", "dip",
        )) {
            assertTrue("missing exercise: $id", EXERCISES.containsKey(id))
        }
    }

    @Test
    fun `every exercise belongs to a real group and has at least one fault`() {
        for ((id, ex) in EXERCISES) {
            assertTrue("$id has an unknown group ${ex.group}", GROUPS.contains(ex.group))
            assertTrue("$id has no fault rules", ex.faults.isNotEmpty())
        }
    }

    @Test
    fun `bodyweight exercises are exactly the three legacy marks as loadRatio 0`() {
        val bodyweight = EXERCISES.filterValues { it.loadRatio == 0.0 }.keys
        assertEquals(setOf("chestDip", "pushup", "dip"), bodyweight)
    }

    @Test
    fun `squat thresholds match legacy exactly`() {
        val squat = EXERCISES.getValue("squat")
        assertEquals(168.0, squat.repStart, 0.001)
        assertEquals(95.0, squat.repEnd, 0.001)
        assertEquals(Joint.KNEE, squat.primary)
        assertEquals(160.0, squat.thresholds.getValue("lockout"), 0.001)
        assertEquals(0.82, squat.thresholds.getValue("valgusRatio"), 0.001)
        assertTrue(squat.faults.any { it.id == "valgus" && it.view == "front" })
    }

    @Test
    fun `safety faults are stamped correctly and never invented for an exercise that has none`() {
        assertTrue(isSafetyFault("squat", "valgus"))
        assertTrue(isSafetyFault("deadlift", "barDrift"))
        assertFalse(isSafetyFault("rearDeltRaise", "heave")) // deliberately not a safety fault, per legacy comment
        val squatValgus = EXERCISES.getValue("squat").faults.first { it.id == "valgus" }
        assertEquals("safety", squatValgus.severity)
        val benchWrist = EXERCISES.getValue("bench").faults.first { it.id == "wrist" }
        assertEquals("safety", benchWrist.severity)
        val benchLockout = EXERCISES.getValue("bench").faults.first { it.id == "lockout" }
        assertEquals("efficiency", benchLockout.severity)
    }

    @Test
    fun `factory-built exercises (bench, raise, curl variants) carry their own overridden thresholds`() {
        assertEquals(70.0, EXERCISES.getValue("inclineBench").thresholds.getValue("flare"), 0.001)
        assertEquals(82.0, EXERCISES.getValue("declineBench").thresholds.getValue("flare"), 0.001)
        assertEquals(100.0, EXERCISES.getValue("rearDeltRaise").thresholds.getValue("maxHeight"), 0.001)
        assertEquals("dumbbell", EXERCISES.getValue("dbBench").equipment)
        assertEquals("cable", EXERCISES.getValue("cableCurl").equipment)
    }

    @Test
    fun `calibrate refuses too little data and too little range`() {
        assertNull(calibrate("squat", (1..29).map { CalibrationSample(it.toDouble()) })) // < 30 samples
        assertNull(calibrate("squat", (1..40).map { CalibrationSample(100.0 + it * 0.01) })) // no real range
    }

    @Test
    fun `calibrate learns real endpoints from a wide-enough recording`() {
        val samples = (0..99).map { CalibrationSample(90.0 + it) } // 90..189, wide range
        val patch = calibrate("squat", samples)
        assertNotNull(patch)
        assertTrue(patch!!.repEnd < patch.repStart) // squat's arc goes high->low (168 -> 95)
    }

    @Test
    fun `cameraCheck abstains rather than guessing on missing or unreliable landmarks`() {
        assertNull(cameraCheck(null, Pt(0.5, 0.5, 1.0), Pt(0.4, 0.9, 1.0), Pt(0.6, 0.9, 1.0), "side"))
        assertNull(cameraCheck(Pt(0.4, 0.5, 0.1), Pt(0.6, 0.5, 0.1), Pt(0.4, 0.9, 1.0), Pt(0.6, 0.9, 1.0), "side")) // low visibility
    }

    @Test
    fun `cameraCheck tells side-on from front-on by shoulder spread`() {
        // Shoulders nearly overlapping relative to torso height -> side-on
        val side = cameraCheck(Pt(0.50, 0.3, 1.0), Pt(0.52, 0.3, 1.0), Pt(0.50, 0.6, 1.0), Pt(0.52, 0.6, 1.0), "side")
        assertNotNull(side)
        assertEquals("side", side!!.view)
        assertTrue(side.ok)
        // Shoulders far apart relative to torso height -> front-on
        val front = cameraCheck(Pt(0.30, 0.3, 1.0), Pt(0.70, 0.3, 1.0), Pt(0.45, 0.6, 1.0), Pt(0.55, 0.6, 1.0), "side")
        assertNotNull(front)
        assertEquals("front", front!!.view)
        assertFalse(front.ok) // wanted side-on, got front-on
    }
}
