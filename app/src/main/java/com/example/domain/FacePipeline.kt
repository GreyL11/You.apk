package com.example.domain

object FacePipeline {
    fun checkQuality(
        faceSizeFraction: Double,
        yawRad: Double,
        pitchRad: Double,
        rollRad: Double,
        sharpnessVariance: Double,
        exposureClipFraction: Double,
        luma: Double
    ): Boolean {
        if (faceSizeFraction < 0.11 || faceSizeFraction > 0.42) return false
        if (Math.abs(yawRad) > 0.25) return false
        if (Math.abs(pitchRad) > 0.20) return false
        if (Math.abs(rollRad) > 0.30) return false
        if (sharpnessVariance < 0.15) return false
        if (exposureClipFraction > 0.02) return false
        if (luma < 0.22 || luma > 0.82) return false
        return true
    }
}
