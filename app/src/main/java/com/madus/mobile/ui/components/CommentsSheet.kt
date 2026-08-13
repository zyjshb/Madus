package com.madus.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.madus.mobile.data.BilibiliApi
import com.madus.mobile.ui.CommentsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    state: CommentsUiState,
    onDismiss: () -> Unit,
    onDraftChange: (String) -> Unit,
    onPost: () -> Unit,
    onLoadMore: () -> Unit = {},
    onReply: (BilibiliApi.Comment) -> Unit = {},
    onCancelReply: () -> Unit = {},
    onLoadAllReplies: (BilibiliApi.Comment) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    var previewUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewIndex by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "评论",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    state.track?.let { t ->
                        Text(
                            text = t.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = buildString {
                        when {
                            state.total > 0 -> append("已加载 ${state.comments.size}/${state.total}")
                            state.comments.isNotEmpty() -> append("已加载 ${state.comments.size} 条")
                        }
                        if (isNotEmpty()) append(" · ")
                        append(if (state.loggedIn) "已登录" else "未登录")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                state.comments.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.error ?: "还没有评论",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (state.loggedIn) {
                                    "已登录 · 可在下方发表评论（同步 B 站）"
                                } else {
                                    "未登录 · 仍可浏览；发表需登录 B 站"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        items(
                            state.comments,
                            key = { c ->
                                c.rpid.ifBlank { "${c.uname}_${c.message.hashCode()}_${c.ctime}" }
                            },
                        ) { c ->
                            CommentThread(
                                root = c,
                                loadingReplies = state.loadingRepliesRoot == c.rpid,
                                onReply = onReply,
                                onLoadAllReplies = onLoadAllReplies,
                                onOpenPictures = { urls, index ->
                                    previewUrls = urls
                                    previewIndex = index
                                },
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        if (state.hasMore) {
                            item {
                                TextButton(
                                    onClick = onLoadMore,
                                    enabled = !state.loadingMore,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        if (state.loadingMore) {
                                            "加载中…"
                                        } else {
                                            val totalHint = if (state.total > 0) {
                                                " · B站约 ${state.total} 条"
                                            } else ""
                                            "加载更多（已 ${state.comments.size}$totalHint）"
                                        },
                                    )
                                }
                            }
                        } else if (state.comments.isNotEmpty()) {
                            item {
                                Text(
                                    text = if (state.total > 0) {
                                        "已全部加载 ${state.comments.size}/${state.total}"
                                    } else {
                                        "已加载完毕 · 共 ${state.comments.size} 条"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }

            state.error?.takeIf { state.comments.isNotEmpty() }?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            // 回复提示条
            if (state.replyTo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.replyHint.ifBlank { "回复 @${state.replyTo.uname}" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = onCancelReply) {
                        Text("取消")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = state.draft,
                    onValueChange = onDraftChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    decorationBox = { inner ->
                        if (state.draft.isEmpty()) {
                            Text(
                                when {
                                    !state.loggedIn -> "登录后可发评 · 仍可先浏览"
                                    state.replyTo != null -> "回复 @${state.replyTo.uname}…"
                                    else -> "说点什么…（同步到 B 站）"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
                IconButton(
                    onClick = onPost,
                    enabled = !state.posting && state.draft.isNotBlank(),
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "发送",
                        tint = if (!state.posting && state.draft.isNotBlank()) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }

    if (previewUrls.isNotEmpty()) {
        CommentPicturePreview(
            urls = previewUrls,
            index = previewIndex,
            onIndex = { previewIndex = it },
            onDismiss = { previewUrls = emptyList() },
        )
    }
}

@Composable
private fun CommentThread(
    root: BilibiliApi.Comment,
    loadingReplies: Boolean,
    onReply: (BilibiliApi.Comment) -> Unit,
    onLoadAllReplies: (BilibiliApi.Comment) -> Unit,
    onOpenPictures: (List<String>, Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CommentRow(
            c = root,
            indent = false,
            onReply = onReply,
            onOpenPictures = onOpenPictures,
        )
        // 楼中楼：缩进显示，不再与一级评论平铺
        root.children.forEach { child ->
            CommentRow(
                c = child,
                indent = true,
                onReply = onReply,
                onOpenPictures = onOpenPictures,
            )
        }
        val canLoadMore = root.repliesHasMore || root.rcount > root.children.size
        if (canLoadMore) {
            Text(
                text = when {
                    loadingReplies -> "加载中…"
                    root.children.isEmpty() ->
                        "展开回复" + if (root.rcount > 0) "（共 ${root.rcount}）" else ""
                    else ->
                        "加载更多回复（已 ${root.children.size}" +
                            (if (root.rcount > root.children.size) "/${root.rcount}" else "") +
                            "）"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(start = 46.dp, top = 2.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = !loadingReplies) { onLoadAllReplies(root) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        } else if (root.children.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun CommentRow(
    c: BilibiliApi.Comment,
    indent: Boolean,
    onReply: (BilibiliApi.Comment) -> Unit,
    onOpenPictures: (List<String>, Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (indent) 46.dp else 0.dp,
                top = if (indent) 6.dp else 12.dp,
                bottom = if (indent) 6.dp else 12.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        CommentAvatar(
            avatarUrl = c.avatar,
            uname = c.uname,
            size = if (indent) 28.dp else 36.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = c.uname.ifBlank { "用户" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatCtime(c.ctime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            if (indent && c.replyToUname.isNotBlank()) {
                Text(
                    text = "回复 @${c.replyToUname}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
            }
            CommentMessageWithEmotes(
                message = c.message,
                emotes = c.emotes,
            )
            if (c.pictureUrls.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    c.pictureUrls.take(9).forEachIndexed { index, url ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(url)
                                .crossfade(160)
                                .build(),
                            contentDescription = "评论图片",
                            imageLoader = MadusImageLoader.get(LocalContext.current),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                .clickable { onOpenPictures(c.pictureUrls, index) },
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                if (c.like > 0) {
                    Text(
                        text = "赞 ${c.like}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = "回复",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onReply(c) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * 把 message 里的 [doge] 等 B 站表情换成图片；无表情时退回纯文字。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentMessageWithEmotes(
    message: String,
    emotes: Map<String, String>,
) {
    if (message.isBlank()) return
    if (emotes.isEmpty()) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
        return
    }
    // 按 [xxx] 切分
    val parts = remember(message, emotes) {
        splitEmoteMessage(message, emotes.keys)
    }
    val context = LocalContext.current
    val loader = remember { MadusImageLoader.get(context) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        parts.forEach { part ->
            val url = emotes[part]
            if (url != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .crossfade(80)
                        .build(),
                    contentDescription = part,
                    imageLoader = loader,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(22.dp),
                )
            } else if (part.isNotEmpty()) {
                Text(
                    text = part,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** 把 message 拆成「文本 / 表情 key」交替段 */
private fun splitEmoteMessage(message: String, keys: Set<String>): List<String> {
    if (keys.isEmpty()) return listOf(message)
    // 长 key 优先，避免短 key 截断
    val ordered = keys.sortedByDescending { it.length }
    val out = mutableListOf<String>()
    var i = 0
    while (i < message.length) {
        var matched: String? = null
        for (k in ordered) {
            if (message.startsWith(k, i)) {
                matched = k
                break
            }
        }
        if (matched != null) {
            out.add(matched)
            i += matched.length
        } else {
            // 吞到下一个可能的 [
            val next = message.indexOf('[', i + 1).let { if (it < 0) message.length else it }
            // 若当前就是 [ 但未匹配任何 emote，当普通字推进 1
            if (message[i] == '[' && next == i) {
                out.add(message.substring(i, i + 1))
                i++
            } else {
                val end = if (message[i] == '[') next else next
                // 更稳：逐字符累积到能再试匹配
                val sb = StringBuilder()
                while (i < message.length) {
                    var hit: String? = null
                    for (k in ordered) {
                        if (message.startsWith(k, i)) {
                            hit = k
                            break
                        }
                    }
                    if (hit != null) break
                    sb.append(message[i])
                    i++
                }
                if (sb.isNotEmpty()) out.add(sb.toString())
            }
        }
    }
    return out
}

@Composable
private fun CommentAvatar(
    avatarUrl: String,
    uname: String,
    size: androidx.compose.ui.unit.Dp,
) {
    val context = LocalContext.current
    val loader = androidx.compose.runtime.remember { MadusImageLoader.get(context) }
    val url = normalizeCoverUrl(avatarUrl)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(160)
                    .build(),
                contentDescription = uname,
                imageLoader = loader,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
            )
        } else {
            Text(
                text = uname.take(1).ifBlank { "?" },
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun CommentPicturePreview(
    urls: List<String>,
    index: Int,
    onIndex: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val safeIndex = index.coerceIn(0, (urls.size - 1).coerceAtLeast(0))
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(urls.getOrNull(safeIndex))
                    .crossfade(120)
                    .build(),
                contentDescription = "评论大图",
                imageLoader = MadusImageLoader.get(context),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 48.dp),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
            }
            if (urls.size > 1) {
                Text(
                    text = "${safeIndex + 1}/${urls.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp),
                )
                if (safeIndex > 0) {
                    Text(
                        text = "上一张",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .clickable { onIndex(safeIndex - 1) }
                            .padding(16.dp),
                    )
                }
                if (safeIndex < urls.lastIndex) {
                    Text(
                        text = "下一张",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clickable { onIndex(safeIndex + 1) }
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}

private fun formatCtime(sec: Long): String {
    if (sec <= 0L) return ""
    return runCatching {
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(sec * 1000))
    }.getOrDefault("")
}
