package com.example.domain

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

/**
 * These test the REFUSALS above all else. A quality gate that accepts everything is not a gate, and
 * the failure mode of this whole feature is a confident conclusion drawn from two photos taken under
 * different lamps. So most of this asserts that the geometry layer says no when it should.
 *
 * Ported from `test_face.mjs`'s geometry section, against [syntheticFace].
 */
class GeometryTest {
    @Test
    fun `alignment measures eye distance, and scales with camera distance`() {
        val near = Geometry.alignment(syntheticFace(eyeGap = 0.30))!!
        val far = Geometry.alignment(syntheticFace(eyeGap = 0.15))!!
        assertTrue(abs(near.scale - 0.30) < 1e-9)
        assertTrue(abs(far.scale - 0.15) < 1e-9)
        assertTrue("closer face reads larger -- this is the distance proxy", near.scale > far.scale)
    }

    @Test
    fun `a tilted head is measured as roll, not as a moved face`() {
        val a = Geometry.alignment(syntheticFace(roll = 0.3))!!
        assertTrue(abs(a.roll - 0.3) < 1e-6)
        // Rolling must not change the apparent size, or tilting would read as leaning in.
        assertTrue(abs(a.scale - 0.20) < 1e-9)
    }

    @Test
    fun `regions scale with the face, so the same patch of skin is measured at any distance`() {
        val near = Geometry.regions(syntheticFace(eyeGap = 0.30))!!.asMap()
        val far = Geometry.regions(syntheticFace(eyeGap = 0.15))!!.asMap()
        // Every box should be exactly half the size when the face is half as wide.
        for (k in near.keys) {
            assertTrue("$k did not scale with the face", abs(near[k]!!.half / far[k]!!.half - 2) < 1e-6)
        }
    }

    @Test
    fun `regions rotate with the head instead of sliding off the cheek`() {
        val level = Geometry.regions(syntheticFace(roll = 0.0))!!
        val tilted = Geometry.regions(syntheticFace(roll = Math.PI / 2))!!
        // A quarter turn should swap the axes the cheeks are separated along.
        val levelDx = abs(level.leftCheek.cx - level.rightCheek.cx)
        val tiltedDy = abs(tilted.leftCheek.cy - tilted.rightCheek.cy)
        assertTrue("cheeks separate horizontally on a level head", levelDx > 0.05)
        assertTrue("and vertically on a head turned 90 degrees", tiltedDy > 0.05)
    }

    @Test
    fun `the selfie camera mirror swaps left and right exactly once`() {
        val normal = Geometry.regions(syntheticFace(), mirrored = false)!!
        val mirrored = Geometry.regions(syntheticFace(), mirrored = true)!!
        // The subject's left cheek must appear on the other side of the frame when mirrored --
        // getting this wrong would report one cheek's readings as the other's, forever.
        assertTrue(abs(normal.leftCheek.cx - mirrored.rightCheek.cx) < 1e-9)
        assertTrue(abs(normal.rightCheek.cx - mirrored.leftCheek.cx) < 1e-9)
    }

    @Test
    fun `head pose reads yaw from the nose, scale-free`() {
        assertTrue("forward is zero", abs(Geometry.headPose(syntheticFace(noseShift = 0.0))!!.yaw) < 1e-9)
        val turned = Geometry.headPose(syntheticFace(noseShift = 0.3))!!
        assertTrue("a turned head should read as yaw, got ${turned.yaw}", turned.yaw > 0.25)
        // Same turn, different camera distance -- yaw must not change.
        val near = Geometry.headPose(syntheticFace(noseShift = 0.3, eyeGap = 0.30))!!
        val far = Geometry.headPose(syntheticFace(noseShift = 0.3, eyeGap = 0.12))!!
        assertTrue("yaw must not depend on distance", abs(near.yaw - far.yaw) < 1e-6)
    }

    @Test
    fun `the transformation matrix is preferred and agrees on an identity pose`() {
        val identity = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        val p = Geometry.poseFromMatrix(identity)!!
        assertTrue(abs(p.yaw) < 1e-9 && abs(p.pitch!!) < 1e-9 && abs(p.roll) < 1e-9)
        assertNull("absent matrix falls back rather than inventing", Geometry.poseFromMatrix(null))
    }

    @Test
    fun `a box pushed off the edge of the frame is reported as clipped, not silently shrunk`() {
        val r = Geometry.regions(syntheticFace(cx = 0.03))!!
        val px = Geometry.toPixels(r.rightCheek, 640, 480)
        assertTrue("must be flagged so the caller drops it", px.clipped)
        val inside = Geometry.toPixels(Geometry.regions(syntheticFace())!!.forehead, 640, 480)
        assertFalse(inside.clipped)
    }

    @Test
    fun `anatomy returns null rather than a partial region set when a ring is missing a vertex`() {
        // Fewer than 478 landmarks means every ring lookup misses at least one vertex. anatomy()'s
        // own polygon math needs a real facial structure (distinct lips, brows, oval) to be
        // meaningful, which only real captured landmarks provide -- the synthetic face above is
        // deliberately degenerate everywhere except the five points the SQUARE-region functions
        // read, and is not exercised against anatomy() for that reason, matching the legacy test
        // suite's own choice.
        val tooShort = List(50) { Geometry.Point(0.5, 0.5) }
        assertNull(Geometry.anatomy(tooShort))
    }
}
