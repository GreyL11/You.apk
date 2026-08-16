package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class FacePipelineTest {
    @Test
    fun `face pipeline thresholds check out`() {
        // Valid face
        assertTrue(FacePipeline.checkQuality(0.20, 0.1, 0.1, 0.1, 0.20, 0.01, 0.5))
        // Invalid size
        assertFalse(FacePipeline.checkQuality(0.10, 0.1, 0.1, 0.1, 0.20, 0.01, 0.5))
        assertFalse(FacePipeline.checkQuality(0.43, 0.1, 0.1, 0.1, 0.20, 0.01, 0.5))
        // Invalid yaw
        assertFalse(FacePipeline.checkQuality(0.20, 0.3, 0.1, 0.1, 0.20, 0.01, 0.5))
        // Invalid luma
        assertFalse(FacePipeline.checkQuality(0.20, 0.1, 0.1, 0.1, 0.20, 0.01, 0.1))
        assertFalse(FacePipeline.checkQuality(0.20, 0.1, 0.1, 0.1, 0.20, 0.01, 0.9))
    }
}
