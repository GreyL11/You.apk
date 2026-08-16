package com.example.domain

import org.json.JSONObject

/** Real, measured metrics from one analyzed camera frame — every field comes straight off ML Kit's
 *  Face (bounding box, head Euler angles) or a direct sample of the Y-plane (luma/sharpness/
 *  exposure). Nothing here is inferred or invented. */
data class FaceMetrics(
    val faceSizeFraction: Double,
    val centerXFraction: Double,
    val centerYFraction: Double,
    val yawRad: Double,
    val pitchRad: Double,
    val rollRad: Double,
    val sharpnessVariance: Double,
    val exposureClipFraction: Double,
    val luma: Double,
)

/** A future real skin-analysis model plugs in here as a new `Available` case — the result screen
 *  already renders against this interface, so no screen change would be needed to light it up. */
sealed interface SkinAnalysis {
    object NotAvailable : SkinAnalysis
    data class Available(val summary: String) : SkinAnalysis
}

object FaceScan {
    private const val CENTER_TOLERANCE = 0.18
    const val HOLD_FRAMES_TO_CAPTURE = 12

    /** Face-count and framing checks that sit above FacePipeline's single-face geometry/image
     *  checks — the two layers together cover everything the guided scan needs to gate on. */
    fun diagnose(faceCount: Int, metrics: FaceMetrics?): FaceCaptureIssue? {
        if (faceCount == 0 || metrics == null) return FaceCaptureIssue.NO_FACE
        if (faceCount > 1) return FaceCaptureIssue.MULTIPLE_FACES
        if (Math.abs(metrics.centerXFraction - 0.5) > CENTER_TOLERANCE || Math.abs(metrics.centerYFraction - 0.5) > CENTER_TOLERANCE) {
            return FaceCaptureIssue.OFF_CENTER
        }
        return FacePipeline.diagnose(
            metrics.faceSizeFraction, metrics.yawRad, metrics.pitchRad, metrics.rollRad,
            metrics.sharpnessVariance, metrics.exposureClipFraction, metrics.luma,
        )
    }

    fun guidance(issue: FaceCaptureIssue?): String = when (issue) {
        null -> "Hold still…"
        FaceCaptureIssue.NO_FACE -> "Position your face in the frame"
        FaceCaptureIssue.MULTIPLE_FACES -> "Make sure only one face is in frame"
        FaceCaptureIssue.OFF_CENTER -> "Center your face in the frame"
        FaceCaptureIssue.TOO_FAR -> "Move closer"
        FaceCaptureIssue.TOO_CLOSE -> "Move back"
        FaceCaptureIssue.LOOK_STRAIGHT -> "Look straight ahead"
        FaceCaptureIssue.HOLD_STILL -> "Hold still — image isn't sharp"
        FaceCaptureIssue.TOO_DARK -> "Move to better lighting"
        FaceCaptureIssue.TOO_BRIGHT -> "Reduce direct light on your face"
    }

    data class Record(val id: Int, val at: String, val valid: Boolean, val metrics: FaceMetrics)

    /** FaceCapture.data is a JSON blob (existing entity — see Entities.kt) so adding this scan
     *  needed no new table and no schema/version bump. */
    fun toJson(at: String, valid: Boolean, metrics: FaceMetrics): String = JSONObject().apply {
        put("at", at)
        put("valid", valid)
        put("faceSizeFraction", metrics.faceSizeFraction)
        put("centerXFraction", metrics.centerXFraction)
        put("centerYFraction", metrics.centerYFraction)
        put("yawRad", metrics.yawRad)
        put("pitchRad", metrics.pitchRad)
        put("rollRad", metrics.rollRad)
        put("sharpnessVariance", metrics.sharpnessVariance)
        put("exposureClipFraction", metrics.exposureClipFraction)
        put("luma", metrics.luma)
    }.toString()

    fun fromJson(id: Int, data: String): Record {
        val o = JSONObject(data)
        return Record(
            id = id,
            at = o.optString("at", ""),
            valid = o.optBoolean("valid", false),
            metrics = FaceMetrics(
                faceSizeFraction = o.optDouble("faceSizeFraction", 0.0),
                centerXFraction = o.optDouble("centerXFraction", 0.5),
                centerYFraction = o.optDouble("centerYFraction", 0.5),
                yawRad = o.optDouble("yawRad", 0.0),
                pitchRad = o.optDouble("pitchRad", 0.0),
                rollRad = o.optDouble("rollRad", 0.0),
                sharpnessVariance = o.optDouble("sharpnessVariance", 0.0),
                exposureClipFraction = o.optDouble("exposureClipFraction", 0.0),
                luma = o.optDouble("luma", 0.0),
            ),
        )
    }
}
