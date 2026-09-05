package com.auraride.app.ui.screens.onboarding

import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
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


@Composable
fun NidCaptureScreen(vm: RegisterViewModel, onBack: () -> Unit, onContinue: () -> Unit) {
    CameraPermissionGate { NidContent(vm, onBack, onContinue) }
}

@Composable
private fun NidContent(vm: RegisterViewModel, onBack: () -> Unit, onContinue: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var captured by remember { mutableStateOf(false) }
    var analyzing by remember { mutableStateOf(false) }
    var ocrName by remember { mutableStateOf("") }
    var ocrGender by remember { mutableStateOf("") }
    var ocrNid by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    var camReady by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { controller.unbind(); recognizer.close() } }
    // Delay opening the back camera so the front (selfie) camera's async HAL close finishes
    // first — budget MediaTek devices hang if both cameras overlap.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800)
        controller.bindToLifecycle(lifecycleOwner)
        camReady = true
    }

    fun capture() {
        analyzing = true
        val file = newImageFile(context, "nid")
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                    vm.nidPath = file.absolutePath
                    // Decode + OCR OFF the main thread — fromFilePath decodes the full image
                    // synchronously, which freezes the UI if done on main.
                    scope.launch(Dispatchers.IO) {
                        val text = runCatching {
                            val input = InputImage.fromFilePath(context, Uri.fromFile(file))
                            Tasks.await(recognizer.process(input)).text
                        }.getOrDefault("")
                        val (n, g, id) = parseNid(text)
                        withContext(Dispatchers.Main) {
                            ocrName = n; ocrGender = g; ocrNid = id
                            captured = true; analyzing = false
                        }
                    }
                }
                override fun onError(e: ImageCaptureException) { analyzing = false }
            },
        )
    }

    AuraScaffold(
        title = "Scan your NID",
        onBack = onBack,
        showVerified = false,
        bottom = {
            when {
                analyzing -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                captured -> Column(Modifier.fillMaxWidth()) {
                    PrimaryButton("Looks right — continue", {
                        vm.setOcr(ocrName, ocrGender, ocrNid); onContinue()
                    })
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Retake", {
                        captured = false; ocrName = ""; ocrGender = ""; ocrNid = ""
                    })
                }
                camReady -> PrimaryButton("Capture NID front", { capture() })
                else -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        },
    ) {
        Box(
            Modifier.fillMaxWidth().height(220.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx -> PreviewView(ctx).apply { this.controller = controller } },
                modifier = Modifier.fillMaxSize(),
            )
            if (!camReady) Text("Starting camera…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        if (captured) {
            SectionLabel("Extracted on-device")
            Spacer(Modifier.height(6.dp))
            InfoCard {
                OcrRow("Name", ocrName.ifBlank { "—" })
                OcrRow("NID number", ocrNid.ifBlank { "—" })
                OcrRow("Gender", ocrGender.ifBlank { "unclear" }, verified = ocrGender.equals("Female", true))
            }
            Spacer(Modifier.height(10.dp))
            Text("Check these match your NID. Anything unclear is reviewed by an admin.",
                fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Align the front of your NID inside the frame, then capture.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Best-effort field extraction from raw OCR text (§7.3, Bengali is unreliable). */
private fun parseNid(text: String): Triple<String, String, String> {
    val nid = Regex("\\b\\d{10,17}\\b").find(text.replace(" ", ""))?.value ?: ""
    val gender = when {
        text.contains("female", true) || text.contains("মহিলা") -> "Female"
        text.contains("male", true) -> "Male"
        else -> ""
    }
    // Name: prefer text after a "Name" label on the same line; skip anything date-like.
    val dateLike = Regex("(?i)date|dob|birth|\\d{1,2}[ /.-][A-Za-z0-9]{2,}[ /.-]\\d{2,4}")
    val lines = text.lines().map { it.trim() }
    var name = ""
    for ((i, line) in lines.withIndex()) {
        if (!line.contains("name", true)) continue
        if (line.contains("father", true) || line.contains("mother", true)) continue
        val sameLine = line.substringAfter(":", "").trim()
        if (sameLine.isNotBlank() && !dateLike.containsMatchIn(sameLine)) { name = sameLine; break }
        // else the next line that isn't blank, a date, or numeric
        name = lines.drop(i + 1).firstOrNull {
            it.isNotBlank() && !dateLike.containsMatchIn(it) && it.none(Char::isDigit)
        }.orEmpty()
        if (name.isNotBlank()) break
    }
    return Triple(name, gender, nid)
}

@Composable
private fun OcrRow(label: String, value: String, verified: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium)
        if (verified) { Spacer(Modifier.width(6.dp)); VerifiedBadge() }
    }
}
