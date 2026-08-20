package com.example.domain

import kotlin.math.cos
import kotlin.math.sin

/**
 * A synthetic face and a synthetic luminance buffer, shared by [GeometryTest] and
 * [FaceQualityTest]. Ported from the `face()`/`luma()` helpers at the top of the legacy
 * `test_face.mjs` — one shape, reused across every geometry and quality check, exactly as the
 * legacy test file does.
 *
 * Only the landmarks the modules actually read are placed meaningfully; the rest exist so indices
 * resolve into a 478-length list. `eyeGap` is in normalized frame width, which is exactly the
 * quantity the distance gate judges.
 */
fun syntheticFace(
    cx: Double = 0.5,
    cy: Double = 0.45,
    eyeGap: Double = 0.20,
    roll: Double = 0.0,
    noseShift: Double = 0.0,
    faceH: Double = 0.55,
): List<Geometry.Point> {
    val lm = MutableList(478) { Geometry.Point(cx, cy) }
    val ux = cos(roll)
    val uy = sin(roll)
    val half = eyeGap / 2
    // 263 = subject's left outer eye corner, 33 = right. From FaceLandmarker's own sets.
    lm[263] = Geometry.Point(cx + ux * half, cy + uy * half)
    lm[33] = Geometry.Point(cx - ux * half, cy - uy * half)
    // Nose tip: on the eye axis when facing forward, slid along it by noseShift (in eye-gaps).
    lm[1] = Geometry.Point(cx + ux * noseShift * eyeGap, cy + uy * noseShift * eyeGap)
    // Face vertical extremes, perpendicular to the eye axis.
    val dx = -uy
    val dy = ux
    lm[10] = Geometry.Point(cx - dx * faceH * 0.35, cy - dy * faceH * 0.35)
    lm[152] = Geometry.Point(cx + dx * faceH * 0.65, cy + dy * faceH * 0.65)
    return lm
}

/** A luminance buffer of constant value, optionally with sharp edges to make it "in focus". */
fun syntheticLuma(
    w: Int,
    h: Int,
    value: Double,
    edges: Boolean = false,
    clipDark: Int = 0,
    clipBright: Int = 0,
): DoubleArray {
    val a = DoubleArray(w * h) { value }
    if (edges) {
        var i = 0
        while (i < a.size) {
            a[i] = minOf(1.0, value + 0.25)
            i += 2
        }
    }
    for (i in 0 until clipDark) a[i] = 0.0
    for (i in 0 until clipBright) a[a.size - 1 - i] = 1.0
    return a
}
