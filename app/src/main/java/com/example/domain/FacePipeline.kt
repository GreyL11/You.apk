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
        // Every limit here belongs to FaceQuality, which also owns the metric each one is compared
        // against. Two copies of a threshold is how the focus gate ended up being checked against a
        // number computed a different way — a limit is only meaningful beside its own definition.
        val l = FaceQuality.LIMITS
        if (faceSizeFraction < l.FACE_MIN) return FaceCaptureIssue.TOO_FAR
        if (faceSizeFraction > l.FACE_MAX) return FaceCaptureIssue.TOO_CLOSE
        if (Math.abs(yawRad) > l.YAW_MAX || Math.abs(pitchRad) > l.PITCH_MAX ||
            Math.abs(rollRad) > l.ROLL_MAX
        ) return FaceCaptureIssue.LOOK_STRAIGHT
        if (sharpnessVariance < l.SHARPNESS_MIN) return FaceCaptureIssue.HOLD_STILL
        if (luma < l.LUMA_MIN) return FaceCaptureIssue.TOO_DARK
        if (luma > l.LUMA_MAX) return FaceCaptureIssue.TOO_BRIGHT
        if (exposureClipFraction > l.CLIP_MAX) return FaceCaptureIssue.TOO_BRIGHT
        return null
    }
}
