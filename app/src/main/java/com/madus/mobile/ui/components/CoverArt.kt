package com.madus.mobile.ui.components

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.madus.mobile.data.AppearanceMode
import com.madus.mobile.ui.theme.appearanceTokens
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Shared loader: Bilibili CDN requires Referer or returns blank/403. */
object MadusImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }
    }

    private fun build(context: Context): ImageLoader {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val builder = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
                    )
                if (url.contains("hdslb.com") || url.contains("bilibili") || url.contains("bilivideo")) {
                    builder.header("Referer", "https://www.bilibili.com")
                    builder.header("Origin", "https://www.bilibili.com")
                }
                chain.proceed(builder.build())
            }
            .build()
        return ImageLoader.Builder(context)
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }
}

fun normalizeCoverUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    var u = raw.trim()
    if (u.startsWith("//")) u = "https:$u"
    // Prefer https for bilibili CDN
    if (u.startsWith("http://")) u = "https://" + u.removePrefix("http://")
    return u
}

/**
 * Real cover from http URL, content URI, or local file path; falls back to line-sketch note.
 */
@Composable
fun CoverArt(
    coverUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val loader = remember { MadusImageLoader.get(context) }
    val tokens = appearanceTokens()
    val coverShape = RoundedCornerShape(tokens.cornerSm)
    val sized = if (size > 0.dp) modifier.size(size) else modifier

    val normalized = normalizeCoverUrl(coverUrl)
    val data: Any? = when {
        normalized == null -> null
        normalized.startsWith("http") || normalized.startsWith("content:") || normalized.startsWith("file:") ->
            normalized
        else -> java.io.File(normalized)
    }

    if (data != null) {
        // 先 clip 再画图，避免加载时露出直角白底
        Box(
            modifier = sized
                .clip(coverShape)
                .border(tokens.borderWidth, MaterialTheme.colorScheme.outline.copy(
                    alpha = if (tokens.mode == AppearanceMode.SoftGlass) 0.2f else 1f,
                ), coverShape),
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(data)
                    .crossfade(180)
                    .build(),
                contentDescription = contentDescription,
                imageLoader = loader,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(coverShape),
                loading = {
                    CoverPlaceholder(modifier = Modifier.fillMaxSize(), size = 0.dp)
                },
                error = {
                    CoverPlaceholder(modifier = Modifier.fillMaxSize(), size = 0.dp)
                },
            )
        }
    } else {
        CoverPlaceholder(modifier = sized, size = if (size > 0.dp) size else 0.dp)
    }
}
