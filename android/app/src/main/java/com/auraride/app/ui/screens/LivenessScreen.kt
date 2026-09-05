package com.auraride.app.ui.screens

import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.auraride.app.camera.CameraPermissionGate
import com.auraride.app.camera.LivenessAnalyzer
import com.auraride.app.camera.newImageFile
import com.auraride.app.data.LivenessStep
import com.auraride.app.data.Mock
import com.auraride.app.ui.components.VerifiedStrip
import com.auraride.app.ui.theme.AuraColors
import com.auraride.app.ui.vm.RegisterViewModel
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Real liveness (§7.1). Two phases so the stored selfie is a clean, STRAIGHT frame:
 *  1) LOOK_STRAIGHT — wait for a frontal face (eyes open, small yaw/pitch), capture the selfie.
 *  2) CHALLENGE — randomized blink / head-turn to prove liveness.
 * The selfie captured in phase 1 (looking straight) is what's saved, so it matches the NID
 * portrait far better than a frame grabbed mid-turn.
 */
@Composable
fun LivenessScreen(vm: RegisterViewModel, onVerified: () -> Unit) {
    CameraPermissionGate { LivenessContent(vm, onVerified) }
}

private enum class Phase { LOOK_STRAIGHT, CHALLENGE }

// Hold a steady frontal pose this long before the selfie is taken, so it can't snap the
// instant the camera opens (which produced angled/half-settled shots).
private const val HOLD_MS = 1200L

@Composable
private fun LivenessContent(vm: RegisterViewModel, onVerified: () -> Unit) {
    val ext = AuraColors.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val steps = remember { Mock.randomizedLiveness() }
    var phase by remember { mutableStateOf(Phase.LOOK_STRAIGHT) }
    var index by remember { mutableIntStateOf(0) }
    var faceFound by remember { mutableStateOf(false) }
    var frontal by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var challengeDone by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }   // 0..1, fills the ring as they hold
    val frontalSince = remember { longArrayOf(0L) }            // ms when the frontal hold began (0 = not holding)
    val progress = index.toFloat() / steps.size

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE)
        }
    }

    fun captureStraightSelfie() {
        val file = newImageFile(context, "selfie")
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    vm.selfiePath = file.absolutePath
                    capturing = false
                    phase = Phase.CHALLENGE
                }
                override fun onError(exc: ImageCaptureException) {
                    Log.e("Liveness", "capture failed", exc)
                    vm.selfiePath = ""       // proceed; face-match will just land in REVIEW
                    capturing = false
                    phase = Phase.CHALLENGE
                }
            },
        )
    }

    fun handleFace(left: Float?, right: Float?, yaw: Float, pitch: Float, found: Boolean) {
        faceFound = found
        if (!found) { frontalSince[0] = 0L; holdProgress = 0f; frontal = false; return }
        when (phase) {
            Phase.LOOK_STRAIGHT -> {
                if (capturing) return
                // Frontal = both eyes clearly open, head not turned (yaw) or tilted up/down (pitch).
                val isFrontal = left != null && right != null &&
                    minOf(left, right) > 0.5f && abs(yaw) < 10f && abs(pitch) < 12f
                frontal = isFrontal
                if (isFrontal) {
                    val now = System.currentTimeMillis()
                    if (frontalSince[0] == 0L) frontalSince[0] = now
                    val held = now - frontalSince[0]
                    holdProgress = (held.toFloat() / HOLD_MS).coerceIn(0f, 1f)
                    if (held >= HOLD_MS) { capturing = true; captureStraightSelfie() }
                } else {
                    frontalSince[0] = 0L    // moved out of frontal -> restart the hold
                    holdProgress = 0f
                }
            }
            Phase.CHALLENGE -> {
                if (index >= steps.size) return
                val advanced = when (steps[index]) {
                    LivenessStep.BLINK -> left != null && right != null && minOf(left, right) < 0.35f
                    LivenessStep.TURN_LEFT -> yaw > 20f
                    LivenessStep.TURN_RIGHT -> yaw < -20f
                }
                if (advanced) { index++; if (index >= steps.size) challengeDone = true }
            }
        }
    }

    DisposableEffect(Unit) {
        controller.setImageAnalysisAnalyzer(
            analysisExecutor,
            LivenessAnalyzer { l, r, y, p, f -> handleFace(l, r, y, p, f) },
        )
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind(); analysisExecutor.shutdown() }
    }

    LaunchedEffect(challengeDone) {
        if (challengeDone) { controller.unbind(); onVerified() }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        VerifiedStrip()
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (phase == Phase.LOOK_STRAIGHT) "Look at the camera" else "Prove it's you",
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(230.dp)) {
                        val stroke = 16.dp.toPx()
                        drawArc(ext.verifiedContainer, 0f, 360f, false,
                            style = Stroke(stroke, cap = StrokeCap.Round))
                        val sweep = if (phase == Phase.LOOK_STRAIGHT) holdProgress else progress
                        drawArc(ext.verified, -90f, sweep * 360f, false,
                            style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    AndroidView(
                        factory = { ctx -> PreviewView(ctx).apply { this.controller = controller } },
                        modifier = Modifier.size(196.dp).clip(CircleShape),
                    )
                }
            }
            val hint = when (phase) {
                Phase.LOOK_STRAIGHT -> when {
                    !faceFound -> "Center your face in the circle"
                    !frontal -> "Face the camera straight on, eyes open"
                    capturing -> "Hold still…"
                    else -> "Hold still…"
                }
                Phase.CHALLENGE -> if (index < steps.size) steps[index].prompt else "Verifying…"
            }
            Text(hint, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            if (phase == Phase.CHALLENGE && index < steps.size) {
                Text(steps[index].hint, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            if (phase == Phase.CHALLENGE) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.indices.forEach { i ->
                        Box(Modifier.size(9.dp).clip(CircleShape)
                            .background(if (i < index) ext.verified else MaterialTheme.colorScheme.surfaceVariant))
                    }
                }
            }
        }
    }
}
