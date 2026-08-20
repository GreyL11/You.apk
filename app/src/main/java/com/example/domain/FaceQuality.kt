package com.example.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Whether a capture is good enough to measure anything from. Pure — landmarks and luminance in,
 * numbers out.
 *
 * Ported from the legacy `www/face/quality.js`. The thresholds in [LIMITS] and the metrics here are
 * one unit: a threshold only means something against the exact definition it was written for, which
 * is how the Android app ended up gating a Laplacian-variance limit (0.15) against a completely
 * different gradient measure that lands two orders of magnitude lower — so the focus check could
 * never pass, on any device, in any light.
 *
 * Nothing here decides anything about a face. It decides whether the photograph is worth reading.
 */
object FaceQuality {
    /**
     * The common shape every check result carries — enough for [guide], [assess] and [gate] to
     * combine checks they know nothing else about.
     *
     * `value`/`clipped` are the two fields the legacy's duck-typed objects carried beyond
     * score/reason (sharpness's raw value, framing's list of clipped region names); everything else
     * leaves them null.
     */
    interface CheckResult {
        val score: Double
        val reason: String?
        val value: Double? get() = null
        val clipped: List<String>? get() = null
    }

    object LIMITS {
        /** Eye distance as a fraction of frame width. Too small and every region is a few pixels. */
        const val FACE_MIN = 0.11
        const val FACE_MAX = 0.42

        /** Radians. ~14 degrees of yaw already changes which part of a cheek faces the light. */
        const val YAW_MAX = 0.25
        const val PITCH_MAX = 0.20
        const val ROLL_MAX = 0.30

        /**
         * Variance of the Laplacian, normalised by [SHARPNESS_SCALE]. Below this the image is soft
         * enough that a texture measurement is measuring the blur rather than the face.
         */
        const val SHARPNESS_MIN = 0.15

        /** Fraction of pixels allowed to be crushed to black or blown to white in a region. */
        const val CLIP_MAX = 0.02

        /** Mean luminance 0-1. Outside this the sensor is fighting and colour ratios drift. */
        const val LUMA_MIN = 0.22
        const val LUMA_MAX = 0.82

        /** Left/right illumination imbalance. Side lighting would read as a real asymmetry finding. */
        const val BALANCE_MAX = 0.18

        /** How far brightness may sit from the person's own usual, in their own SDs. */
        const val LIGHTING_DRIFT_MAX = 2.0

        /** Below this overall score, a check-in is not stored as baseline evidence. */
        const val ACCEPT_MIN = 0.6
    }

    /**
     * Empirical, and the legacy says so out loud: chosen so a normally-lit sharp photo lands near 1.
     * Kept as a named constant rather than an inline `* 8` because it is the one number here expected
     * to move once there are real captures from a real device to calibrate against — and when it
     * moves, [LIMITS.SHARPNESS_MIN] does not have to.
     */
    const val SHARPNESS_SCALE = 8.0

    /** ~2/3 second at 30fps. Long enough to rule out a lucky frame, short enough not to be a chore. */
    const val STEADY_FRAMES = 20

    private fun clamp01(n: Double) = max(0.0, min(1.0, n))

    /** 1 inside a band, falling off linearly outside it. */
    fun band(value: Double, lo: Double, hi: Double, tolerance: Double): Double {
        if (value in lo..hi) return 1.0
        val d = if (value < lo) lo - value else value - hi
        return clamp01(1.0 - d / tolerance)
    }

    /** 1 at zero, 0 at [max]. */
    fun under(value: Double, max: Double) = clamp01(1.0 - abs(value) / max)

    data class Sharpness(
        override val score: Double,
        override val value: Double,
        override val reason: String?,
    ) : CheckResult

    /**
     * The standard cheap focus measure: a second-derivative kernel responds to edges, so a sharp
     * image has high variance in the result and a soft one almost none.
     *
     * @param luma luminance 0-1, row-major, [w] x [h]
     */
    fun sharpness(luma: DoubleArray?, w: Int, h: Int): Sharpness {
        if (luma == null || w < 3 || h < 3) return Sharpness(0.0, 0.0, "no image")
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                // 4-neighbour Laplacian.
                val v = 4 * luma[i] - luma[i - 1] - luma[i + 1] - luma[i - w] - luma[i + w]
                sum += v
                sumSq += v * v
                n++
            }
        }
        if (n == 0) return Sharpness(0.0, 0.0, "no image")
        val mean = sum / n
        val variance = sumSq / n - mean * mean
        val value = sqrt(max(0.0, variance)) * SHARPNESS_SCALE
        return Sharpness(
            score = clamp01(value / LIMITS.SHARPNESS_MIN / 2),
            value = value,
            reason = if (value < LIMITS.SHARPNESS_MIN) "hold still — that came out blurry" else null,
        )
    }

    data class Exposure(
        override val score: Double,
        val meanLuma: Double,
        val clipFraction: Double,
        override val reason: String?,
    ) : CheckResult

    /**
     * Is the sensor clipping, and is the overall level usable?
     *
     * Clipped pixels carry no information at all — a blown highlight is the same white whatever was
     * underneath it — so a region with many of them cannot be compared with one without.
     */
    fun exposure(luma: DoubleArray?): Exposure {
        if (luma == null || luma.isEmpty()) return Exposure(0.0, 0.0, 0.0, "no image")
        var sum = 0.0
        var clipped = 0
        for (v in luma) {
            sum += v
            if (v <= 0.02 || v >= 0.98) clipped++
        }
        val mean = sum / luma.size
        val clipFraction = clipped.toDouble() / luma.size
        val reason = when {
            clipFraction > LIMITS.CLIP_MAX -> "too much of that is pure black or pure white"
            mean < LIMITS.LUMA_MIN -> "too dark to read"
            mean > LIMITS.LUMA_MAX -> "too bright to read"
            else -> null
        }
        val score = min(
            band(mean, LIMITS.LUMA_MIN, LIMITS.LUMA_MAX, 0.15),
            under(clipFraction, LIMITS.CLIP_MAX * 4),
        )
        return Exposure(score, mean, clipFraction, reason)
    }

    data class Balance(
        override val score: Double,
        val imbalance: Double,
        override val reason: String?,
    ) : CheckResult

    /**
     * Left/right illumination imbalance.
     *
     * Side lighting makes one cheek brighter than the other, and a difference of that origin would
     * be reported as a real asymmetry about the person's face. Either side missing (not yet sampled)
     * passes cleanly rather than penalising a check that has not run.
     */
    fun balance(leftLuma: Double?, rightLuma: Double?): Balance {
        if (leftLuma == null || rightLuma == null) return Balance(1.0, 0.0, null)
        val total = leftLuma + rightLuma
        if (total < 1e-6) return Balance(0.0, 0.0, "too dark to tell")
        val imbalance = abs(leftLuma - rightLuma) / total
        return Balance(
            score = clamp01(1.0 - imbalance / LIMITS.BALANCE_MAX),
            imbalance = imbalance,
            reason = if (imbalance > LIMITS.BALANCE_MAX) "the light is coming from one side" else null,
        )
    }

    data class LightingMatch(
        override val score: Double,
        val drift: Double?,
        /** False until there is enough history to have an opinion at all. */
        val known: Boolean,
        override val reason: String?,
    ) : CheckResult

    /**
     * Does this capture's lighting match how this person usually photographs themselves?
     *
     * Everything else here judges a capture on its own. This one judges it against their history,
     * which is the only thing that makes a COMPARISON valid — a perfectly exposed photo under a warm
     * bathroom bulb is a perfectly good photo and a bad match for thirty daylight ones.
     *
     * Returns full marks until there is enough history to have an opinion. Refusing to compare on
     * the grounds of a history that does not exist yet would block the feature from ever starting.
     */
    fun lightingMatch(meanLuma: Double, history: List<Double>): LightingMatch {
        if (history.size < 4) return LightingMatch(1.0, null, known = false, reason = null)
        val mu = history.average()
        val sd = sqrt(history.sumOf { (it - mu) * (it - mu) } / history.size)
        // Extremely consistent history: an absolute tolerance instead of dividing by a standard
        // deviation near zero, which would call any difference at all enormous.
        val drift = if (sd < 0.01) abs(meanLuma - mu) / 0.08 else abs(meanLuma - mu) / sd
        return LightingMatch(
            score = clamp01(1.0 - drift / LIMITS.LIGHTING_DRIFT_MAX),
            drift = drift,
            known = true,
            reason = if (drift > LIMITS.LIGHTING_DRIFT_MAX)
                "the light is different from your usual check-ins"
            else null,
        )
    }

    data class Framing(
        override val score: Double,
        val size: Double,
        override val clipped: List<String>,
        override val reason: String?,
    ) : CheckResult

    /** Is the face big enough, centred enough, and completely inside the frame? */
    fun framing(
        lm: List<Geometry.Point>,
        width: Int,
        height: Int,
        mirrored: Boolean = false,
    ): Framing {
        val a = Geometry.alignment(lm) ?: return Framing(0.0, 0.0, emptyList(), "no face")

        val size = a.scale
        val sizeScore = band(size, LIMITS.FACE_MIN, LIMITS.FACE_MAX, 0.10)

        // Centre matters much less than size, so it is weighted lightly — an off-centre face is
        // still perfectly measurable, it just risks clipping.
        val off = kotlin.math.hypot(a.eyeMid.x - 0.5, a.eyeMid.y - 0.45)
        val centreScore = clamp01(1.0 - off / 0.35)

        val rs = Geometry.regions(lm, mirrored)
        val clipped = rs?.asMap()
            ?.filter { (_, box) -> Geometry.toPixels(box, width, height).clipped }
            ?.keys?.toList()
            ?: emptyList()

        val score = if (clipped.isNotEmpty()) 0.0 else sizeScore * 0.75 + centreScore * 0.25
        val reason = when {
            clipped.isNotEmpty() -> "${clipped.joinToString(", ")} outside the frame"
            size < LIMITS.FACE_MIN -> "too far away"
            size > LIMITS.FACE_MAX -> "too close"
            else -> null
        }
        return Framing(score, size, clipped, reason)
    }

    data class Pose(
        override val score: Double,
        val yaw: Double?,
        val pitch: Double?,
        val roll: Double?,
        override val reason: String?,
    ) : CheckResult

    /** Head square-on to the camera? Uses the transformation matrix when it is there. */
    fun pose(lm: List<Geometry.Point>, matrix: DoubleArray? = null): Pose {
        val p = Geometry.poseFromMatrix(matrix) ?: Geometry.headPose(lm)
            ?: return Pose(0.0, null, null, null, "no face")
        val yawScore = under(p.yaw, LIMITS.YAW_MAX)
        val pitchScore = if (p.pitch == null) 1.0 else under(p.pitch, LIMITS.PITCH_MAX)
        val rollScore = under(p.roll, LIMITS.ROLL_MAX)
        val score = min(yawScore, min(pitchScore, rollScore))
        val reason = when {
            score > 0.5 -> null
            yawScore <= pitchScore && yawScore <= rollScore -> "turn to face the camera"
            pitchScore <= rollScore -> "hold the phone level with your face"
            else -> "straighten your head"
        }
        return Pose(score, p.yaw, p.pitch, p.roll, reason)
    }

    data class Steadiness(val frames: Int, val ready: Boolean)

    /**
     * How many frames in a row have been good enough to capture from.
     *
     * The face has to be still, not merely acceptable in one lucky frame: a capture taken
     * mid-movement is blurred in a way the sharpness check can miss when the blur runs along an
     * edge it happens not to sample. Counting consecutive good frames costs nothing and rules that
     * out.
     *
     * Any bad frame resets it to zero rather than decrementing. Half-steady is not steady.
     */
    fun steadiness(state: Steadiness?, accepted: Boolean): Steadiness {
        val frames = if (accepted) (state?.frames ?: 0) + 1 else 0
        return Steadiness(frames, frames >= STEADY_FRAMES)
    }

    /** Gates that stop a capture outright, versus ones that only make it incomparable. */
    val BLOCKING = listOf("framing", "pose", "sharpness", "exposure")
    val COMPARABILITY = listOf("balance", "lighting")

    /** What each check is about, in words a person can act on. Used by the dev panel and the HUD. */
    val LABELS = mapOf(
        "framing" to "Distance and framing",
        "pose" to "Head angle",
        "sharpness" to "Focus",
        "exposure" to "Brightness",
        "balance" to "Even lighting",
        "lighting" to "Match with your usual light",
    )

    data class Guide(val instruction: String?, val blocking: String?)

    /**
     * The single thing to tell the person right now.
     *
     * One instruction, never a list. A panel reading "move closer, hold still, turn to face the
     * camera, the light is coming from one side" is not guidance, it is a wall — and someone holding
     * a phone at arm's length can act on exactly one thing at a time.
     *
     * Ordered by what blocks what. There is no point asking someone to hold still while they are
     * out of frame, and no point mentioning the lighting until they are the right distance away,
     * because moving will change it anyway.
     */
    fun guide(parts: Map<String, CheckResult?>): Guide {
        val order = listOf("framing", "pose", "exposure", "sharpness", "balance", "lighting")
        for (k in order) {
            val reason = parts[k]?.reason
            if (reason != null) return Guide(reason, k)
        }
        return Guide(null, null)
    }

    data class Assessment(
        val scores: Map<String, Double>,
        val overall: Double,
        val accepted: Boolean,
        /** A capture can be accepted and not trustworthy — good enough to store, not a strong basis
         *  for a claim. Kept separate from [accepted] so the trend layer can tell the two apart. */
        val trustworthy: Boolean,
        val warnings: List<String>,
    )

    /**
     * One verdict from all of it.
     *
     * Combined as a WEIGHTED MINIMUM, not an average. A capture that is perfect in four ways and
     * cropped in the fifth is not 80% usable, it is unusable, and averaging would hide that. The
     * lowest component sets the ceiling; the others can only pull it further down.
     */
    fun assess(parts: Map<String, CheckResult?>): Assessment {
        val named = parts.filterValues { it != null }.mapValues { it.value!! }
        if (named.isEmpty()) return Assessment(emptyMap(), 0.0, false, false, listOf("nothing to assess"))

        val scores = named.mapValues { it.value.score }
        val worst = scores.values.min()
        val avg = scores.values.average()
        // Two thirds the weakest link, one third the general standard.
        val overall = Math.round((worst * 0.67 + avg * 0.33) * 100) / 100.0

        val warnings = named.values.mapNotNull { it.reason }
        return Assessment(
            scores = scores.mapValues { Math.round(it.value * 100) / 100.0 },
            overall = overall,
            accepted = overall >= LIMITS.ACCEPT_MIN,
            trustworthy = overall >= 0.8,
            warnings = warnings,
        )
    }

    data class GateCheck(
        val score: Double,
        val pass: Boolean,
        val reason: String?,
        val label: String,
        val value: Double?,
        val clipped: List<String>?,
    )

    data class Gate(
        /** Every blocking check passed and the capture is worth measuring and storing. */
        val accepted: Boolean,
        /** May also be set beside this person's history. Accepted-but-not-comparable is common and
         *  useful: stored, and the trend layer declines to use it rather than the app declining to
         *  take it. */
        val comparable: Boolean,
        val checks: Map<String, GateCheck>,
        val failures: List<String>,
        val warnings: List<String>,
        val missing: List<String>,
        val instruction: String?,
        /** Kept for the record and for triage, deliberately not shown to anyone. */
        val overall: Double,
    )

    /**
     * The structured verdict the pipeline runs on.
     *
     * A check that was never run — because no pixels were read yet, which is every preview frame —
     * is ABSENT from `checks` rather than present with a zero. A zero would read as a failed check.
     */
    fun gate(parts: Map<String, CheckResult?>): Gate {
        val checks = parts.mapNotNull { (name, p) ->
            if (p == null) null
            else name to GateCheck(
                score = Math.round(p.score * 100) / 100.0,
                pass = p.reason == null,
                reason = p.reason,
                label = LABELS[name] ?: name,
                value = p.value,
                clipped = p.clipped?.takeIf { it.isNotEmpty() },
            )
        }.toMap()

        fun named(list: List<String>) = list.filter { checks[it] != null && !checks.getValue(it).pass }
        val failures = named(BLOCKING)
        val warnings = named(COMPARABILITY)
        val missing = BLOCKING.filter { checks[it] == null }

        return Gate(
            // Missing a blocking check is not a pass. A capture measured before the pixel checks
            // existed must never be recorded as though it had passed them.
            accepted = failures.isEmpty() && missing.isEmpty(),
            comparable = failures.isEmpty() && missing.isEmpty() && warnings.isEmpty(),
            checks = checks,
            failures = failures,
            warnings = warnings,
            missing = missing,
            instruction = guide(parts).instruction,
            overall = assess(parts).overall,
        )
    }
}
