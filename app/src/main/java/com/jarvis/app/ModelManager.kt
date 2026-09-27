package com.jarvis.app

import android.content.Context
import android.net.Uri
import java.io.File

object ModelManager {

    private const val MODEL_FILENAME = "model.gguf"

    fun copyToInternal(context: Context, uri: Uri): File? {
        return try {
            val dest = File(context.filesDir, MODEL_FILENAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 1024 * 1024)
                }
            }
            dest
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getSavedModel(context: Context): File? {
        val f = File(context.filesDir, MODEL_FILENAME)
        return if (f.exists() && f.length() > 0) f else null
    }
}
