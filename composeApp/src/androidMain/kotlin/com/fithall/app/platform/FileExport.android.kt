package com.fithall.app.platform

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** Writes the file to the app cache and opens the share sheet (save to Files, Drive, email...). */
@Composable
actual fun rememberFileExporter(): FileExporter {
    val context = LocalContext.current
    return remember(context) {
        object : FileExporter {
            override val isAvailable = true
            override fun export(fileName: String, mimeType: String, content: String) {
                val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() } // keep only the latest export around
                val file = File(dir, fileName).apply { writeText(content) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND)
                    .setType(mimeType)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "Export FitHall data").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

/** Deletes the temporary files FitHall creates: camera captures and data exports. */
fun clearAppCaches(context: Context) {
    listOf("meal_photos", EXPORT_DIR).forEach { File(context.cacheDir, it).deleteRecursively() }
}

private const val EXPORT_DIR = "exports"
