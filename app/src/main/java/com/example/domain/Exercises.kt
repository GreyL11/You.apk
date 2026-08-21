package com.example.domain

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// Pure movement analysis — the Kotlin port of legacy's www/exercises.js. No Android/Compose
// import here on purpose, mirroring the legacy file's own "no DOM" discipline, so this can be
// unit-tested on a plain JVM.
//
// LANDMARK-SPACE GAP (read this before trusting an angle number):
// Legacy computes joint angles on MediaPipe's `worldLandmarks` — real metres, origin at the hip,
// aspect-undistorted — and only uses the plain normalized `lm` (image-plane, aspect-distorted,
// y-down) for anything comparing a body part to the frame/ground (squat depth, heel lift, knee
// valgus). ML Kit's PoseLandmark API exposes only image-space positions: `getPosition()` (2D
// pixel) and `getPosition3D()` (pixel x/y, z relative to the hip) — there is no ML Kit
// equivalent of a true metric world landmark. So every joint here lives in ONE space —
// normalized-by-image-size pixel coordinates — which is legacy's `lm`, not its `w`. Angle-based
// faults (elbow lockout, torso lean, bar drift-vs-leg-length) will therefore carry more
// perspective/aspect distortion than the legacy thresholds were tuned against. This is a real,
// named parity gap, not a bug to "fix" by inventing a fake metric space.
//
// Angles ignore z, matching legacy's own choice (see exercises.js's header comment) — this part
// carries over exactly, it isn't a compromise forced by ML Kit.

/** One landmark: normalized [0,1] image-plane position plus a visibility/likelihood score. */
data class Pt(val x: Double, val y: Double, val visibility: Double)

/** The 9 per-side joints legacy's IDX table names. */
enum class Joint { SHOULDER, ELBOW, WRIST, INDEX, HIP, KNEE, ANKLE, HEEL, TOE }

enum class Side { LEFT, RIGHT }

/** Interior angle at `b`, in degrees — direct port of exercises.js's `angle()`. */
fun jointAngleOf(a: Pt, b: Pt, c: Pt): Double {
    val abx = a.x - b.x; val aby = a.y - b.y
    val cbx = c.x - b.x; val cby = c.y - b.y
    val mag = hypot(abx, aby) * hypot(cbx, cby)
    if (mag < 1e-9) return 0.0
    val cos = min(1.0, max(-1.0, (abx * cbx + aby * cby) / mag))
    return acos(cos) * (180.0 / Math.PI)
}

/** Tilt of the hip→shoulder line away from vertical, sign-agnostic — port of `torsoLean()`. */
fun torsoLean(hip: Pt, shoulder: Pt): Double =
    atan2(abs(shoulder.x - hip.x), abs(shoulder.y - hip.y)) * (180.0 / Math.PI)

/**
 * Records exactly which joints a fault's test touched while it ran — the Kotlin equivalent of
 * exercises.js's `jointsUsedBy` / the inline Proxy in step()'s fault loop. A fault gates on only
 * the joints it actually reads that frame, never the exercise's full `needs` list, which is what
 * lets a half-visible skeleton still run the checks it can (see exercises.js's own comment on
 * this — "a half-visible skeleton now counts reps and runs the rules it genuinely can").
 */
class TrackingPoints(private val points: Map<Joint, Pt>) {
    val touched = mutableSetOf<Joint>()
    fun get(j: Joint): Pt {
        touched.add(j)
        return points[j] ?: Pt(0.0, 0.0, 0.0)
    }
}

/** Everything one fault `test` closure needs. `p`/`w` are the SAME tracked space (see the
 *  landmark-gap note above); kept as two accessors so the exercise definitions below still read
 *  the same way legacy's `c.P`/`c.W` did, and a future real-world-landmark source only has to
 *  change what feeds `w`, not every fault that reads it. */
class EvalContext(
    private val tracked: TrackingPoints,
    val bothSideVisible: (Joint) -> Boolean,
    val lmLeft: Map<Joint, Pt>,
    val lmRight: Map<Joint, Pt>,
    val thresholds: Map<String, Double>,
    val phase: String,
    val view: String,
    val tEnd: Long,
    val tLeftStart: Long,
) {
    fun p(j: Joint): Pt = tracked.get(j)
    fun w(j: Joint): Pt = tracked.get(j)
    val touched get() = tracked.touched
    fun t(key: String): Double = thresholds[key] ?: error("missing threshold $key")
    fun jointAngle(j: Joint): Double = when (j) {
        Joint.KNEE -> jointAngleOf(w(Joint.HIP), w(Joint.KNEE), w(Joint.ANKLE))
        Joint.ELBOW -> jointAngleOf(w(Joint.SHOULDER), w(Joint.ELBOW), w(Joint.WRIST))
        Joint.HIP -> jointAngleOf(w(Joint.SHOULDER), w(Joint.HIP), w(Joint.KNEE))
        Joint.SHOULDER -> jointAngleOf(w(Joint.HIP), w(Joint.SHOULDER), w(Joint.ELBOW))
        else -> error("no angle formula for $j")
    }
}

data class FaultRule(
    val id: String,
    val cue: String,
    val phase: String = "any", // "any" | "start" | "end"
    val view: String? = null,  // gated to this camera view if set — unobservable otherwise
    val bothSides: Boolean = false,
    var severity: String = "efficiency", // stamped from SAFETY after catalogue build, like legacy
    val test: (EvalContext) -> Boolean,
)

// ── shared fault builders — direct ports of exercises.js's parameterised builders ──────────

fun lockoutFault(cue: String, joint: Joint = Joint.ELBOW, phase: String = "start") = FaultRule(
    id = "lockout", cue = cue, phase = phase,
    test = { c -> c.jointAngle(joint) < c.t("lockout") },
)

fun torsoLeanFault(cue: String, id: String = "torso") = FaultRule(
    id = id, cue = cue, phase = "any",
    test = { c -> torsoLean(c.w(Joint.HIP), c.w(Joint.SHOULDER)) > c.t("torsoLean") },
)

fun fastEccentricFault(cue: String) = FaultRule(
    id = "eccentric", cue = cue, phase = "end",
    test = { c -> c.tEnd > 0 && c.tLeftStart > 0 && (c.tEnd - c.tLeftStart) < c.t("eccentricMs") },
)

fun upperArmFault(cue: String) = FaultRule(
    id = "elbowDrift", cue = cue, phase = "any",
    test = { c -> c.jointAngle(Joint.SHOULDER) > c.t("upperArm") },
)

fun fixedUpperArmFault(cue: String) = FaultRule(
    id = "upperArm", cue = cue, phase = "any",
    test = { c -> abs(c.jointAngle(Joint.SHOULDER) - c.t("upperArmTarget")) > c.t("upperArmTol") },
)

fun elbowPathFault(cue: String) = FaultRule(
    id = "elbowPath", cue = cue, phase = "end",
    test = { c -> c.jointAngle(Joint.SHOULDER) > c.t("elbowPath") },
)

fun shortRangeFault(cue: String, joint: Joint = Joint.ELBOW) = FaultRule(
    id = "depth", cue = cue, phase = "end",
    test = { c -> c.jointAngle(joint) > c.t("depth") },
)

fun minLeanFault(cue: String, id: String = "heave") = FaultRule(
    id = id, cue = cue, phase = "any",
    test = { c -> torsoLean(c.w(Joint.HIP), c.w(Joint.SHOULDER)) < c.t("torsoMin") },
)

fun barDriftFault(cue: String, ref: Joint = Joint.KNEE) = FaultRule(
    id = "barDrift", cue = cue, phase = "any",
    test = { c ->
        val legLen = hypot(c.w(Joint.HIP).x - c.w(Joint.ANKLE).x, c.w(Joint.HIP).y - c.w(Joint.ANKLE).y)
        if (legLen < 1e-6) false else abs(c.w(Joint.WRIST).x - c.w(ref).x) / legLen > c.t("barDrift")
    },
)

data class ExerciseDef(
    val id: String,
    val name: String,
    val group: String,
    val view: String, // "side" | "front"
    val cameraHint: String,
    val needs: List<Joint>, // used for side-selection + rep-visibility fallback, port of `needs`
    val repStart: Double,
    val repEnd: Double,
    val primary: Joint, // every legacy exercise's `primary` reduces to exactly one of these 4
    val thresholds: Map<String, Double>,
    val faults: List<FaultRule>,
    val equipment: String,
    val compound: Boolean,
    val avoidFor: List<String>,
    val loadRatio: Double,
)

private val ARMS_SIDE = listOf(Joint.SHOULDER, Joint.ELBOW, Joint.WRIST, Joint.HIP)
private val LEGS_SIDE = listOf(Joint.SHOULDER, Joint.HIP, Joint.KNEE, Joint.ANKLE)

/** Bench and its variants differ only in bench angle, which the rules do not see — port of `benchLike`. */
private fun benchLike(name: String, cameraHint: String, over: Map<String, Double> = emptyMap()) = ExerciseDef(
    id = "", name = name, group = "Chest", view = "side", cameraHint = cameraHint, needs = ARMS_SIDE,
    repStart = 165.0, repEnd = 80.0, primary = Joint.ELBOW,
    thresholds = mapOf("lockout" to 163.0, "flare" to 75.0, "wristBend" to 155.0, "asymmetry" to 22.0, "eccentricMs" to 600.0) + over,
    faults = listOf(
        FaultRule("flare", "Tuck the elbows. Around forty-five degrees, not flared out.", "end") { c -> c.jointAngle(Joint.SHOULDER) > c.t("flare") },
        lockoutFault("Finish the lockout at the top."),
        FaultRule("wrist", "Stack your wrists. Knuckles to the ceiling.", "any") { c ->
            jointAngleOf(c.w(Joint.ELBOW), c.w(Joint.WRIST), c.w(Joint.INDEX)) < c.t("wristBend")
        },
        fastEccentricFault("Control the descent. Do not bounce it off your chest."),
        FaultRule("asymmetry", "One arm is lagging. Press evenly.", "any", bothSides = true) { c ->
            val l = jointAngleOf(c.lmLeft[Joint.SHOULDER]!!, c.lmLeft[Joint.ELBOW]!!, c.lmLeft[Joint.WRIST]!!)
            val r = jointAngleOf(c.lmRight[Joint.SHOULDER]!!, c.lmRight[Joint.ELBOW]!!, c.lmRight[Joint.WRIST]!!)
            abs(l - r) > c.t("asymmetry")
        },
    ),
    equipment = "barbell", compound = true, avoidFor = listOf("shoulder"), loadRatio = 0.50,
)

/** Straight-arm raises — side/front/rear delt. `hinged` swaps the torso rule, port of `raiseLike`. */
private fun raiseLike(name: String, view: String, cameraHint: String, hinged: Boolean = false, over: Map<String, Double> = emptyMap()) = ExerciseDef(
    id = "", name = name, group = "Shoulders", view = view, cameraHint = cameraHint, needs = ARMS_SIDE,
    repStart = 18.0, repEnd = 82.0, primary = Joint.SHOULDER,
    thresholds = mapOf("maxHeight" to 105.0, "elbowStraight" to 145.0, "eccentricMs" to 400.0) +
        (if (hinged) mapOf("torsoMin" to 50.0) else mapOf("torsoLean" to 12.0)) + over,
    faults = listOf(
        FaultRule("tooHigh", "Stop at shoulder height. Higher is your traps, not your delts.", "end") { c -> c.jointAngle(Joint.SHOULDER) > c.t("maxHeight") },
        FaultRule("elbowBend", "Keep the elbow fixed. You are curling it up.", "any") { c -> c.jointAngle(Joint.ELBOW) < c.t("elbowStraight") },
        if (hinged) minLeanFault("Stay hinged over. Stop standing up into it.") else torsoLeanFault("No swinging. Let the weight do the work on the way down.", "swing"),
        fastEccentricFault("Lower it under control."),
    ),
    equipment = "dumbbell", compound = false, avoidFor = listOf("shoulder"), loadRatio = 0.06,
)

/** Barbell/hammer/cable curls — port of `curlLike`. */
private fun curlLike(name: String, cameraHint: String) = ExerciseDef(
    id = "", name = name, group = "Biceps", view = "side", cameraHint = cameraHint, needs = ARMS_SIDE,
    repStart = 163.0, repEnd = 55.0, primary = Joint.ELBOW,
    thresholds = mapOf("lockout" to 155.0, "upperArm" to 28.0, "torsoLean" to 14.0, "eccentricMs" to 500.0),
    faults = listOf(
        upperArmFault("Elbows still. Stop swinging them forward."),
        torsoLeanFault("Stop swinging. Stand still and let the biceps work.", "swing"),
        lockoutFault("All the way down. Full stretch at the bottom."),
        fastEccentricFault("Slow the negative down."),
    ),
    equipment = "barbell", compound = false, avoidFor = listOf("elbow"), loadRatio = 0.20,
)

private fun withId(id: String, def: ExerciseDef) = def.copy(id = id)

// ── fault severity — port of the SAFETY table ─────────────────────────────────────────────
// Declared BEFORE the catalogue on purpose: `rawExercises()` below is called eagerly from
// EXERCISES's own top-level initializer, and Kotlin/JVM runs top-level property initializers in
// file order — so SAFETY has to already exist by then, not merely be declared later in the file.
private val SAFETY: Map<String, List<String>> = mapOf(
    "squat" to listOf("valgus", "torso"),
    "deadlift" to listOf("barDrift"),
    "rdl" to listOf("barDrift"),
    "ohp" to listOf("arch"),
    "bench" to listOf("flare", "wrist"), "inclineBench" to listOf("flare", "wrist"),
    "declineBench" to listOf("flare", "wrist"), "dbBench" to listOf("flare", "wrist"),
    "inclineDbPress" to listOf("flare", "wrist"),
    "pushup" to listOf("plank"),
    "row" to listOf("heave"),
    "curl" to listOf("swing"), "hammerCurl" to listOf("swing"), "cableCurl" to listOf("swing"),
    "lateralRaise" to listOf("tooHigh"), "frontRaise" to listOf("tooHigh"),
    "cableLateralRaise" to listOf("tooHigh"), "cableFrontRaise" to listOf("tooHigh"),
    // rearDeltRaise deliberately carries no safety fault — see exercises.js's own comment: its
    // 'heave' is the row's rule at rear-delt loads, sloppy rather than dangerous there.
)

fun isSafetyFault(exId: String, faultId: String) = (SAFETY[exId] ?: emptyList()).contains(faultId)

/**
 * The full legacy catalogue — 28 exercises, ported from exercises.js's `EXERCISES`+`META`
 * tables. Every threshold/fault/loadRatio/equipment value below is transcribed, not invented;
 * cross-reference exercises.js's own source comments for the reasoning behind each one.
 */
private fun rawExercises(): Map<String, ExerciseDef> = linkedMapOf(
    // ── Legs ──
    "squat" to ExerciseDef(
        id = "squat", name = "Back squat", group = "Legs", view = "side",
        cameraHint = "Phone side-on at hip height, 2–3 m away. Whole body and both feet in frame.",
        needs = LEGS_SIDE, repStart = 168.0, repEnd = 95.0, primary = Joint.KNEE,
        thresholds = mapOf("lockout" to 160.0, "torsoLean" to 55.0, "depthGap" to 0.0, "heelLift" to 0.30, "valgusRatio" to 0.82, "eccentricMs" to 0.0),
        faults = listOf(
            FaultRule("depth", "Deeper. Hip crease below the knee.", "end") { c -> c.p(Joint.HIP).y < c.p(Joint.KNEE).y - c.t("depthGap") },
            torsoLeanFault("Chest up. You are folding over the bar."),
            FaultRule("heel", "Heels down. Drive through the midfoot.", "any") { c ->
                val footLen = hypot(c.p(Joint.TOE).x - c.p(Joint.HEEL).x, c.p(Joint.TOE).y - c.p(Joint.HEEL).y)
                if (footLen < 1e-6) false else (c.p(Joint.TOE).y - c.p(Joint.HEEL).y) / footLen > c.t("heelLift")
            },
            // Physically unobservable from the side — gated to 'front' below, exactly like legacy.
            // Legacy reads both-side raw `lm` directly here rather than through the tracked-side
            // P/W proxy, so this fault carries NO visibility gating in legacy either — preserved
            // as-is rather than "fixed", per the preserve-exact-behavior instruction.
            FaultRule("valgus", "Knees out. Do not let them cave in.", "any", view = "front") { c ->
                val kneeGap = abs(c.lmLeft[Joint.KNEE]!!.x - c.lmRight[Joint.KNEE]!!.x)
                val ankleGap = abs(c.lmLeft[Joint.ANKLE]!!.x - c.lmRight[Joint.ANKLE]!!.x)
                if (ankleGap < 1e-6) false else kneeGap / ankleGap < c.t("valgusRatio")
            },
        ),
        equipment = "barbell", compound = true, avoidFor = listOf("knee", "lowerBack"), loadRatio = 0.60,
    ),
    "rdl" to ExerciseDef(
        id = "rdl", name = "Romanian deadlift", group = "Legs", view = "side",
        cameraHint = "Phone side-on at hip height. Whole body and the bar in frame.",
        needs = LEGS_SIDE, repStart = 165.0, repEnd = 100.0, primary = Joint.HIP,
        thresholds = mapOf("lockout" to 158.0, "kneeMin" to 150.0, "barDrift" to 0.16, "depth" to 115.0, "eccentricMs" to 0.0),
        faults = listOf(
            FaultRule("kneeBend", "Less knee bend. Push the hips back, this is a hinge.", "any") { c -> c.jointAngle(Joint.KNEE) < c.t("kneeMin") },
            barDriftFault("Keep the bar against your legs.", Joint.KNEE),
            lockoutFault("Stand all the way up. Squeeze the glutes.", Joint.HIP),
            shortRangeFault("Hinge further. Feel the hamstrings stretch.", Joint.HIP),
        ),
        equipment = "barbell", compound = true, avoidFor = listOf("lowerBack"), loadRatio = 0.50,
    ),
    "lunge" to ExerciseDef(
        id = "lunge", name = "Lunge", group = "Legs", view = "side",
        cameraHint = "Phone side-on at hip height, on your working leg. Both feet in frame.",
        needs = LEGS_SIDE, repStart = 165.0, repEnd = 95.0, primary = Joint.KNEE,
        thresholds = mapOf("lockout" to 158.0, "torsoLean" to 25.0, "depth" to 105.0, "eccentricMs" to 0.0),
        faults = listOf(
            shortRangeFault("Deeper. Back knee toward the floor.", Joint.KNEE),
            torsoLeanFault("Chest up. Stay tall through the torso."),
            lockoutFault("Stand all the way up between reps.", Joint.KNEE),
        ),
        equipment = "dumbbell", compound = true, avoidFor = listOf("knee"), loadRatio = 0.20,
    ),
    // ── Chest ──
    "bench" to withId("bench", benchLike("Bench press", "Phone at bench height, side-on or 45° from the foot end. Both arms in frame.")),
    "inclineBench" to withId("inclineBench", benchLike("Incline bench press", "Phone at bench height, side-on. Both arms and the bar path in frame.", mapOf("flare" to 70.0))),
    "declineBench" to withId("declineBench", benchLike("Decline bench press", "Phone at bench height, side-on. Both arms and the bar path in frame.", mapOf("flare" to 82.0))),
    "dbBench" to withId("dbBench", benchLike("Dumbbell bench press", "Phone at bench height, side-on or 45° from the foot end. Both arms in frame.").copy(equipment = "dumbbell", loadRatio = 0.20)),
    "inclineDbPress" to withId("inclineDbPress", benchLike("Incline dumbbell press", "Phone at bench height, side-on. Both arms in frame.", mapOf("flare" to 70.0)).copy(equipment = "dumbbell", loadRatio = 0.16)),
    "chestDip" to ExerciseDef(
        id = "chestDip", name = "Chest dip", group = "Chest", view = "side",
        cameraHint = "Phone side-on at chest height, 2 m away. Whole body in frame.",
        needs = ARMS_SIDE, repStart = 168.0, repEnd = 85.0, primary = Joint.ELBOW,
        thresholds = mapOf("lockout" to 162.0, "torsoMin" to 20.0, "depth" to 100.0, "eccentricMs" to 500.0),
        faults = listOf(
            minLeanFault("Lean forward over your hands. Upright makes this a triceps dip.", "upright"),
            shortRangeFault("Deeper. Chest down between your hands."),
            lockoutFault("Lock the elbows out at the top."),
            fastEccentricFault("Control the descent."),
        ),
        equipment = "bodyweight", compound = true, avoidFor = listOf("shoulder", "elbow"), loadRatio = 0.0,
    ),
    "pushup" to ExerciseDef(
        id = "pushup", name = "Push-up", group = "Chest", view = "side",
        cameraHint = "Phone on the floor, side-on, 2 m away. Head to heels in frame.",
        needs = listOf(Joint.SHOULDER, Joint.ELBOW, Joint.WRIST, Joint.HIP, Joint.KNEE),
        repStart = 165.0, repEnd = 90.0, primary = Joint.ELBOW,
        thresholds = mapOf("lockout" to 160.0, "flare" to 70.0, "plank" to 163.0, "depth" to 100.0, "eccentricMs" to 0.0),
        faults = listOf(
            FaultRule("plank", "Straight line from head to heels. Squeeze your glutes.", "any") { c -> jointAngleOf(c.w(Joint.SHOULDER), c.w(Joint.HIP), c.w(Joint.KNEE)) < c.t("plank") },
            FaultRule("flare", "Elbows back, not out to the sides.", "end") { c -> c.jointAngle(Joint.SHOULDER) > c.t("flare") },
            shortRangeFault("Lower all the way. Chest to the floor."),
            lockoutFault("Push all the way up."),
        ),
        equipment = "bodyweight", compound = true, avoidFor = emptyList(), loadRatio = 0.0,
    ),
    // ── Back ──
    "deadlift" to ExerciseDef(
        id = "deadlift", name = "Deadlift", group = "Back", view = "side",
        cameraHint = "Phone side-on at hip height, 2–3 m away. Bar, shins and whole body in frame.",
        needs = LEGS_SIDE, repStart = 165.0, repEnd = 105.0, primary = Joint.HIP,
        thresholds = mapOf("lockout" to 158.0, "barDrift" to 0.14, "depth" to 125.0, "torsoLean" to 90.0, "eccentricMs" to 0.0),
        faults = listOf(
            barDriftFault("Bar is drifting away from your shins. Drag it up your legs."),
            lockoutFault("Finish the lockout. Hips through, glutes tight.", Joint.HIP),
            shortRangeFault("Get your hips down to the bar before you pull.", Joint.HIP),
        ),
        equipment = "barbell", compound = true, avoidFor = listOf("lowerBack"), loadRatio = 0.75,
    ),
    "row" to ExerciseDef(
        id = "row", name = "Barbell row", group = "Back", view = "side",
        cameraHint = "Phone side-on at hip height. Torso, bar and both arms in frame.",
        needs = ARMS_SIDE, repStart = 165.0, repEnd = 75.0, primary = Joint.ELBOW,
        thresholds = mapOf("lockout" to 158.0, "torsoMin" to 32.0, "elbowPath" to 55.0, "eccentricMs" to 0.0),
        faults = listOf(
            minLeanFault("Stay hinged over. Stop standing up into it."),
            elbowPathFault("Elbows tight to your body. Row to your hip, not your chest."),
            lockoutFault("Full stretch at the bottom. Let the bar hang."),
        ),
        equipment = "barbell", compound = true, avoidFor = listOf("lowerBack"), loadRatio = 0.45,
    ),
    "cableRow" to ExerciseDef(
        id = "cableRow", name = "Seated cable row", group = "Back", view = "side",
        cameraHint = "Phone side-on at chest height. Torso, both arms and the handle in frame.",
        needs = ARMS_SIDE, repStart = 165.0, repEnd = 75.0, primary = Joint.ELBOW,
        thresholds = mapOf("lockout" to 158.0, "torsoLean" to 22.0, "elbowPath" to 50.0, "eccentricMs" to 0.0),
        faults = listOf(
            torsoLeanFault("Stop rocking. Move it with your back, not your bodyweight."),
            elbowPathFault("Elbows tight past your ribs, not out wide."),
            lockoutFault("Let your arms straighten all the way out at the front."),
        ),
        equipment = "cable", compound = true, avoidFor = emptyList(), loadRatio = 0.45,
    ),
    "straightArmPulldown" to ExerciseDef(
        id = "straightArmPulldown", name = "Straight-arm pulldown", group = "Back", view = "side",
        cameraHint = "Phone side-on at chest height, 2 m away. Whole torso and the working arm in frame.",
        needs = ARMS_SIDE, repStart = 148.0, repEnd = 25.0, primary = Joint.SHOULDER,
        thresholds = mapOf("lockout" to 140.0, "elbowStraight" to 155.0, "torsoLean" to 22.0, "depth" to 40.0, "eccentricMs" to 0.0),
        faults = listOf(
            FaultRule("elbowBend", "Arms straight. Bending the elbow makes this a pulldown.", "any") { c -> c.jointAngle(Joint.ELBOW) < c.t("elbowStraight") },
            torsoLeanFault("Hold your hinge. Stop bobbing up and down."),
            lockoutFault("Let your arms rise all the way back up for the stretch.", Joint.SHOULDER),
            shortRangeFault("All the way to your thighs.", Joint.SHOULDER),
        ),
        equipment = "cable", compound = false, avoidFor = emptyList(), loadRatio = 0.20,
    ),
    "latPulldown" to ExerciseDef(
        id = "latPulldown", name = "Lat pulldown", group = "Back", view = "side",
        cameraHint = "Phone side-on at chest height. Torso and both arms in frame.",
        needs = ARMS_SIDE, repStart = 168.0, repEnd = 60.0, primary = Joint.ELBOW,
        thresholds = mapOf("lockout" to 160.0, "torsoLean" to 28.0, "depth" to 80.0, "eccentricMs" to 0.0),
        faults = listOf(
            torsoLeanFault("Stop leaning back. Pull with your lats, not your bodyweight."),
            lockoutFault("Full stretch at the top. Let your shoulders rise."),
            shortRangeFault("Bar to your upper chest."),
        ),
        equipment = "cable", compound = true, avoidFor = emptyList(), loadRatio = 0.50,
    ),
    // ── Shoulders ──
    "ohp" to ExerciseDef(
        id = "ohp", name = "Overhead press", group = "Shoulders", view = "side",
        cameraHint = "Phone side-on at chest height, 2–3 m away. Full overhead reach in frame.",
        needs = ARMS_SIDE, repStart = 80.0, repEnd = 172.0, primary = Joint.ELBOW,
        thresholds = mapOf("lockout" to 165.0, "torsoLean" to 16.0, "barPath" to 0.22, "eccentricMs" to 0.0),
        faults = listOf(
            torsoLeanFault("Stop leaning back. Squeeze your glutes and press vertically.", "arch"),
            FaultRule("lockout", "Lock it out overhead.", "end") { c -> c.jointAngle(Joint.ELBOW) < c.t("lockout") },
            FaultRule("barPath", "Press straight up. The bar should finish over your ears.", "end") { c ->
                val torso = hypot(c.w(Joint.SHOULDER).x - c.w(Joint.HIP).x, c.w(Joint.SHOULDER).y - c.w(Joint.HIP).y)
                if (torso < 1e-6) false else abs(c.w(Joint.WRIST).x - c.w(Joint.SHOULDER).x) / torso > c.t("barPath")
            },
        ),
        equipment = "barbell", compound = true, avoidFor = listOf("shoulder"), loadRatio = 0.30,
    ),
    "lateralRaise" to withId("lateralRaise", raiseLike("Lateral raise", "front", "Phone FRONT-ON at chest height. Both arms in frame — this one needs a front view.")),
    "frontRaise" to withId("frontRaise", raiseLike("Front raise", "side", "Phone SIDE-ON at chest height, 2 m away. Torso and the working arm in frame.", over = mapOf("elbowStraight" to 150.0))),
    "rearDeltRaise" to withId("rearDeltRaise", raiseLike("Rear delt raise", "front", "Phone FRONT-ON and LOW, roughly knee height, 2 m away. Hinge over facing it.", hinged = true, over = mapOf("maxHeight" to 100.0, "elbowStraight" to 140.0)).copy(loadRatio = 0.04)),
    "cableLateralRaise" to withId("cableLateralRaise", raiseLike("Cable lateral raise", "front", "Phone FRONT-ON at chest height, working side nearest the stack. Both arms in frame.").copy(equipment = "cable", loadRatio = 0.07)),
    "cableFrontRaise" to withId("cableFrontRaise", raiseLike("Cable front raise", "side", "Phone SIDE-ON at chest height, 2 m away. Torso and the working arm in frame.", over = mapOf("elbowStraight" to 150.0)).copy(equipment = "cable", loadRatio = 0.06)),
    // ── Biceps ──
    "curl" to withId("curl", curlLike("Barbell curl", "Phone side-on at chest height. Torso and both arms in frame.")),
    "hammerCurl" to withId("hammerCurl", curlLike("Hammer curl", "Phone side-on at chest height. Torso and both arms in frame.").copy(equipment = "dumbbell", loadRatio = 0.10)),
    "cableCurl" to withId("cableCurl", curlLike("Cable curl", "Phone side-on at chest height. Torso and both arms in frame.").copy(equipment = "cable", loadRatio = 0.20)),
    // ── Triceps ──
    "pushdown" to ExerciseDef(
        id = "pushdown", name = "Cable pushdown", group = "Triceps", view = "side",
        cameraHint = "Phone side-on at chest height. Whole torso and the working arm in frame.",
        needs = ARMS_SIDE, repStart = 70.0, repEnd = 172.0, primary = Joint.ELBOW,
        thresholds = mapOf("lockout" to 165.0, "upperArm" to 35.0, "torsoLean" to 18.0, "eccentricMs" to 0.0),
        faults = listOf(
            upperArmFault("Pin your elbows to your ribs. You are pressing, not pushing down."),
            torsoLeanFault("Stand tall. Stop leaning your bodyweight into it."),
            FaultRule("lockout", "Full extension. Squeeze at the bottom.", "end") { c -> c.jointAngle(Joint.ELBOW) < c.t("lockout") },
        ),
        equipment = "cable", compound = false, avoidFor = listOf("elbow"), loadRatio = 0.25,
    ),
    "skullcrusher" to ExerciseDef(
        id = "skullcrusher", name = "Skullcrusher", group = "Triceps", view = "side",
        cameraHint = "Phone low and side-on, roughly bench height, level with your shoulder.",
        needs = ARMS_SIDE, repStart = 163.0, repEnd = 60.0, primary = Joint.ELBOW,
        thresholds = mapOf("lockout" to 160.0, "upperArmTarget" to 92.0, "upperArmTol" to 20.0, "depth" to 72.0, "eccentricMs" to 700.0),
        faults = listOf(
            fixedUpperArmFault("Upper arms still. You are turning it into a pullover."),
            lockoutFault("Lock it out at the top."),
            shortRangeFault("Go deeper. Bring it to your forehead."),
            fastEccentricFault("Slow the negative down."),
        ),
        equipment = "barbell", compound = false, avoidFor = listOf("elbow"), loadRatio = 0.20,
    ),
    "overheadExtension" to ExerciseDef(
        id = "overheadExtension", name = "Overhead cable extension", group = "Triceps", view = "side",
        cameraHint = "Phone side-on at chest height, facing away from the stack. Full overhead reach in frame.",
        needs = ARMS_SIDE, repStart = 165.0, repEnd = 65.0, primary = Joint.ELBOW,
        thresholds = mapOf("lockout" to 158.0, "upperArmTarget" to 158.0, "upperArmTol" to 22.0, "depth" to 80.0, "eccentricMs" to 500.0),
        faults = listOf(
            fixedUpperArmFault("Upper arms stay overhead. Only the elbow moves."),
            lockoutFault("Squeeze it out straight at the top."),
            shortRangeFault("Deeper. Let it stretch behind your head."),
            fastEccentricFault("Slow the negative down."),
        ),
        equipment = "cable", compound = false, avoidFor = listOf("elbow"), loadRatio = 0.20,
    ),
    "dip" to ExerciseDef(
        id = "dip", name = "Triceps dip", group = "Triceps", view = "side",
        cameraHint = "Phone side-on at chest height, 2 m away. Whole body in frame.",
        needs = ARMS_SIDE, repStart = 168.0, repEnd = 85.0, primary = Joint.ELBOW,
        thresholds = mapOf("lockout" to 162.0, "torsoLean" to 22.0, "depth" to 100.0, "eccentricMs" to 500.0),
        faults = listOf(
            shortRangeFault("Deeper. Upper arms to parallel."),
            torsoLeanFault("Stay upright. Leaning forward turns this into a chest dip."),
            lockoutFault("Lock the elbows out at the top."),
            fastEccentricFault("Control the descent."),
        ),
        equipment = "bodyweight", compound = true, avoidFor = listOf("shoulder", "elbow"), loadRatio = 0.0,
    ),
)

/**
 * The catalogue every other file reads. Stamps each fault's `severity` from SAFETY right after
 * construction — the Kotlin equivalent of exercises.js's own module-load-time
 * `for (...) f.severity = isSafetyFault(id, f.id) ? 'safety' : 'efficiency'` loop (top-level
 * `init {}` isn't legal outside a class/object, so this runs it inline via a plain function call
 * instead of a lazy/init trick).
 */
val EXERCISES: Map<String, ExerciseDef> = rawExercises().also { map ->
    for ((id, ex) in map) for (f in ex.faults) f.severity = if (isSafetyFault(id, f.id)) "safety" else "efficiency"
}

val EQUIPMENT = listOf("barbell", "dumbbell", "cable", "bodyweight")
val INJURIES = listOf("shoulder", "elbow", "lowerBack", "knee")
val GROUPS = listOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Legs")

fun byGroup(group: String): List<ExerciseDef> = EXERCISES.values.filter { it.group == group }

// ── calibration — direct port of exercises.js's calibrate() ──────────────────────────────
const val MIN_RANGE_DEG = 25.0

data class CalibrationSample(val primary: Double)
data class CalibrationPatch(val repStart: Double, val repEnd: Double, val lockout: Double?, val depth: Double?)

fun calibrate(exId: String, samples: List<CalibrationSample>): CalibrationPatch? {
    val ex = EXERCISES[exId] ?: return null
    val primary = samples.map { it.primary }.filter { it.isFinite() }.sorted()
    if (primary.size < 30) return null
    fun percentile(sorted: List<Double>, p: Double) = sorted[min(sorted.size - 1, (p * sorted.size).toInt())]
    val lo = percentile(primary, 0.05)
    val hi = percentile(primary, 0.95)
    if (hi - lo < MIN_RANGE_DEG) return null
    val margin = 5.0
    val towardsHigh = ex.repEnd > ex.repStart
    val repStart = Math.round(if (towardsHigh) lo + margin else hi - margin).toDouble()
    val repEnd = Math.round(if (towardsHigh) hi - margin else lo + margin).toDouble()
    val lockout = if (ex.thresholds.containsKey("lockout")) min(180.0, Math.round(hi - margin).toDouble()) else null
    val depth = if (ex.thresholds.containsKey("depth")) max(10.0, Math.round(lo + margin).toDouble()) else null
    return CalibrationPatch(repStart, repEnd, lockout, depth)
}

// ── camera-view check — direct port of cameraCheck() ──────────────────────────────────────
val VIEW_OK = mapOf("side" to 0.42, "front" to 0.55)

data class CameraCheckResult(val spread: Double, val view: String, val ok: Boolean)

fun cameraCheck(leftShoulder: Pt?, rightShoulder: Pt?, leftHip: Pt?, rightHip: Pt?, wanted: String): CameraCheckResult? {
    if (leftShoulder == null || rightShoulder == null || leftHip == null || rightHip == null) return null
    if (min(leftShoulder.visibility, rightShoulder.visibility) < 0.4) return null
    val midShoulderY = (leftShoulder.y + rightShoulder.y) / 2
    val midHipY = (leftHip.y + rightHip.y) / 2
    val torso = abs(midHipY - midShoulderY)
    if (torso < 1e-3) return null
    val spread = abs(leftShoulder.x - rightShoulder.x) / torso
    val view = if (spread < VIEW_OK.getValue("side")) "side" else "front"
    val ok = if (wanted == "side") spread < VIEW_OK.getValue("front") else spread > VIEW_OK.getValue("side")
    return CameraCheckResult(spread, view, ok)
}

// ── movement-engine constants — direct port of exercises.js's step()-adjacent constants ────
// Landmarks are assumed pre-smoothed (legacy's filter.js One-Euro pass) before this second,
// lighter EMA on the primary angle (pose analysis has since been removed)
// this port carries (no One-Euro equivalent is wired to ML Kit's raw landmarks yet).
const val EMA_ALPHA = 0.6
const val HYSTERESIS = 12.0   // degrees of slop around each rep endpoint
const val HOLD_FRAMES = 3     // a fault must survive this many frames before it fires
const val MIN_JOINT_VIS = 0.5 // a joint below this confidence cannot trigger a correction
const val MIN_REP_MS = 500L   // below this, a completed "rep" was kit being moved, not a lift
const val SIDE_LOST_VIS = 0.35
const val SIDE_LOST_FRAMES = 15
