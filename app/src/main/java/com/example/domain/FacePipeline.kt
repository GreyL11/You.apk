package com.example.domain

/** Single-face capture-quality issues FacePipeline can detect from real geometry/image metrics.
 *  NO_FACE/MULTIPLE_FACES/OFF_CENTER are diagnosed one level up (FaceScan.diagnose), since
 *  FacePipeline only ever reasoned about one already-located face. */
enum class FaceCaptureIssue {
    NO_FACE, MULTIPLE_FACES, OFF_CENTER, TOO_FAR, TOO_CLOSE, LOOK_STRAIGHT, HOLD_STILL, TOO_DARK, TOO_BRIGHT
}

object FacePipeline {
    /** checkQuality is the original tested contract (FacePipelineTest) — now a thin wrapper over
     *  diagnose() so the guided-scan UI and this boolean gate can never disagree. */
    fun checkQuality(
        faceSizeFraction: Double,
        yawRad: Double,
        pitchRad: Double,
        rollRad: Double,
        sharpnessVariance: Double,
        exposureClipFraction: Double,
        luma: Double
    ): Boolean = diagnose(faceSizeFraction, yawRad, pitchRad, rollRad, sharpnessVariance, exposureClipFraction, luma) == null

    fun diagnose(
        faceSizeFraction: Double,
        yawRad: Double,
        pitchRad: Double,
        rollRad: Double,
        sharpnessVariance: Double,
        exposureClipFraction: Double,
        luma: Double
    ): FaceCaptureIssue? {
        if (faceSizeFraction < 0.11) return FaceCaptureIssue.TOO_FAR
        if (faceSizeFraction > 0.42) return FaceCaptureIssue.TOO_CLOSE
        if (Math.abs(yawRad) > 0.25 || Math.abs(pitchRad) > 0.20 || Math.abs(rollRad) > 0.30) return FaceCaptureIssue.LOOK_STRAIGHT
        if (sharpnessVariance < 0.15) return FaceCaptureIssue.HOLD_STILL
        if (luma < 0.22) return FaceCaptureIssue.TOO_DARK
        if (luma > 0.82) return FaceCaptureIssue.TOO_BRIGHT
        if (exposureClipFraction > 0.02) return FaceCaptureIssue.TOO_BRIGHT
        return null
    }
}
