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
            .background(Color(0xFF0F1115)),
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
                Text("Camera access is needed for a guided face scan.", color = Color.White, textAlign = TextAlign.Center)
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
            val ringColor = if (issue == null) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.5f)
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(ringColor),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (cameraPermissionState.status.isGranted) FaceScan.guidance(issue) else "Position your face in the frame",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (issue == null && goodFrames > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (goodFrames.toFloat() / FaceScan.HOLD_FRAMES_TO_CAPTURE).coerceAtMost(1f) },
                    modifier = Modifier.fillMaxWidth(0.6f).clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF4ADE80),
                    trackColor = Color.White.copy(alpha = 0.2f),
                )
            }
        }
    }
}

@Composable
private fun IconButtonBack(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            androidx.compose.material.icons.Icons.Filled.ArrowBack,
            contentDescription = "Back",
            tint = Color.White,
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

    val imageAnalyzer = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(ContextCompat.getMainExecutor(context), FaceAnalyzer(onResult))
            }
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
