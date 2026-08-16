package com.example.domain

// Kotlin port of exercises.js's step() — generalized over any ExerciseDef, replacing the single
// hardcoded hip-knee-ankle "squat only" logic this project's RepCounter used to contain (per the
// source-to-source audit: "current pose/rep logic is effectively specialized to one hardcoded
// exercise"). This file is the fix for that specific gap.
//
// SMOOTHING GAP (separate from the landmark-space gap documented in Exercises.kt): legacy runs
// every landmark through a One-Euro filter (filter.js) BEFORE step() ever sees it — a speed-
// adaptive smoother tuned against 30fps synthetic signals. Nothing here re-implements that; this
// engine reads whatever landmarks the caller hands it and applies only the same lighter EMA_ALPHA
// smoothing legacy's step() itself does on the primary angle. Fault checks that read raw landmark
// positions directly (torso lean, bar drift, squat depth/valgus) will be noisier here than in
// legacy until a comparable pre-filter is wired to ML Kit's output. Named, not silently accepted.

/** Which 3 joints each of the 4 possible `primary` angle formulas reads — a fixed, exhaustive
 *  table since every legacy exercise's `primary` reduces to exactly one of these (verified by
 *  reading all 28 definitions), unlike the fully dynamic discovery legacy does at runtime. */
private fun jointsForPrimary(j: Joint): List<Joint> = when (j) {
    Joint.KNEE -> listOf(Joint.HIP, Joint.KNEE, Joint.ANKLE)
    Joint.ELBOW -> listOf(Joint.SHOULDER, Joint.ELBOW, Joint.WRIST)
    Joint.HIP -> listOf(Joint.SHOULDER, Joint.HIP, Joint.KNEE)
    Joint.SHOULDER -> listOf(Joint.HIP, Joint.SHOULDER, Joint.ELBOW)
    else -> error("no angle formula for $j")
}

private fun primaryAngle(j: Joint, p: Map<Joint, Pt>): Double {
    fun at(k: Joint) = p[k] ?: Pt(0.0, 0.0, 0.0)
    return when (j) {
        Joint.KNEE -> jointAngleOf(at(Joint.HIP), at(Joint.KNEE), at(Joint.ANKLE))
        Joint.ELBOW -> jointAngleOf(at(Joint.SHOULDER), at(Joint.ELBOW), at(Joint.WRIST))
        Joint.HIP -> jointAngleOf(at(Joint.SHOULDER), at(Joint.HIP), at(Joint.KNEE))
        Joint.SHOULDER -> jointAngleOf(at(Joint.HIP), at(Joint.SHOULDER), at(Joint.ELBOW))
        else -> error("no angle formula for $j")
    }
}

/** One frame of input: both sides' picked joints (already the exercise's own coordinate space —
 *  see the landmark-space gap note), a monotonic timestamp, and which camera view is active. */
data class MovementFrame(
    val left: Map<Joint, Pt>,
    val right: Map<Joint, Pt>,
    val tMs: Long,
    val view: String,
)

data class FaultEvent(val rep: Int, val faultId: String)
data class FiredFault(val id: String, val cue: String, val severity: String)

data class StepResult(
    val visible: Boolean,
    val missing: List<Joint> = emptyList(),
    val angle: Double = 0.0,
    val phase: String = "start",
    val reps: Int = 0,
    val repCompleted: Boolean = false,
    val faults: List<FiredFault> = emptyList(),
)

/**
 * One exercise's live state — the direct equivalent of exercises.js's `createState()` +
 * `step()`, generalized so a new exercise needs zero new engine code, only a new `ExerciseDef`
 * (Step 4's explicit requirement).
 */
class MovementEngine(exId: String, private val thresholds: Map<String, Double>? = null) {
    private val ex: ExerciseDef = EXERCISES[exId] ?: error("unknown exercise: $exId")
    private val t: Map<String, Double> = thresholds ?: ex.thresholds

    var phase: String = "start"; private set
    var reps: Int = 0; private set
    var rejected: Int = 0; private set
    private var ema: Double? = null
    private var tLeftStart: Long = 0
    private var tEnd: Long = 0
    private val faultFrames = mutableMapOf<String, Int>()
    val faultCounts: MutableMap<String, Int> = mutableMapOf()
    val faultEvents: MutableList<FaultEvent> = mutableListOf()
    val repMs: MutableList<Long> = mutableListOf()

    private var side: Side? = null
    private var sideLost = 0

    private fun bestSide(frame: MovementFrame): Side {
        fun score(m: Map<Joint, Pt>) = ex.needs.sumOf { m[it]?.visibility ?: 0.0 }
        return if (score(frame.left) >= score(frame.right)) Side.LEFT else Side.RIGHT
    }

    fun step(frame: MovementFrame): StepResult {
        // Which side we track is decided once per set, not per frame — re-picking every frame
        // would let the tracked side swap mid-rep and read as a sudden fault (see exercises.js's
        // own comment on this exact point).
        if (side == null) side = bestSide(frame)
        val picked = if (side == Side.LEFT) frame.left else frame.right
        val held = ex.needs.sumOf { picked[it]?.visibility ?: 0.0 } / ex.needs.size
        if (held < SIDE_LOST_VIS) {
            sideLost += 1
            if (sideLost > SIDE_LOST_FRAMES) { side = bestSide(frame); sideLost = 0 }
        } else {
            sideLost = 0
        }
        val activeSide = side!!
        val p = if (activeSide == Side.LEFT) frame.left else frame.right

        // Only what rep counting needs, not the exercise's full `needs` list — a half-visible
        // skeleton still counts reps and runs the fault checks it genuinely can.
        val primaryJoints = jointsForPrimary(ex.primary)
        val missing = primaryJoints.filter { (p[it]?.visibility ?: 1.0) < MIN_JOINT_VIS }
        if (missing.isNotEmpty()) {
            faultFrames.clear()
            return StepResult(visible = false, missing = missing, angle = ema ?: 0.0, phase = phase, reps = reps, repCompleted = false)
        }

        val raw = primaryAngle(ex.primary, p)
        ema = if (ema == null) raw else ema!! + EMA_ALPHA * (raw - ema!!)
        val a = ema!!

        // ── rep state machine ──────────────────────────────────────────────────────────────
        val start = t["repStart"] ?: ex.repStart
        val end = t["repEnd"] ?: ex.repEnd
        val dir = if (end > start) 1 else -1
        val atEnd = dir * (a - end) >= -HYSTERESIS
        val atStart = dir * (a - start) <= HYSTERESIS

        var repCompleted = false
        if (phase == "start") {
            if (!atStart && tLeftStart == 0L) {
                tLeftStart = frame.tMs
            }
            if (atEnd) { phase = "end"; tEnd = frame.tMs }
        } else if (atStart) {
            val took = if (tLeftStart != 0L) frame.tMs - tLeftStart else Long.MAX_VALUE
            phase = "start"
            if (took >= MIN_REP_MS) {
                reps += 1
                if (tLeftStart != 0L) repMs.add(took)
                repCompleted = true
            } else if (tLeftStart != 0L) {
                rejected += 1
            }
            tLeftStart = 0
            tEnd = 0
        }

        // ── fault evaluation ───────────────────────────────────────────────────────────────
        // Visibility is checked per CHECK, not per exercise, via TrackingPoints — the Kotlin
        // equivalent of legacy's inline Proxy: each rule abstains on exactly the joints it
        // personally reads that frame, discovered dynamically, never a hand-maintained list.
        val fired = mutableListOf<FiredFault>()
        for (f in ex.faults) {
            val phaseOk = f.phase == "any" || f.phase == phase
            val viewOk = f.view == null || f.view == frame.view
            val sidesOk = !f.bothSides || (
                (frame.left[Joint.ELBOW]?.visibility ?: 0.0) >= MIN_JOINT_VIS &&
                    (frame.right[Joint.ELBOW]?.visibility ?: 0.0) >= MIN_JOINT_VIS
                )
            if (!phaseOk || !viewOk || !sidesOk) {
                faultFrames[f.id] = 0
                continue
            }

            val tracked = TrackingPoints(p)
            val ctx = EvalContext(
                tracked = tracked,
                bothSideVisible = { j -> (frame.left[j]?.visibility ?: 0.0) >= MIN_JOINT_VIS && (frame.right[j]?.visibility ?: 0.0) >= MIN_JOINT_VIS },
                lmLeft = frame.left, lmRight = frame.right,
                thresholds = t, phase = phase, view = frame.view, tEnd = tEnd, tLeftStart = tLeftStart,
            )
            val didFire = f.test(ctx)
            val confident = tracked.touched.all { j -> (p[j]?.visibility ?: 1.0) >= MIN_JOINT_VIS }

            if (!didFire || !confident) {
                faultFrames[f.id] = 0
                continue
            }
            faultFrames[f.id] = (faultFrames[f.id] ?: 0) + 1
            if (faultFrames[f.id] == HOLD_FRAMES) {
                faultCounts[f.id] = (faultCounts[f.id] ?: 0) + 1
                fired.add(FiredFault(f.id, f.cue, f.severity))
                // `reps` already holds completed reps at this point, so the rep in progress is
                // reps + 1 — the same off-by-one legacy's own comment calls out as easy to lose.
                faultEvents.add(FaultEvent(reps + 1, f.id))
            }
        }

        return StepResult(visible = true, angle = a, phase = phase, reps = reps, repCompleted = repCompleted, faults = fired)
    }
}
