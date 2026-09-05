package com.auraride.app.ui.screens.passenger

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.auraride.app.camera.CameraPermissionGate
import com.auraride.app.camera.newImageFile
import com.auraride.app.ui.components.*
import com.auraride.app.ui.vm.StepUpViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Step-up re-verification (§6.3): capture a fresh selfie, POST /auth/step-up, which
 * face-matches it against the registration selfie. On PASS the account is re-activated.
 */
@Composable
fun StepUpScreen(onResolved: () -> Unit) {
    CameraPermissionGate { StepUpContent(onResolved) }
}

@Composable
private fun StepUpContent(onResolved: () -> Unit) {
    val vm: StepUpViewModel = viewModel(factory = StepUpViewModel.Factory)
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    DisposableEffect(Unit) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    fun capture() {
        val file = newImageFile(context, "stepup")
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                    vm.submit(file.absolutePath, onResolved)
                }
                override fun onError(e: ImageCaptureException) {}
            },
        )
    }

    AuraScaffold(
        title = "Quick security check",
        showVerified = false,
        bottom = {
            if (state.loading) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else PrimaryButton("Take a selfie to verify", { capture() })
        },
    ) {
        StatusBanner("We noticed something new about this sign-in. A quick face check keeps your account yours.", Tone.WARN)
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(200.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                AndroidViewPreview(controller)
            }
        }
        Spacer(Modifier.height(16.dp))
        val msg = when {
            state.error != null -> state.error!!
            state.verdict != null && state.verdict != "PASS" ->
                "Couldn't confirm it's you. Face the camera straight on in good light and try again."
            else -> "Line your face up and hold still."
        }
        Text(msg, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            color = if (state.verdict != null && state.verdict != "PASS") MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AndroidViewPreview(controller: LifecycleCameraController) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx -> PreviewView(ctx).apply { this.controller = controller } },
        modifier = Modifier.fillMaxSize(),
    )
}
