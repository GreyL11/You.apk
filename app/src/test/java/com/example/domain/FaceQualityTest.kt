package com.example.domain

import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

/**
 * These test the REFUSALS above all else. A quality gate that accepts everything is not a gate, and
 * the failure mode of this whole feature is a confident conclusion drawn from two photos taken under
 * different lamps. So most of what follows builds a deliberately bad capture and asserts that the
 * gate says no.
 *
 * Ported from `test_face.mjs`'s quality section.
 */
class FaceQualityTest {
    private val w = 64
    private val h = 64
    private val frameW = 640
    private val frameH = 480

    /** Alternating pixels: maximum second-derivative energy — as sharp as an image can be. */
    private fun checkerboard() = DoubleArray(w * h) { i ->
        val x = i % w
        val y = i / w
        if ((x + y) % 2 == 0) 0.35 else 0.65
    }

    /** A smooth ramp: real content, no edges. This is what a badly out-of-focus face looks like. */
    private fun softGradient() = DoubleArray(w * h) { i -> 0.25 + 0.5 * (i % w) / w.toDouble() }

    /** A gentle sine — some structure, still soft. */
    private fun blurredTexture() = DoubleArray(w * h) { i ->
        val x = i % w
        val y = i / w
        0.5 + 0.05 * sin(x / 9.0) * sin(y / 9.0)
    }

    // ── sharpness ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a sharp image clears the gate the threshold was written for`() {
        val s = FaceQuality.sharpness(checkerboard(), w, h)
        // The whole point of the port: value and threshold now share one definition, so a sharp
        // image actually passes instead of scoring ~0.005 against a 0.15 floor.
        assertTrue("sharp value was ${s.value}", s.value > FaceQuality.LIMITS.SHARPNESS_MIN)
        assertNull(s.reason)
        assertEquals(1.0, s.score, 0.001)
    }

    @Test
    fun `a soft image is still rejected, and says why`() {
        val s = FaceQuality.sharpness(softGradient(), w, h)
        assertTrue("soft value was ${s.value}", s.value < FaceQuality.LIMITS.SHARPNESS_MIN)
        assertEquals("hold still — that came out blurry", s.reason)
    }

    @Test
    fun `a blurred texture sits below the gate too`() {
        val s = FaceQuality.sharpness(blurredTexture(), w, h)
        assertTrue("blurred value was ${s.value}", s.value < FaceQuality.LIMITS.SHARPNESS_MIN)
    }

    @Test
    fun `no image is not a zero-sharpness reading`() {
        assertEquals("no image", FaceQuality.sharpness(null, w, h).reason)
        assertEquals("no image", FaceQuality.sharpness(DoubleArray(4), 2, 2).reason)
    }

    @Test
    fun `the sharpness scale is a named constant, so recalibrating it does not move the threshold`() {
        // The legacy calls this scaling empirical and expects it to move with real captures. The
        // threshold is a separate decision and must not have to move with it.
        assertEquals(8.0, FaceQuality.SHARPNESS_SCALE, 0.001)
        assertEquals(0.15, FaceQuality.LIMITS.SHARPNESS_MIN, 0.001)
    }

    // ── exposure ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `exposure names the specific problem`() {
        assertEquals("too dark to read", FaceQuality.exposure(DoubleArray(100) { 0.10 }).reason)
        assertEquals("too bright to read", FaceQuality.exposure(DoubleArray(100) { 0.90 }).reason)
        assertNull(FaceQuality.exposure(DoubleArray(100) { 0.50 }).reason)
    }

    @Test
    fun `clipping is caught before brightness, because clipped pixels carry nothing`() {
        // Mean sits in the usable band, but a tenth of it is crushed to black.
        val luma = DoubleArray(100) { i -> if (i < 10) 0.0 else 0.55 }
        val e = FaceQuality.exposure(luma)
        assertEquals("too much of that is pure black or pure white", e.reason)
        assertEquals(0.10, e.clipFraction, 0.001)
    }

    // ── balance ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `side lighting is caught before it can be reported as facial asymmetry`() {
        assertTrue("even light passes", FaceQuality.balance(0.5, 0.5).score > 0.99)
        val lopsided = FaceQuality.balance(0.72, 0.28)
        assertTrue(
            "one-sided light must be refused, got ${lopsided.score}",
            lopsided.score < 0.3,
        )
        assertEquals("the light is coming from one side", lopsided.reason)
    }

    @Test
    fun `an unsampled side passes rather than penalising a check that has not run`() {
        assertEquals(1.0, FaceQuality.balance(null, 0.5).score, 0.001)
        assertNull(FaceQuality.balance(null, 0.5).reason)
    }

    // ── lighting match ───────────────────────────────────────────────────────────────────────

    @Test
    fun `lighting is compared against the person, and stays quiet until it knows them`() {
        assertFalse("no history means no opinion", FaceQuality.lightingMatch(0.5, emptyList()).known)
        assertEquals(1.0, FaceQuality.lightingMatch(0.5, listOf(0.5, 0.5)).score, 0.001)
        assertFalse("two samples is not a habit", FaceQuality.lightingMatch(0.5, listOf(0.5, 0.5)).known)

        val usual = listOf(0.50, 0.52, 0.48, 0.51, 0.49, 0.50)
        assertTrue("a typical capture matches", FaceQuality.lightingMatch(0.50, usual).score > 0.9)
        val odd = FaceQuality.lightingMatch(0.75, usual)
        assertTrue("a very different lamp should be flagged, got ${odd.score}", odd.score < 0.3)
        assertEquals("the light is different from your usual check-ins", odd.reason)
    }

    @Test
    fun `a person whose captures are near-identical does not get flagged for a trivial difference`() {
        // Standard deviation near zero would make any difference read as enormous if divided by it.
        val rigid = listOf(0.500, 0.500, 0.501, 0.499, 0.500, 0.500)
        assertTrue("half a percent is not a different room", FaceQuality.lightingMatch(0.505, rigid).score > 0.8)
        assertTrue("but a real change still trips it", FaceQuality.lightingMatch(0.80, rigid).score < 0.3)
    }

    // ── framing and pose (need real geometry, so a synthetic face) ──────────────────────────

    @Test
    fun `a well-framed face passes framing`() {
        val f = FaceQuality.framing(syntheticFace(eyeGap = 0.22), frameW, frameH)
        assertTrue("expected a clean pass, got ${f.score}", f.score > 0.8)
        assertNull(f.reason)
    }

    @Test
    fun `too far away and too close are both rejected, and say which`() {
        val far = FaceQuality.framing(syntheticFace(eyeGap = 0.05), frameW, frameH)
        assertTrue("distant face should score low, got ${far.score}", far.score < 0.6)
        assertTrue(far.reason!!.contains("too far"))

        val near = FaceQuality.framing(syntheticFace(eyeGap = 0.60, faceH = 0.2), frameW, frameH)
        assertTrue("very close face should score low, got ${near.score}", near.score < 0.6)
        assertNotNull("and should say why", near.reason)
    }

    @Test
    fun `a cropped face scores zero outright rather than being averaged down`() {
        val f = FaceQuality.framing(syntheticFace(cx = 0.02), frameW, frameH)
        assertEquals("a region off the edge is a different piece of skin, not a worse one", 0.0, f.score, 0.0)
        assertTrue(f.clipped.isNotEmpty())
        assertTrue(f.reason!!.contains("outside the frame"))
    }

    @Test
    fun `excessive head rotation is refused, with the right instruction`() {
        assertTrue("facing forward passes", FaceQuality.pose(syntheticFace(noseShift = 0.0)).score > 0.9)
        val turned = FaceQuality.pose(syntheticFace(noseShift = 0.5))
        assertTrue("a turned head should be refused, got ${turned.score}", turned.score < 0.4)
        assertEquals("turn to face the camera", turned.reason)
        val tilted = FaceQuality.pose(syntheticFace(roll = 0.5))
        assertTrue("and so should a strongly tilted one", tilted.score < 0.5)
    }

    // ── the combined verdict ─────────────────────────────────────────────────────────────────

    @Test
    fun `one bad component sinks the capture -- good scores cannot average it away`() {
        val v = FaceQuality.assess(
            mapOf<String, FaceQuality.CheckResult>(
                "framing" to FaceQuality.Sharpness(1.0, 0.0, null),
                "pose" to FaceQuality.Sharpness(1.0, 0.0, null),
                "sharpness" to FaceQuality.Sharpness(1.0, 0.0, null),
                "exposure" to FaceQuality.Sharpness(1.0, 0.0, null),
                "balance" to FaceQuality.Balance(0.05, 0.9, "the light is coming from one side"),
            ),
        )
        assertTrue("weighted minimum, not mean -- got ${v.overall}", v.overall < FaceQuality.LIMITS.ACCEPT_MIN)
        assertFalse(v.accepted)
        assertEquals(listOf("the light is coming from one side"), v.warnings)
    }

    @Test
    fun `a good capture is accepted and marked trustworthy`() {
        val v = FaceQuality.assess(
            mapOf(
                "framing" to FaceQuality.Sharpness(0.95, 0.0, null),
                "pose" to FaceQuality.Sharpness(0.95, 0.0, null),
                "sharpness" to FaceQuality.Sharpness(0.9, 0.0, null),
                "exposure" to FaceQuality.Sharpness(0.95, 0.0, null),
                "balance" to FaceQuality.Sharpness(0.95, 0.0, null),
            ),
        )
        assertTrue("expected a clean pass, got ${v.overall}", v.accepted && v.trustworthy)
        assertEquals(emptyList<String>(), v.warnings)
    }

    @Test
    fun `storable and trustworthy are different bars`() {
        // Good enough to keep as evidence, not good enough to base a strong claim on.
        val v = FaceQuality.assess(
            mapOf(
                "framing" to FaceQuality.Sharpness(0.75, 0.0, null),
                "pose" to FaceQuality.Sharpness(0.72, 0.0, null),
                "sharpness" to FaceQuality.Sharpness(0.7, 0.0, null),
                "exposure" to FaceQuality.Sharpness(0.75, 0.0, null),
                "balance" to FaceQuality.Sharpness(0.74, 0.0, null),
            ),
        )
        assertTrue(v.accepted)
        assertFalse("the trend layer must be able to tell these apart", v.trustworthy)
    }

    @Test
    fun `nothing to assess is refused rather than defaulted to fine`() {
        val v = FaceQuality.assess(emptyMap())
        assertFalse(v.accepted)
        assertEquals(0.0, v.overall, 0.0)
    }

    // ── live guidance ────────────────────────────────────────────────────────────────────────

    @Test
    fun `guidance gives one instruction at a time, in the order that unblocks things`() {
        // Everything wrong at once. Framing must win: there is no point telling someone to hold
        // still while they are out of shot, and moving will change the lighting anyway.
        val g = FaceQuality.guide(
            mapOf(
                "framing" to FaceQuality.Sharpness(0.0, 0.0, "too far away"),
                "pose" to FaceQuality.Sharpness(0.0, 0.0, "turn to face the camera"),
                "exposure" to FaceQuality.Sharpness(0.0, 0.0, "too dark in here"),
                "balance" to FaceQuality.Sharpness(0.0, 0.0, "the light is coming from one side"),
            ),
        )
        assertEquals("too far away", g.instruction)
        assertEquals("framing", g.blocking)

        // With framing fixed, the next blocker surfaces rather than the whole list at once.
        val g2 = FaceQuality.guide(
            mapOf(
                "framing" to FaceQuality.Sharpness(1.0, 0.0, null),
                "pose" to FaceQuality.Sharpness(0.0, 0.0, "turn to face the camera"),
                "exposure" to FaceQuality.Sharpness(0.0, 0.0, "too dark in here"),
            ),
        )
        assertEquals("turn to face the camera", g2.instruction)

        val g3 = FaceQuality.guide(
            mapOf(
                "framing" to FaceQuality.Sharpness(1.0, 0.0, null),
                "pose" to FaceQuality.Sharpness(1.0, 0.0, null),
            ),
        )
        assertNull(g3.instruction)
    }

    // ── steadiness ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `steadiness needs consecutive good frames, and any bad one starts it over`() {
        var s: FaceQuality.Steadiness? = null
        repeat(FaceQuality.STEADY_FRAMES - 1) { s = FaceQuality.steadiness(s, true) }
        assertFalse("nearly there is not there", s!!.ready)

        s = FaceQuality.steadiness(s, false)
        assertEquals("half-steady is not steady -- it resets, it does not decrement", 0, s!!.frames)

        repeat(FaceQuality.STEADY_FRAMES) { s = FaceQuality.steadiness(s, true) }
        assertTrue(s!!.ready)
    }

    @Test
    fun `a single lucky frame in a shaky hold never reaches ready`() {
        var s: FaceQuality.Steadiness? = null
        for (i in 0 until 60) s = FaceQuality.steadiness(s, i % 3 == 0)
        assertFalse("one good frame in three is not holding still", s!!.ready)
    }
}
