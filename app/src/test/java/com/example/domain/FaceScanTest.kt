package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class FaceScanTest {
    private val goodMetrics = FaceMetrics(
        faceSizeFraction = 0.20, centerXFraction = 0.5, centerYFraction = 0.5,
        yawRad = 0.1, pitchRad = 0.1, rollRad = 0.1,
        sharpnessVariance = 0.20, exposureClipFraction = 0.01, luma = 0.5,
    )

    @Test
    fun `no face or zero faces is diagnosed as NO_FACE, not a fabricated pass`() {
        assertEquals(FaceCaptureIssue.NO_FACE, FaceScan.diagnose(0, null))
        assertEquals(FaceCaptureIssue.NO_FACE, FaceScan.diagnose(1, null))
    }

    @Test
    fun `more than one face is diagnosed as MULTIPLE_FACES before any geometry check`() {
        assertEquals(FaceCaptureIssue.MULTIPLE_FACES, FaceScan.diagnose(2, goodMetrics))
    }

    @Test
    fun `an off-center face is caught even when every other metric is otherwise good`() {
        val offCenter = goodMetrics.copy(centerXFraction = 0.9)
        assertEquals(FaceCaptureIssue.OFF_CENTER, FaceScan.diagnose(1, offCenter))
    }

    @Test
    fun `a single well-framed centered face with good metrics has no issue`() {
        assertNull(FaceScan.diagnose(1, goodMetrics))
    }

    @Test
    fun `geometry and image-quality failures delegate to FacePipeline's own thresholds`() {
        assertEquals(FaceCaptureIssue.TOO_FAR, FaceScan.diagnose(1, goodMetrics.copy(faceSizeFraction = 0.05)))
        assertEquals(FaceCaptureIssue.TOO_CLOSE, FaceScan.diagnose(1, goodMetrics.copy(faceSizeFraction = 0.50)))
        assertEquals(FaceCaptureIssue.LOOK_STRAIGHT, FaceScan.diagnose(1, goodMetrics.copy(yawRad = 0.5)))
        assertEquals(FaceCaptureIssue.HOLD_STILL, FaceScan.diagnose(1, goodMetrics.copy(sharpnessVariance = 0.01)))
        assertEquals(FaceCaptureIssue.TOO_DARK, FaceScan.diagnose(1, goodMetrics.copy(luma = 0.05)))
        assertEquals(FaceCaptureIssue.TOO_BRIGHT, FaceScan.diagnose(1, goodMetrics.copy(luma = 0.95)))
    }

    @Test
    fun `toJson then fromJson round-trips every real measured field`() {
        val json = FaceScan.toJson("2026-08-16T10:00:00", valid = true, metrics = goodMetrics)
        val record = FaceScan.fromJson(id = 7, data = json)

        assertEquals(7, record.id)
        assertEquals("2026-08-16T10:00:00", record.at)
        assertTrue(record.valid)
        assertEquals(goodMetrics.faceSizeFraction, record.metrics.faceSizeFraction, 0.0001)
        assertEquals(goodMetrics.yawRad, record.metrics.yawRad, 0.0001)
        assertEquals(goodMetrics.sharpnessVariance, record.metrics.sharpnessVariance, 0.0001)
        assertEquals(goodMetrics.luma, record.metrics.luma, 0.0001)
    }
}
