package com.example.ui.screens

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

/** `onPoseDetected` also receives the pose's own (width, height) — needed to normalize ML Kit's
 *  pixel-space landmark positions into [0,1] image-plane coordinates (see Exercises.kt's
 *  landmark-space gap note). Swapped for 90°/270° rotation, since ML Kit reports landmark
 *  positions against the rotated image, not the raw sensor buffer. */
class PoseAnalyzer(private val onPoseDetected: (Pose, Int, Int) -> Unit) : ImageAnalysis.Analyzer {
    private val options = PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
        .build()
    private val detector = PoseDetection.getClient(options)

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotation = imageProxy.imageInfo.rotationDegrees
            val (w, h) = if (rotation == 90 || rotation == 270) mediaImage.height to mediaImage.width else mediaImage.width to mediaImage.height
            val image = InputImage.fromMediaImage(mediaImage, rotation)
            detector.process(image)
                .addOnSuccessListener { pose ->
                    onPoseDetected(pose, w, h)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
