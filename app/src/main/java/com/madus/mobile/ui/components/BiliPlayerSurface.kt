package com.madus.mobile.ui.components

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.madus.mobile.MadusApp
import com.madus.mobile.R

/**
 * TextureView 视频面：切 Tab 不残留、不盖住其它页。
 * 不调用 clearVideoSurface（会崩）。
 */
@OptIn(UnstableApi::class)
@Composable
fun BiliPlayerSurface(
    modifier: Modifier = Modifier,
    /** true=完整画面（可能黑边）；false=裁切铺满 */
    fit: Boolean = true,
    edgeToEdge: Boolean = false,
) {
    val player = MadusApp.instance.playerEngine.player
    val holder = remember { arrayOfNulls<PlayerView>(1) }

    Box(
        modifier = modifier.background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                val view = LayoutInflater.from(ctx)
                    .inflate(R.layout.bili_player_view, null, false) as PlayerView
                view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                view.useController = false
                view.resizeMode = if (fit) {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
                view.player = player
                holder[0] = view
                view
            },
            update = { view ->
                holder[0] = view
                if (view.player !== player) view.player = player
                view.resizeMode = if (fit) {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
                // 可见时绑定，不可见时在 onRelease 解绑
                view.visibility = android.view.View.VISIBLE
            },
            onRelease = { view ->
                view.visibility = android.view.View.GONE
                view.player = null
                if (holder[0] === view) holder[0] = null
            },
            modifier = Modifier.fillMaxSize(),
        )
        DisposableEffect(Unit) {
            onDispose {
                holder[0]?.let { v ->
                    v.visibility = android.view.View.GONE
                    v.player = null
                }
                holder[0] = null
            }
        }
    }
}
