package com.fithall.app.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.fithall.app.data.KeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import kotlin.math.max

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun todayKey(): String = LocalDate.now().toString()

class SharedPreferencesStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("fithall", Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
    override fun clear() {
        prefs.edit().clear().commit()
    }
}

@Composable
actual fun rememberMealPhotoLauncher(onPhoto: (ByteArray) -> Unit): MealPhotoLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Survives the activity being recreated while the camera app is in front.
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }

    /** [ownFile] is a camera capture in our cache; it's deleted once read, so photos don't pile up. */
    fun deliver(uri: Uri, ownFile: File? = null) {
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                try {
                    runCatching { loadScaledJpeg(context, uri) }.getOrNull()
                } finally {
                    ownFile?.delete()
                }
            }
            if (bytes != null) onPhoto(bytes)
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = pendingCapture
        pendingCapture = null
        val file = path?.let(::File)
        if (saved && file != null) deliver(Uri.fromFile(file), ownFile = file) else file?.delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) deliver(uri)
    }

    return remember(context) {
        object : MealPhotoLauncher {
            override val canTakePhoto = true
            override val canPickFromGallery = true

            override fun takePhoto() {
                val dir = File(context.cacheDir, "meal_photos").apply { mkdirs() }
                val file = File(dir, "meal_${System.currentTimeMillis()}.jpg")
                pendingCapture = file.absolutePath
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                camera.launch(uri)
            }

            override fun pickFromGallery() {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }
}

/**
 * Decodes the photo, applies EXIF rotation, downsizes the long edge to [MAX_EDGE] px and
 * re-encodes as JPEG. Larger images cost more tokens without improving the estimate.
 */
private fun loadScaledJpeg(context: Context, uri: Uri): ByteArray {
    val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val size = info.size
            val scale = MAX_EDGE.toFloat() / max(size.width, size.height)
            if (scale < 1f) decoder.setTargetSize((size.width * scale).toInt(), (size.height * scale).toInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val raw = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        val scale = MAX_EDGE.toFloat() / max(raw.width, raw.height)
        if (scale < 1f) Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true)
        else raw
    }
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }
}

private const val MAX_EDGE = 1568
