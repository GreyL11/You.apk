package com.example.ui.screens

import android.Manifest
import android.app.Application
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.EXERCISES
import com.example.domain.Joint
import com.example.domain.MovementEngine
import com.example.domain.MovementFrame
import com.example.domain.Pt
import com.example.domain.Side
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

/**
 * Real, generalized live session — reads the exercise the user actually picked (`exId`, threaded
 * through navigation from WorkoutSheet) instead of the old hardcoded `exId = "squat"` / `load =
 * 0.0` literals this screen used to carry. Rep counting, side selection and fault detection now
 * come from `MovementEngine` against the full `Exercises` catalogue, not a single inlined
 * hip-knee-ankle formula.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LiveSessionScreen(
    exId: String = "squat",
    onBack: () -> Unit = {},
    todayViewModel: TodayViewModel = viewModel(factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(LocalContext.current.applicationContext as Application)),
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val ex = remember(exId) { EXERCISES[exId] }
    val engine = remember(exId) { MovementEngine(exId) }
    // The real bar/plates/injuries, not TrainingProfile()'s defaults -- see ProfileMapping.kt.
    // Read here rather than threaded as a parameter because this is the one screen that both
    // suggests a load and snaps a progression, and both have to agree with what is on the bar.
    val dashboardState by todayViewModel.dashboardState.collectAsState()

    var reps by remember { mutableStateOf(0) }
    var isSaving by remember { mutableStateOf(false) }
    var faultCue by remember { mutableStateOf<String?>(null) }
    // Real suggested load: last logged weight for this lift, or Planner's bodyweight-scaled
    // starting guess — never the hardcoded literal this screen used to carry regardless of
    // exercise. Bodyweight lifts (loadRatio 0) are handled explicitly, not defaulted into 0 by
    // omission — see TodayViewModel.suggestedLoad.
    var suggestedLoad by remember { mutableStateOf(0.0) }

    // What this lifter has been shown to simply do -- computed once per lift, from their own logged
    // sessions, not per frame. Empty until FormBaseline.MIN_SESSIONS of history exists, so a new
    // lifter is cued on everything.
    var habitualFaultIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(exId) {
        habitualFaultIds = todayViewModel.habitualFaultIds(exId)
    }

    LaunchedEffect(exId, dashboardState.trainingProfile) {
        suggestedLoad = todayViewModel.suggestedLoad(exId, dashboardState.trainingProfile)
    }

    if (ex == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Unknown exercise: $exId")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onBack) { Text("Back") }
        }
        return
    }
    val bodyweight = ex.loadRatio == 0.0

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(ex.name, style = MaterialTheme.typography.headlineMedium)
        Text(ex.cameraHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Reps: $reps", style = MaterialTheme.typography.headlineSmall)
                if (!bodyweight) {
                    Text(
                        "Suggested load: ${suggestedLoad} kg",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // The number this screen already had. What it did not have: what to actually
                    // hang on the bar to get there, which is the thing you need standing at the rack.
                    Text(
                        com.example.domain.Planner.loadoutText(suggestedLoad, dashboardState.trainingProfile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Bodyweight", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                faultCue?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }

            Button(onClick = {
                if (reps > 0 && !isSaving) {
                    isSaving = true
                    todayViewModel.logTraining(
                        exId = exId,
                        reps = reps,
                        load = if (bodyweight) 0.0 else suggestedLoad,
                        faultEvents = engine.faultEvents.toList(),
                        profile = dashboardState.trainingProfile,
                    )
                    onBack()
                }
            }) {
                Text("Finish & Save")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (cameraPermissionState.status.isGranted) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LiveSessionCameraPreview(
                    engine = engine,
                    shouldCue = { faultId -> faultId !in habitualFaultIds },
                ) { r, cue -> reps = r; faultCue = cue }
            }
        } else {
            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                Text("Grant Camera Permission")
            }
        }
    }
}

/** Which ML Kit landmark type backs each of our `Joint`s, per side. ML Kit's own 33-point pose
 *  topology names every one of legacy's IDX joints (shoulder/elbow/wrist/index/hip/knee/ankle/
 *  heel/toe→foot_index) — see Exercises.kt's landmark-space note for what does NOT carry over
 *  (true metric world coordinates). */
private fun mlKitType(side: Side, joint: Joint): Int = when (joint) {
    Joint.SHOULDER -> if (side == Side.LEFT) PoseLandmark.LEFT_SHOULDER else PoseLandmark.RIGHT_SHOULDER
    Joint.ELBOW -> if (side == Side.LEFT) PoseLandmark.LEFT_ELBOW else PoseLandmark.RIGHT_ELBOW
    Joint.WRIST -> if (side == Side.LEFT) PoseLandmark.LEFT_WRIST else PoseLandmark.RIGHT_WRIST
    Joint.INDEX -> if (side == Side.LEFT) PoseLandmark.LEFT_INDEX else PoseLandmark.RIGHT_INDEX
    Joint.HIP -> if (side == Side.LEFT) PoseLandmark.LEFT_HIP else PoseLandmark.RIGHT_HIP
    Joint.KNEE -> if (side == Side.LEFT) PoseLandmark.LEFT_KNEE else PoseLandmark.RIGHT_KNEE
    Joint.ANKLE -> if (side == Side.LEFT) PoseLandmark.LEFT_ANKLE else PoseLandmark.RIGHT_ANKLE
    Joint.HEEL -> if (side == Side.LEFT) PoseLandmark.LEFT_HEEL else PoseLandmark.RIGHT_HEEL
    Joint.TOE -> if (side == Side.LEFT) PoseLandmark.LEFT_FOOT_INDEX else PoseLandmark.RIGHT_FOOT_INDEX
}

/** ML Kit gives pixel positions; normalizing by the (rotation-corrected) image size puts them in
 *  the same [0,1] space `Exercises.kt`'s fault rules expect (legacy's `lm` space — see that
 *  file's landmark-space note for what's NOT equivalent to legacy's metric `w` space). */
private fun extractSide(pose: Pose, side: Side, w: Int, h: Int): Map<Joint, Pt> {
    val out = mutableMapOf<Joint, Pt>()
    for (joint in Joint.values()) {
        val lm = pose.getPoseLandmark(mlKitType(side, joint)) ?: continue
        out[joint] = Pt(x = (lm.position.x / w).toDouble(), y = (lm.position.y / h).toDouble(), visibility = lm.inFrameLikelihood.toDouble())
    }
    return out
}

@Composable
private fun LiveSessionCameraPreview(
    engine: MovementEngine,
    /** Which fault ids are still worth speaking aloud for this lifter -- see FormBaseline. */
    shouldCue: (String) -> Boolean,
    onUpdate: (Int, String?) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    val imageAnalyzer = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(ContextCompat.getMainExecutor(context), PoseAnalyzer { pose, w, h ->
                    val left = extractSide(pose, Side.LEFT, w, h)
                    val right = extractSide(pose, Side.RIGHT, w, h)
                    val result = engine.step(MovementFrame(left, right, System.currentTimeMillis(), view = "side"))
                    // Faults are still all RECORDED by the engine; this only decides what gets
                    // said out loud. A cue this lifter has heard on 200 straight reps is noise, and
                    // noise is why people stop looking at the screen -- including on the rep that
                    // matters. Safety faults are never filtered, however habitual.
                    onUpdate(result.reps, result.faults.firstOrNull { shouldCue(it.id) }?.cue)
                })
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
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
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

// No current caller (its only one, BoxingScreen.kt, was removed as dead/unrouted code) — kept as a
// small, working rear-camera preview building block rather than deleted speculatively.
@Composable
fun RearCameraPreview() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
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
