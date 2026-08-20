package com.example.domain

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Face geometry: alignment, head pose, and the regions everything else measures inside.
 *
 * Pure. No Android, no MediaPipe import, no pixels — it takes a list of normalized [0,1] image
 * landmarks and returns numbers. That is deliberate: this is the layer that decides WHERE to
 * measure, and getting it wrong silently corrupts every signal built on top, so it has to be
 * testable without a camera.
 *
 * LANDMARK INDICES ARE NOT REMEMBERED, THEY ARE THE LIBRARY'S OWN, and live in [Topology] for
 * exactly the reason its own header gives.
 *
 * MediaPipe's left/right are the SUBJECT's left and right, not the viewer's. A selfie camera also
 * mirrors the image. Both facts are handled in one place — see `mirrored` in [regions].
 *
 * Ported from `www/face/geometry.js`.
 */
object Geometry {
    data class Point(val x: Double, val y: Double)

    /** Frame-space point: `a` along the eye axis, `d` down the face, both in eye-distance units. */
    data class FramePoint(val a: Double, val d: Double)

    val EYE_LEFT: List<Int> = Topology.RINGS.getValue("leftEye").vertices
    val EYE_RIGHT: List<Int> = Topology.RINGS.getValue("rightEye").vertices
    val IRIS_LEFT: List<Int> = Topology.RINGS.getValue("leftIris").vertices
    val IRIS_RIGHT: List<Int> = Topology.RINGS.getValue("rightIris").vertices
    val FACE_OVAL: List<Int> = Topology.RINGS.getValue("faceOval").vertices

    /** Outer eye corners, the two most stable points on a face for alignment. From the eye sets. */
    private const val OUTER_EYE_LEFT = 263
    private const val OUTER_EYE_RIGHT = 33

    /** Chin and forehead extremes of the oval, used for vertical anchoring. */
    private const val CHIN = 152
    private const val FOREHEAD_TOP = 10
    private const val NOSE_TIP = 1

    private fun at(lm: List<Point>?, i: Int): Point? = lm?.getOrNull(i)
    private fun mean(xs: List<Double>) = xs.sum() / xs.size

    /** Centroid of a set of landmark indices, or null if any is missing. */
    fun centroid(lm: List<Point>, indices: List<Int>): Point? {
        val pts = indices.map { at(lm, it) }
        if (pts.any { it == null }) return null
        val real = pts.filterNotNull()
        return Point(mean(real.map { it.x }), mean(real.map { it.y }))
    }

    data class Alignment(
        val scale: Double,
        val roll: Double,
        val eyeMid: Point,
        val chin: Point,
        val top: Point,
        val height: Double,
    )

    /**
     * The frame of reference every measurement is expressed in.
     *
     * `scale` is the distance between the outer eye corners. For ONE person that distance is fixed
     * in the real world, so it is a direct proxy for how far the camera was, which is what makes a
     * region measured today comparable with the same region measured last week. Iris width is the
     * more famous choice (it barely varies between humans), but between-person constancy buys
     * nothing here: every comparison this app makes is a person against their own history. Eye
     * corners survive blinks and are visible at more head angles, which does buy something.
     *
     * `roll` lets regions rotate with the head instead of sliding off the cheek when someone tilts.
     */
    fun alignment(lm: List<Point>): Alignment? {
        val l = at(lm, OUTER_EYE_LEFT) ?: return null
        val r = at(lm, OUTER_EYE_RIGHT) ?: return null
        val chin = at(lm, CHIN) ?: return null
        val top = at(lm, FOREHEAD_TOP) ?: return null

        val dx = l.x - r.x
        val dy = l.y - r.y
        val scale = hypot(dx, dy)
        if (scale < 1e-6) return null

        return Alignment(
            scale = scale,
            roll = atan2(dy, dx),
            eyeMid = Point((l.x + r.x) / 2, (l.y + r.y) / 2),
            chin = chin,
            top = top,
            height = hypot(chin.x - top.x, chin.y - top.y),
        )
    }

    data class HeadPose(val yaw: Double, val pitch: Double?, val roll: Double)

    /**
     * Head orientation, without the transformation matrix.
     *
     * [poseFromMatrix] is the better source and is used when present. This is the fallback and the
     * cross-check, from geometry alone:
     *
     *   yaw   the nose sits mid-way between the eye corners when facing forward. Turning the head
     *         slides it toward the near eye. Measured as that offset over the eye distance, so it
     *         is scale-free: -1 fully left, 0 forward, +1 fully right.
     *   pitch the eye line sits at a fixed fraction of face height when level. Nodding moves it.
     *   roll  straight from the eye-corner angle.
     *
     * All three are ratios of distances that shrink together with camera distance, so none of them
     * depends on how close the phone is.
     */
    fun headPose(lm: List<Point>): HeadPose? {
        val a = alignment(lm) ?: return null
        val nose = at(lm, NOSE_TIP) ?: return null

        // Project the nose onto the eye axis; the perpendicular offset from the midpoint is yaw.
        val ux = cos(a.roll)
        val uy = sin(a.roll)
        val yaw = ((nose.x - a.eyeMid.x) * ux + (nose.y - a.eyeMid.y) * uy) / a.scale

        // How far down the face the eye line sits. ~0.35 looking straight ahead; smaller looking up.
        val eyeDrop = if (a.height > 1e-6) {
            hypot(a.eyeMid.x - a.top.x, a.eyeMid.y - a.top.y) / a.height
        } else {
            null
        }

        return HeadPose(yaw = yaw, pitch = eyeDrop?.minus(0.35), roll = a.roll)
    }

    /**
     * Head pose from FaceLandmarker's own 4x4 transformation matrix, when available.
     *
     * Column-major, as MediaPipe returns it. The upper-left 3x3 is the rotation, so the Euler
     * angles come straight out of it — no landmark heuristics, no tuned constants. Prefer this;
     * [headPose] exists for when the matrix is absent and as something to sanity-check it against.
     */
    fun poseFromMatrix(m: DoubleArray?): HeadPose? {
        if (m == null || m.size != 16) return null
        fun r(row: Int, col: Int) = m[col * 4 + row]
        val sy = hypot(r(0, 0), r(1, 0))
        if (sy < 1e-6) return null
        return HeadPose(
            yaw = atan2(-r(2, 0), sy),
            pitch = atan2(r(2, 1), r(2, 2)),
            roll = atan2(r(1, 0), r(0, 0)),
        )
    }

    data class Box(val cx: Double, val cy: Double, val half: Double)

    data class Regions(
        val forehead: Box,
        val leftCheek: Box,
        val rightCheek: Box,
        val leftUnderEye: Box,
        val rightUnderEye: Box,
        val nose: Box,
        val chin: Box,
    ) {
        /** All seven, named — for callers that iterate rather than destructure. */
        fun asMap(): Map<String, Box> = mapOf(
            "forehead" to forehead, "leftCheek" to leftCheek, "rightCheek" to rightCheek,
            "leftUnderEye" to leftUnderEye, "rightUnderEye" to rightUnderEye, "nose" to nose,
            "chin" to chin,
        )
    }

    /**
     * The regions measured inside, as squares in normalized image coordinates.
     *
     * Every box is sized as a FRACTION OF EYE DISTANCE and positioned relative to face anchors,
     * never in absolute pixels. That is the whole trick: a cheek patch is then the same piece of
     * face whether the phone was at arm's length or a foot away, which is the precondition for
     * comparing today against last month at all.
     *
     * Boxes are deliberately small and well inside their features. A forehead patch that
     * occasionally catches hair, or a cheek patch that catches the jaw shadow, does not produce a
     * slightly worse measurement — it produces a confident measurement of hair.
     *
     * `mirrored` handles the selfie camera. A front camera flips the image, so the subject's left
     * cheek appears on the right of the frame. Getting this wrong swaps left and right for every
     * asymmetry comparison, which would look like a real finding rather than a bug.
     */
    fun regions(lm: List<Point>, mirrored: Boolean = false): Regions? {
        val a = alignment(lm) ?: return null

        val s = a.scale
        val ux = cos(a.roll)
        val uy = sin(a.roll)
        // Face-down direction, perpendicular to the eye axis.
        val dx = -uy
        val dy = ux

        /** Place a box `along` the eye axis and `down` the face, both in eye-distance units. */
        fun box(along: Double, down: Double, size: Double) = Box(
            cx = a.eyeMid.x + ux * along * s + dx * down * s,
            cy = a.eyeMid.y + uy * along * s + dy * down * s,
            half = (size * s) / 2,
        )

        val side = if (mirrored) -1.0 else 1.0

        return Regions(
            // Above the brows, inside the hairline. Kept narrow: foreheads vary hugely in height.
            forehead = box(0.0, -0.45, 0.45),
            // Out along the eye axis and below the eye, the flat of the cheek, clear of the nose
            // fold and clear of the jaw.
            leftCheek = box(0.42 * side, 0.45, 0.38),
            rightCheek = box(-0.42 * side, 0.45, 0.38),
            // Directly under each eye, above the cheek box. Small: the useful area is small.
            leftUnderEye = box(0.30 * side, 0.16, 0.20),
            rightUnderEye = box(-0.30 * side, 0.16, 0.20),
            // Bridge of the nose, between the eyes and above the tip.
            nose = box(0.0, 0.28, 0.26),
            // Between the lower lip and the chin point.
            chin = box(0.0, 0.95, 0.30),
        )
    }

    // ── anatomical geometry ──────────────────────────────────────────────────────────────────
    //
    // Everything above places SQUARES by formula, and that is all the per-frame framing gate needs:
    // it only asks "is the area I would measure inside the picture", thirty times a second, on a
    // phone.
    //
    // Everything below places POLYGONS built out of the mesh's own contour rings — the actual
    // eyebrows, the actual eye lids, the actual face oval. That costs more and is computed once per
    // capture, and it buys two things a square cannot have:
    //
    //   The boundaries are anatomy. A forehead bounded by the brow ring underneath and the oval arc
    //   above is the forehead on any face; a square 0.45 eye-distances up is the forehead on some.
    //
    //   THERE IS NO `mirrored` FLAG DOWN HERE, and its absence is the point. Squares had to be
    //   placed by formula, so a selfie-flipped image had to flip the formula, and getting that
    //   wrong swapped left and right for every asymmetry signal. Landmark 263 is the subject's left
    //   eye wherever it lands in the frame, so a polygon built from it needs no such correction and
    //   cannot acquire one.
    //
    // These polygons are deliberately GENEROUS — a future mask/segmentation layer disposes of the
    // rest (clipping to the oval, subtracting eyes/brows/lips, eroding the edge). A region that is
    // slightly too big survives that; one that is too small has already thrown away skin nobody can
    // get back.

    /** Region polygons are inset from their bounding anatomy by these fractions of eye distance. */
    object Inset {
        /** Distance kept between the brow ring and the bottom of the forehead patch. */
        const val BROW_CLEARANCE = 0.10
        /** Fraction of the brow-to-oval-top span left unmeasured at the top (the hairline). */
        const val HAIRLINE = 0.34
        /** How far the oval is pulled inward before it bounds a cheek. */
        const val OVAL_INSET = 0.16
        const val UNDER_EYE_TOP = 0.06
        const val UNDER_EYE_BOTTOM = 0.30
        const val CHEEK_TOP = 0.36
    }

    data class Frame(
        val origin: Point,
        val ux: Double,
        val uy: Double,
        val dx: Double,
        val dy: Double,
        val scale: Double,
        val roll: Double,
    )

    private fun dot(px: Double, py: Double, ux: Double, uy: Double) = px * ux + py * uy

    /**
     * The face's own coordinate system: along the eye axis, and down the face.
     *
     * Both axes are in EYE-DISTANCE units, so every number expressed in this frame is free of how
     * far away the phone was. `a` is positive toward the subject's left eye, `d` positive toward
     * the chin.
     */
    fun frame(lm: List<Point>): Frame? {
        val a = alignment(lm) ?: return null
        val ux = cos(a.roll)
        val uy = sin(a.roll)
        return Frame(a.eyeMid, ux, uy, -uy, ux, a.scale, a.roll)
    }

    /** Image point -> frame coordinates. */
    fun project(p: Point, f: Frame): FramePoint {
        val vx = p.x - f.origin.x
        val vy = p.y - f.origin.y
        return FramePoint(a = dot(vx, vy, f.ux, f.uy) / f.scale, d = dot(vx, vy, f.dx, f.dy) / f.scale)
    }

    /** Frame coordinates -> image point. */
    fun unproject(q: FramePoint, f: Frame): Point = Point(
        x = f.origin.x + (f.ux * q.a + f.dx * q.d) * f.scale,
        y = f.origin.y + (f.uy * q.a + f.dy * q.d) * f.scale,
    )

    /** Convex hull, monotone chain. Used for the rings the library does not give as a closed loop. */
    fun hull(pts: List<FramePoint>): List<FramePoint> {
        if (pts.size < 3) return pts
        val s = pts.sortedWith(compareBy({ it.a }, { it.d }))
        fun cross(o: FramePoint, p: FramePoint, q: FramePoint) =
            (p.a - o.a) * (q.d - o.d) - (p.d - o.d) * (q.a - o.a)
        fun half(src: List<FramePoint>): List<FramePoint> {
            val out = mutableListOf<FramePoint>()
            for (p in src) {
                while (out.size >= 2 && cross(out[out.size - 2], out[out.size - 1], p) <= 0) {
                    out.removeAt(out.size - 1)
                }
                out.add(p)
            }
            out.removeAt(out.size - 1)
            return out
        }
        return half(s) + half(s.reversed())
    }

    /** Ray casting. Points on the boundary may fall either way; callers erode, so it does not matter. */
    fun inPolygon(a: Double, d: Double, poly: List<FramePoint>): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val pi = poly[i]
            val pj = poly[j]
            if ((pi.d > d) != (pj.d > d) &&
                a < (pj.a - pi.a) * (d - pi.d) / (pj.d - pi.d) + pi.a
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /** A named ring as frame-space points, hulled where the library did not hand us a closed loop. */
    private fun ring(lm: List<Point>, f: Frame, name: String): List<FramePoint>? {
        val spec = Topology.RINGS.getValue(name)
        val pts = spec.vertices.map { at(lm, it) }
        if (pts.any { it == null }) return null
        val proj = pts.filterNotNull().map { project(it, f) }
        return if (spec.hull) hull(proj) else proj
    }

    /** Push every vertex of a polygon toward its centroid by `t`. Shrinks, never reorders. */
    private fun shrink(poly: List<FramePoint>, t: Double): List<FramePoint> {
        val ca = poly.sumOf { it.a } / poly.size
        val cd = poly.sumOf { it.d } / poly.size
        return poly.map { FramePoint(it.a + (ca - it.a) * t, it.d + (cd - it.d) * t) }
    }

    /** Grow a polygon away from its centroid by `t`. Exclusions are grown; regions never are. */
    private fun grow(poly: List<FramePoint>, t: Double) = shrink(poly, -t)

    private fun minBy(pts: List<FramePoint>, key: (FramePoint) -> Double) = pts.minByOrNull(key)!!
    private fun maxBy(pts: List<FramePoint>, key: (FramePoint) -> Double) = pts.maxByOrNull(key)!!

    data class Anatomy(
        val frame: Frame,
        /** Clipped against, never measured: the silhouette. */
        val bounds: List<FramePoint>,
        /** Present keys: leftCheek, rightCheek, leftUnderEye, rightUnderEye, nose, and optionally
         *  forehead / chin, which are experimental and may be absent on a real face. */
        val regions: Map<String, List<FramePoint>>,
        /** Grown before subtraction — an eyelash inside a "skin" measurement costs less than the
         *  skin lost keeping it out. */
        val exclusions: List<List<FramePoint>>,
    )

    /**
     * The regions the pipeline measures inside, as polygons in frame coordinates.
     *
     * Returns null when any ring is missing a vertex, rather than a partial set — a region built
     * from half a ring is not a noisier region, it is a different piece of face.
     *
     * `chin` is included and is flagged experimental by the CALLER, not here. Geometry has no
     * opinion about whether a beard makes it unmeasurable; that is what the validation phase is for.
     */
    fun anatomy(lm: List<Point>): Anatomy? {
        val f = frame(lm) ?: return null

        val oval = ring(lm, f, "faceOval") ?: return null
        val lEye = ring(lm, f, "leftEye") ?: return null
        val rEye = ring(lm, f, "rightEye") ?: return null
        val lBrow = ring(lm, f, "leftBrow") ?: return null
        val rBrow = ring(lm, f, "rightBrow") ?: return null
        val lips = ring(lm, f, "lips") ?: return null

        val brows = lBrow + rBrow
        val browTop = minBy(brows) { it.d }.d
        val ovalTop = minBy(oval) { it.d }.d
        val inner = shrink(oval, Inset.OVAL_INSET)

        // Forehead: bounded below by the brows themselves, above by the oval's forehead arc pulled
        // down out of the hairline. Both boundaries are the mesh's; only the two fractions are ours.
        val lift = (browTop - ovalTop) * Inset.HAIRLINE
        val foreheadTop = inner.filter { it.d < browTop }
            .map { FramePoint(it.a, it.d + lift) }
            .sortedBy { it.a }
        val foreheadBottom = brows
            .map { FramePoint(it.a, browTop - Inset.BROW_CLEARANCE) }
            .sortedByDescending { it.a }
        val forehead = if (foreheadTop.size >= 2) foreheadTop + foreheadBottom else null

        /** One side. `sign` is +1 for the subject's left, which is +a in the frame. */
        fun side(eye: List<FramePoint>, sign: Double): Pair<List<FramePoint>, List<FramePoint>> {
            val lid = maxBy(eye) { it.d }.d // lower lid
            val outer = maxBy(eye) { it.a * sign }.a // outer corner
            val innerEye = minBy(eye) { it.a * sign }.a // inner corner
            val lipCorner = maxBy(lips) { it.a * sign }
            fun edge(d: Double): Double {
                // How far out the inset oval reaches at this height, the lateral bound of the cheek.
                val near = inner.filter { it.a * sign > 0 }
                if (near.isEmpty()) return outer
                return near.minByOrNull { abs(it.d - d) }!!.a
            }

            val underEye = listOf(
                FramePoint(innerEye, lid + Inset.UNDER_EYE_TOP),
                FramePoint(outer, lid + Inset.UNDER_EYE_TOP),
                FramePoint(outer, lid + Inset.UNDER_EYE_BOTTOM),
                FramePoint(innerEye, lid + Inset.UNDER_EYE_BOTTOM),
            )

            val top = lid + Inset.CHEEK_TOP
            val bottom = lipCorner.d
            val cheek = listOf(
                FramePoint(innerEye, top),
                FramePoint(edge(top), top),
                FramePoint(edge(bottom), bottom),
                FramePoint(lipCorner.a - sign * 0.10, bottom),
            )
            return underEye to cheek
        }

        val (leftUnderEye, leftCheek) = side(lEye, 1.0)
        val (rightUnderEye, rightCheek) = side(rEye, -1.0)

        // Nose bridge: between the inner eye corners, above the lip line. Narrow, because the wings
        // and nostrils are neither flat nor lit like the rest of the face.
        val lInner = minBy(lEye) { it.a }.a
        val rInner = maxBy(rEye) { it.a }.a
        val lipTop = minBy(lips) { it.d }.d
        val eyeLine = max(maxBy(lEye) { it.d }.d, maxBy(rEye) { it.d }.d)
        val nose = listOf(
            FramePoint(lInner * 0.55, eyeLine),
            FramePoint(rInner * 0.55, eyeLine),
            FramePoint(rInner * 0.40, (eyeLine + lipTop) / 2),
            FramePoint(lInner * 0.40, (eyeLine + lipTop) / 2),
        )

        // Chin: between the lip ring and the inset oval's bottom. The region most exposed to facial
        // hair, and the one the validation phase exists to accept or reject.
        val lipBottom = maxBy(lips) { it.d }.d
        val ovalBottom = maxBy(inner) { it.d }.d
        val chinHalf = abs(maxBy(lips) { it.a }.a - minBy(lips) { it.a }.a) * 0.30
        val chin = if (ovalBottom > lipBottom) {
            listOf(
                FramePoint(-chinHalf, lipBottom + 0.08),
                FramePoint(chinHalf, lipBottom + 0.08),
                FramePoint(chinHalf * 0.8, ovalBottom),
                FramePoint(-chinHalf * 0.8, ovalBottom),
            )
        } else {
            null
        }

        val regions = buildMap {
            forehead?.let { put("forehead", it) }
            put("leftCheek", leftCheek)
            put("rightCheek", rightCheek)
            put("leftUnderEye", leftUnderEye)
            put("rightUnderEye", rightUnderEye)
            put("nose", nose)
            chin?.let { put("chin", it) }
        }

        return Anatomy(
            frame = f,
            bounds = inner,
            regions = regions,
            exclusions = listOf(
                grow(lEye, 0.18), grow(rEye, 0.18),
                grow(lBrow, 0.22), grow(rBrow, 0.22),
                grow(lips, 0.15),
            ),
        )
    }

    data class PixelBox(
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
        /** A box the face has pushed off the edge of the frame is not a smaller sample of the same
         *  thing, it is a different piece of face. Callers must drop it rather than measure it. */
        val clipped: Boolean,
    )

    /** Turn a normalized box into integer pixel bounds, clipped to the image. */
    fun toPixels(box: Box, width: Int, height: Int): PixelBox {
        val x0 = Math.round((box.cx - box.half) * width).toInt()
        val y0 = Math.round((box.cy - box.half) * height).toInt()
        val x1 = Math.round((box.cx + box.half) * width).toInt()
        val y1 = Math.round((box.cy + box.half) * height).toInt()
        return PixelBox(
            x = max(0, x0),
            y = max(0, y0),
            w = min(width, x1) - max(0, x0),
            h = min(height, y1) - max(0, y0),
            clipped = x0 < 0 || y0 < 0 || x1 > width || y1 > height,
        )
    }
}
