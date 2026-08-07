package com.madus.mobile.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

object MediaEncode {

    fun fileToBase64(file: File): String =
        Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)

    /**
     * 读图并压缩为 JPEG base64，控制体积（多模态接口上限）。
     */
    fun imageUriToJpegBase64(
        context: Context,
        uri: Uri,
        maxEdge: Int = 1280,
        quality: Int = 82,
    ): Pair<String, String>? {
        return runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            val w = bounds.outWidth.coerceAtLeast(1)
            val h = bounds.outHeight.coerceAtLeast(1)
            while (w / sample > maxEdge * 2 || h / sample > maxEdge * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@runCatching null
            val scaled = scaleDown(bmp, maxEdge)
            if (scaled !== bmp) bmp.recycle()
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            scaled.recycle()
            val bytes = baos.toByteArray()
            if (bytes.size > 4_500_000) return@runCatching null
            Base64.encodeToString(bytes, Base64.NO_WRAP) to "image/jpeg"
        }.getOrNull()
    }

    private fun scaleDown(src: Bitmap, maxEdge: Int): Bitmap {
        val w = src.width
        val h = src.height
        val edge = maxOf(w, h)
        if (edge <= maxEdge) return src
        val scale = maxEdge.toFloat() / edge
        return Bitmap.createScaledBitmap(
            src,
            (w * scale).toInt().coerceAtLeast(1),
            (h * scale).toInt().coerceAtLeast(1),
            true,
        )
    }
}

/** 发给多模态模型的附件 */
data class MultimodalPayload(
    val text: String,
    val audioBase64: String? = null,
    /** m4a / mp3 / wav */
    val audioFormat: String = "m4a",
    val imageBase64: String? = null,
    val imageMime: String = "image/jpeg",
) {
    val hasAudio: Boolean get() = !audioBase64.isNullOrBlank()
    val hasImage: Boolean get() = !imageBase64.isNullOrBlank()
}
