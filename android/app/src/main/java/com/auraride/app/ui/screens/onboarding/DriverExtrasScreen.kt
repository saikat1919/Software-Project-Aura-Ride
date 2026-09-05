package com.auraride.app.ui.screens.onboarding

import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.auraride.app.camera.CameraPermissionGate
import com.auraride.app.camera.newImageFile
import com.auraride.app.ui.components.*
import com.auraride.app.ui.vm.RegisterViewModel
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Driver-only: real license capture + OCR (§7.3) + vehicle details (§6.1). */
@Composable
fun DriverExtrasScreen(vm: RegisterViewModel, onBack: () -> Unit, onContinue: () -> Unit) {
    CameraPermissionGate { DriverExtrasContent(vm, onBack, onContinue) }
}

@Composable
private fun DriverExtrasContent(vm: RegisterViewModel, onBack: () -> Unit, onContinue: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var captured by remember { mutableStateOf(false) }
    var analyzing by remember { mutableStateOf(false) }
    var licName by remember { mutableStateOf("") }
    var licNum by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }

    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var camReady by remember { mutableStateOf(false) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    DisposableEffect(Unit) { onDispose { controller.unbind(); recognizer.close() } }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(600)
        controller.bindToLifecycle(lifecycleOwner)
        camReady = true
    }

    fun capture() {
        analyzing = true
        val file = newImageFile(context, "license")
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                    vm.licensePath = file.absolutePath
                    scope.launch(Dispatchers.IO) {
                        val text = runCatching {
                            Tasks.await(recognizer.process(InputImage.fromFilePath(context, Uri.fromFile(file)))).text
                        }.getOrDefault("")
                        val (n, num) = parseLicense(text)
                        withContext(Dispatchers.Main) {
                            licName = n; licNum = num; captured = true; analyzing = false
                        }
                    }
                }
                override fun onError(e: ImageCaptureException) { analyzing = false }
            },
        )
    }

    AuraScaffold(
        title = "Driver details",
        onBack = onBack,
        showVerified = false,
        bottom = {
            PrimaryButton("Continue", {
                vm.setDriverExtras(vm.licensePath, licName, licNum, make, model, plate, color)
                onContinue()
            })
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionLabel("Driving license")
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().height(180.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (!captured) AndroidView(
                    factory = { ctx -> PreviewView(ctx).apply { this.controller = controller } },
                    modifier = Modifier.fillMaxSize(),
                ) else Text("🪪  Captured · OCR done", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!camReady && !captured) Text("Starting camera…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            when {
                analyzing -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                captured -> {
                    InfoCard {
                        LicRow("Name", licName.ifBlank { "—" })
                        LicRow("License no.", licNum.ifBlank { "—" })
                    }
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Retake license", { captured = false; licName = ""; licNum = "" })
                }
                camReady -> PrimaryButton("Capture license front", { capture() })
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel("Vehicle")
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(make, { make = it }, label = { Text("Make") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(plate, { plate = it }, label = { Text("Plate number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(color, { color = it }, label = { Text("Color") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun parseLicense(text: String): Pair<String, String> {
    // Accept either an NID (a long digit run, possibly spaced) or a license number
    // (alphanumeric). Prefer the NID pattern so students can test with their NID.
    val nid = Regex("\\d{10,17}").find(text.replace(" ", ""))?.value
    val alnum = Regex("[A-Z0-9-]{6,20}").findAll(text.uppercase())
        .map { it.value }.firstOrNull { it.any(Char::isDigit) }
    val num = nid ?: alnum ?: ""
    val name = text.lineSequence()
        .firstOrNull { it.contains("name", true) }
        ?.substringAfter(":", "")?.trim().orEmpty()
    return name to num
}

@Composable
private fun LicRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(value)
    }
}
