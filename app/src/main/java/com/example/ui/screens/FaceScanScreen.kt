package com.example.ui.screens

import android.Manifest
import android.app.Application
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.FaceCaptureIssue
import com.example.domain.FaceMetrics
import com.example.domain.FaceScan
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

/** Guided face scan: real-time ML Kit face detection drives the on-screen guidance, and only a
 *  sustained (HOLD_FRAMES_TO_CAPTURE consecutive) valid frame ever gets saved — never a capture
 *  before FaceScan.diagnose reports no issues. */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun FaceScanScreen(
    onCaptured: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: FaceScanViewModel = viewModel(factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(LocalContext.current.applicationContext as Application)),
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val scope = rememberCoroutineScope()

    var faceCount by remember { mutableStateOf(0) }
    var metrics by remember { mutableStateOf<FaceMetrics?>(null) }
    var goodFrames by remember { mutableStateOf(0) }
    var captured by remember { mutableStateOf(false) }

    val issue = FaceScan.diagnose(faceCount, metrics)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (cameraPermissionState.status.isGranted) {
            FaceScanCameraPreview { count, m ->
                if (!captured) {
                    faceCount = count
                    metrics = m
                    val nowOk = FaceScan.diagnose(count, m) == null
                    goodFrames = if (nowOk) goodFrames + 1 else 0
                    if (goodFrames >= FaceScan.HOLD_FRAMES_TO_CAPTURE && m != null) {
                        captured = true
                        scope.launch {
                            viewModel.saveCapture(m)
                            onCaptured()
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Camera access is needed for a guided face scan.", color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) { Text("Grant Camera Permission") }
            }
        }

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPaddingSafe().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButtonBack(onBack)
        }

        // Guidance overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val ringColor = if (issue == null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(ringColor),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (cameraPermissionState.status.isGranted) FaceScan.guidance(issue) else "Position your face in the frame",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (issue == null && goodFrames > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (goodFrames.toFloat() / FaceScan.HOLD_FRAMES_TO_CAPTURE).coerceAtMost(1f) },
                    modifier = Modifier.fillMaxWidth(0.6f).clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IconButtonBack(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ponytail: a real status-bar inset would use WindowInsets; a fixed top padding is enough for one
// full-bleed camera screen and avoids pulling in an extra accompanist-insets dependency.
private fun Modifier.statusBarsPaddingSafe(): Modifier = this.padding(top = 24.dp)

@Composable
private fun FaceScanCameraPreview(onResult: (faceCount: Int, metrics: FaceMetrics?) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // A dedicated thread, not the main executor: FaceLandmarker's detectAsync still does real work
    // on the calling thread before the result is delivered later (only the RESULT is asynchronous),
    // and running that on the main executor would jank the preview on every frame.
    val analyzerExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    // ponytail: constructed synchronously during composition, which loads the ~3.7 MB model and
    // builds its native graph on the main thread — a one-time hitch of well under a second on this
    // screen's own entry transition, not a per-frame cost. Move to a LaunchedEffect on
    // Dispatchers.Default, holding a nullable FaceAnalyzer? until ready, if that hitch ever shows up
    // in a real frame-time trace.
    val faceAnalyzer = remember { FaceAnalyzer(context, onResult) }

    DisposableEffect(Unit) {
        onDispose {
            // Releases the loaded model's native graph, and stops the camera from feeding a
            // detector nobody is reading results from anymore.
            faceAnalyzer.close()
            analyzerExecutor.shutdown()
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    val imageAnalyzer = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            // MediaPipe's live-stream detectAsync path only accepts an RGBA_8888-backed
            // android.media.Image -- confirmed by an actual crash on-device:
            // "UnsupportedOperationException: Android media image must use RGBA_8888 config" out
            // of AndroidPacketCreator.createImage. The default output here is YUV_420_888 (what ML
            // Kit was fine with), which this specific MediaPipe code path rejects outright.
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis -> analysis.setAnalyzer(analyzerExecutor, faceAnalyzer) }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageAnalyzer,
                    )
                } catch (exc: Exception) {
                    // Handle exceptions
                }
            }, executor)
            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )
}
