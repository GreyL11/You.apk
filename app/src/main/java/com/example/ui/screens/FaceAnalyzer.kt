package com.example.ui.screens

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.domain.FaceMetrics
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/** Rotation-aware ImageAnalysis.Analyzer for ML Kit Face Detection — same shape as PoseAnalyzer.
 *  Also samples real luma/sharpness/exposure off the Y plane so FaceScan.diagnose can gate on
 *  actual image quality, not just face geometry. Euler angles and bounding box come free from
 *  ML Kit's default (non-classification) detector — no extra options needed. */
class FaceAnalyzer(private val onResult: (faceCount: Int, metrics: FaceMetrics?) -> Unit) : ImageAnalysis.Analyzer {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build()
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val (w, h) = if (rotation == 90 || rotation == 270) mediaImage.height to mediaImage.width else mediaImage.width to mediaImage.height
        val image = InputImage.fromMediaImage(mediaImage, rotation)
        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.size != 1) {
                    onResult(faces.size, null)
                } else {
                    val box = faces[0].boundingBox
                    val (luma, sharpness, clipFraction) = sampleLumaMetrics(imageProxy)
                    onResult(
                        1,
                        FaceMetrics(
                            faceSizeFraction = (box.width().toDouble() * box.height()) / (w.toDouble() * h),
                            centerXFraction = box.exactCenterX().toDouble() / w,
                            centerYFraction = box.exactCenterY().toDouble() / h,
                            yawRad = Math.toRadians(faces[0].headEulerAngleY.toDouble()),
                            pitchRad = Math.toRadians(faces[0].headEulerAngleX.toDouble()),
                            rollRad = Math.toRadians(faces[0].headEulerAngleZ.toDouble()),
                            sharpnessVariance = sharpness,
                            exposureClipFraction = clipFraction,
                            luma = luma,
                        ),
                    )
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    // ponytail: a coarse (every-8th-pixel) gradient-energy proxy for focus, not a calibrated
    // sharpness metric — real signal off the Y plane, but the normalizing constant below is an
    // approximation until it's tuned against real device captures against FacePipeline's 0.15 gate.
    private fun sampleLumaMetrics(imageProxy: ImageProxy): Triple<Double, Double, Double> {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer.duplicate() // never touch the analyzer's own buffer position
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val rowStride = plane.rowStride
        val width = imageProxy.width
        val height = imageProxy.height
        val step = 8

        var sum = 0L
        var clipped = 0
        var count = 0
        var gradSumSq = 0.0
        var gradCount = 0
        var y = 0
        while (y < height) {
            var prev = -1
            var x = 0
            while (x < width) {
                val idx = y * rowStride + x
                if (idx >= bytes.size) break
                val v = bytes[idx].toInt() and 0xFF
                sum += v
                count++
                if (v >= 250) clipped++
                if (prev >= 0) {
                    val d = (v - prev).toDouble()
                    gradSumSq += d * d
                    gradCount++
                }
                prev = v
                x += step
            }
            y += step
        }
        val luma = if (count > 0) (sum.toDouble() / count) / 255.0 else 0.0
        val sharpness = if (gradCount > 0) (gradSumSq / gradCount) / (255.0 * 255.0) else 0.0
        val exposureClip = if (count > 0) clipped.toDouble() / count else 0.0
        return Triple(luma, sharpness, exposureClip)
    }
}
