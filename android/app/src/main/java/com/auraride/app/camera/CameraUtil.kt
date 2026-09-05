package com.auraride.app.camera

import android.content.Context
import androidx.camera.core.ImageProxy
import java.io.File

/** Capture file in filesDir (NOT cacheDir — the OS evicts cache under camera memory
 *  pressure, which was dropping the NID image before upload). */
fun newImageFile(context: Context, name: String): File {
    val dir = File(context.filesDir, "captures").apply { mkdirs() }
    return File(dir, "$name-${System.currentTimeMillis()}.jpg")
}

/** Rotation the analyzer/ML Kit needs from an ImageProxy. */
fun ImageProxy.rotationDegrees(): Int = imageInfo.rotationDegrees
