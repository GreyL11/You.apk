package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.example.domain.FaceMetrics
import com.example.domain.FaceQuality
import com.example.domain.Geometry
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/**
 * Rotation-aware ImageAnalysis.Analyzer driving MediaPipe's FaceLandmarker — 478 landmarks and the
 * facial transformation matrix, replacing ML Kit's handful of contour points and Euler angles.
 * Every downstream measurement ([Geometry], [FaceQuality]) needs the full mesh: registration and
 * the anatomical regions key off specific landmark indices ML Kit never exposed.
 *
 * `faceSizeFraction`/`centerXFraction`/`centerYFraction`/`yawRad`/`pitchRad`/`rollRad` are now the
 * quantities [FaceQuality.LIMITS] was actually written against (eye-distance scale, eye midpoint,
 * pose from the mesh or the matrix) rather than a bounding-box area ratio and ML Kit's own Euler
 * angles — a second mismatch of the same kind [FaceQuality]'s own header describes for sharpness:
 * a threshold only means something against the exact definition it was written for.
 *
 * The front camera's ImageAnalysis stream from CameraX is NOT mirrored (only the on-screen Preview
 * is, for a natural selfie view), so landmarks come back in the true, unmirrored sensor frame —
 * `mirrored = true` belongs on the square-region framing check for exactly the reason geometry.js's
 * own header gives; `anatomy()` needs no such flag.
 */
class FaceAnalyzer(
    context: Context,
    private val onResult: (faceCount: Int, metrics: FaceMetrics?) -> Unit,
) : ImageAnalysis.Analyzer {
    private val mainExecutor = ContextCompat.getMainExecutor(context)

    private val landmarker: FaceLandmarker = FaceLandmarker.createFromOptions(
        context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            // 2, not 1: with numFaces=1, a second face in frame is not reported as a count, it is
            // just silently dropped or replaced — the app would never learn there were two people
            // in frame to ask for just one.
            .setNumFaces(MAX_FACES)
            .setOutputFacialTransformationMatrixes(true)
            .setOutputFaceBlendshapes(false)
            .setResultListener { result, _ -> handleResult(result) }
            .setErrorListener { e ->
                android.util.Log.e("FaceAnalyzer", "FaceLandmarker failed", e)
                mainExecutor.execute { onResult(0, null) }
            }
            .build(),
    )

    /**
     * The frame currently in flight, copied out of its ImageProxy before that proxy is closed.
     *
     * detectAsync's result arrives later, on MediaPipe's own thread — by which point the ImageProxy
     * (and the camera buffer behind it) may already be reused for the next frame, so the quality
     * sampling that needs real pixels reads this copy rather than a possibly-stale proxy.
     *
     * ponytail: one slot, not a queue keyed by timestamp, so a result that arrives after the NEXT
     * frame's analyze() call reads that next frame's pixels against this frame's landmarks — a
     * live 30fps guidance loop self-corrects from that within one frame, and it is not the frame
     * that gets stored. Key by result.timestampMs() against the frame's own timestamp if this ever
     * feeds something that IS stored without a human confirming the capture first.
     */
    @Volatile private var pendingFrame: Frame? = null

    private class Frame(
        /** RGBA_8888 bytes, 4 per pixel — see [FaceScanScreen]'s ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888. */
        val pixels: ByteArray,
        val rowStride: Int,
        val pixelStride: Int,
        val planeW: Int,
        val planeH: Int,
        val rotation: Int,
        val rotatedW: Int,
        val rotatedH: Int,
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val (w, h) = if (rotation == 90 || rotation == 270) {
            mediaImage.height to mediaImage.width
        } else {
            mediaImage.width to mediaImage.height
        }

        val plane = imageProxy.planes[0]
        val buffer = plane.buffer.duplicate() // never touch the analyzer's own buffer position
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        pendingFrame = Frame(
            pixels = bytes,
            rowStride = plane.rowStride,
            pixelStride = plane.pixelStride,
            planeW = imageProxy.width,
            planeH = imageProxy.height,
            rotation = rotation,
            rotatedW = w,
            rotatedH = h,
        )

        val mpImage = MediaImageBuilder(mediaImage).build()
        try {
            landmarker.detectAsync(
                mpImage,
                ImageProcessingOptions.builder().setRotationDegrees(rotation).build(),
                SystemClock.uptimeMillis(),
            )
        } finally {
            // detectAsync copies what it needs out of the MPImage/Image before returning to the
            // caller — the async part is the graph running, not the hand-off — so both are safe to
            // release here rather than waiting for the result callback.
            mpImage.close()
            imageProxy.close()
        }
    }

    private fun handleResult(result: FaceLandmarkerResult) {
        val frame = pendingFrame
        val faces = result.faceLandmarks()
        if (faces.size != 1 || frame == null) {
            mainExecutor.execute { onResult(faces.size, null) }
            return
        }

        val lm = faces[0].map { Geometry.Point(it.x().toDouble(), it.y().toDouble()) }
        val alignment = Geometry.alignment(lm)
        if (alignment == null) {
            // A face was detected but the two outer eye corners or the chin/forehead extremes were
            // not — nothing downstream can be measured against without them.
            mainExecutor.execute { onResult(1, null) }
            return
        }

        // Sized defensively rather than trusted: Geometry.poseFromMatrix has its own size == 16
        // check, but that check runs AFTER indexing here would already have thrown.
        val matrix = result.facialTransformationMatrixes().orElse(null)?.getOrNull(0)
            ?.takeIf { it.size == 16 }
            ?.let { m -> DoubleArray(16) { i -> m[i].toDouble() } }
        val pose = Geometry.poseFromMatrix(matrix) ?: Geometry.headPose(lm)

        // A tight pixel box around every landmark, in the same rotated-frame convention the old ML
        // Kit box used, so the stride-sampled luma/sharpness/exposure read below needs no changes.
        val xs = lm.map { it.x }
        val ys = lm.map { it.y }
        val box = Rect(
            (xs.min() * frame.rotatedW).toInt(), (ys.min() * frame.rotatedH).toInt(),
            (xs.max() * frame.rotatedW).toInt(), (ys.max() * frame.rotatedH).toInt(),
        )
        val patch = sampleFacePatch(frame, box)
        val sharp = FaceQuality.sharpness(patch?.luma, patch?.width ?: 0, patch?.height ?: 0)
        val exposure = FaceQuality.exposure(patch?.luma)

        android.util.Log.d(
            "FaceAnalyzer",
            "size=${"%.3f".format(alignment.scale)} " +
                "sharpness=${"%.3f".format(sharp.value)} (min ${FaceQuality.LIMITS.SHARPNESS_MIN}) " +
                "luma=${"%.3f".format(exposure.meanLuma)} clip=${"%.4f".format(exposure.clipFraction)} " +
                "patch=${patch?.width}x${patch?.height}",
        )

        val metrics = FaceMetrics(
            faceSizeFraction = alignment.scale,
            centerXFraction = alignment.eyeMid.x,
            centerYFraction = alignment.eyeMid.y,
            yawRad = pose?.yaw ?: 0.0,
            // A pitch geometry could not compute (alignment.height near zero) is treated as level
            // rather than as evidence of a bad angle — the same non-penalising choice FaceQuality's
            // own pose() makes for a null pitch.
            pitchRad = pose?.pitch ?: 0.0,
            rollRad = pose?.roll ?: 0.0,
            sharpnessVariance = sharp.value,
            exposureClipFraction = exposure.clipFraction,
            luma = exposure.meanLuma,
        )
        mainExecutor.execute { onResult(1, metrics) }
    }

    private class Patch(val luma: DoubleArray, val width: Int, val height: Int)

    /** Longest edge of the sampled grid. Fixed so the reading does not change with preview resolution. */
    private val gridMax = 96

    /**
     * The face box, as a luminance grid, read from an already-copied frame rather than a live
     * ImageProxy — see [pendingFrame].
     *
     * The frame is RGBA_8888 (see [FaceScanScreen]'s ImageAnalysis config — required by
     * MediaPipe's live-stream path, confirmed by a real crash when the stream was YUV), so each
     * pixel is 4 bytes, R/G/B/A in that order, and luma is the standard BT.601 weighting of the
     * three colour channels rather than a value already sitting in the buffer.
     *
     * Sampled by stride rather than averaged: averaging is a low-pass filter, and low-passing an
     * image before measuring its high-frequency content would erase the thing being measured.
     */
    private fun sampleFacePatch(frame: Frame, box: Rect): Patch? {
        // Rotated-frame box corners back into plane coordinates.
        fun toPlane(x: Int, y: Int): Pair<Int, Int> = when (frame.rotation) {
            90 -> y to (frame.rotatedW - 1 - x)
            180 -> (frame.rotatedW - 1 - x) to (frame.rotatedH - 1 - y)
            270 -> (frame.rotatedH - 1 - y) to x
            else -> x to y
        }

        val (x0, y0) = toPlane(box.left, box.top)
        val (x1, y1) = toPlane(box.right, box.bottom)
        val left = maxOf(0, minOf(x0, x1))
        val right = minOf(frame.planeW - 1, maxOf(x0, x1))
        val top = maxOf(0, minOf(y0, y1))
        val bottom = minOf(frame.planeH - 1, maxOf(y0, y1))

        val boxW = right - left + 1
        val boxH = bottom - top + 1
        // Under a few pixels there is nothing to measure, and FaceQuality reports "no image" rather
        // than a confident zero.
        if (boxW < 8 || boxH < 8) return null

        val step = maxOf(1, maxOf(boxW, boxH) / gridMax)
        val gw = (boxW + step - 1) / step
        val gh = (boxH + step - 1) / step
        if (gw < 3 || gh < 3) return null

        val luma = DoubleArray(gw * gh)
        var gy = 0
        while (gy < gh) {
            var gx = 0
            while (gx < gw) {
                val px = left + gx * step
                val py = top + gy * step
                val idx = py * frame.rowStride + px * frame.pixelStride
                luma[gy * gw + gx] = if (idx + 2 < frame.pixels.size) {
                    val r = frame.pixels[idx].toInt() and 0xFF
                    val g = frame.pixels[idx + 1].toInt() and 0xFF
                    val b = frame.pixels[idx + 2].toInt() and 0xFF
                    (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                } else {
                    0.0
                }
                gx++
            }
            gy++
        }
        return Patch(luma, gw, gh)
    }

    /** Releases the loaded model and its native graph. Call when the owning screen is disposed. */
    fun close() = landmarker.close()

    companion object {
        /** Relative to `assets/` — see app/src/main/assets/face_landmarker.task. */
        private const val MODEL_ASSET = "face_landmarker.task"
        private const val MAX_FACES = 2
    }
}
