package com.auraride.app.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * On-device liveness signals via ML Kit Face Detection (§7.1) — free, no key.
 * Reports eye-open probabilities + head yaw for the challenge state machine.
 */
class LivenessAnalyzer(
    private val onFace: (leftEye: Float?, rightEye: Float?, yawDegrees: Float, pitchDegrees: Float, faceFound: Boolean) -> Unit,
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // eye-open probability
            .build()
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null) { imageProxy.close(); return }
        val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        detector.process(input)
            .addOnSuccessListener { faces ->
                val f = faces.firstOrNull()
                onFace(
                    f?.leftEyeOpenProbability, f?.rightEyeOpenProbability,
                    f?.headEulerAngleY ?: 0f, f?.headEulerAngleX ?: 0f, f != null,
                )
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
