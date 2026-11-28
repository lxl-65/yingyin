package app.yingyin.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

private fun Context.activityOrNull(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun clock(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    title: String,
    lines: List<Line>,
    epIdx: Int,
    startMs: Long = 0L,
    onBack: () -> Unit,
    onEpChange: (Int) -> Unit = {},
    onProgress: (Long, Long) -> Unit = { _, _ -> }
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val exo = remember { ExoPlayer.Builder(ctx).build() }

    // 同一条剧集在不同线路里的候选地址：优先没被体检判坏的，再优先 m3u8/mp4 直链。
    var playingEp by remember(epIdx) { mutableIntStateOf(epIdx) }
    val candidates = remember(lines, playingEp) {
        val pool = lines.filter { !it.bad }.ifEmpty { lines }
        // 不要用“第 1 集”替换缺失的第 N 集，否则自动切线会播错集。
        pool.mapNotNull { l -> l.eps.getOrNull(playingEp) }
            .distinctBy { it.url }
            .sortedByDescending { looksMedia(it.url) }
    }
    var ci by remember { mutableIntStateOf(0) }
    val maxEp = remember(lines) { lines.maxOfOrNull { it.eps.size } ?: 0 }
    var stateText by remember { mutableStateOf("准备中") }
    var errText by remember { mutableStateOf<String?>(null) }
    var switching by remember { mutableStateOf(false) }
    var bufferSince by remember { mutableLongStateOf(0L) }
    var lastProgressAt by remember { mutableLongStateOf(0L) }

    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(startMs.coerceAtLeast(0L)) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var textTrackCount by remember { mutableIntStateOf(0) }
    var audioTrackCount by remember { mutableIntStateOf(0) }
    var textTrackSel by remember { mutableIntStateOf(-1) }
    var audioTrackSel by remember { mutableIntStateOf(-1) }

    fun cycleTrack(type: Int, selected: Int): Int {
        val groups = exo.currentTracks.groups.filter { it.type == type }
        if (groups.isEmpty()) return -1
        val next = if (selected + 1 >= groups.size) -1 else selected + 1
        val builder = exo.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
        if (next >= 0) builder.setOverrideForType(TrackSelectionOverride(groups[next].mediaTrackGroup, 0))
        exo.trackSelectionParameters = builder.build()
        return next
    }

    BackHandler { onBack() }

    DisposableEffect(Unit) {
        val act = ctx.activityOrNull()
        val old = act?.requestedOrientation
        act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        view.keepScreenOn = true
        onDispose {
            // 返回前补一次进度，短于 5 秒的观看也不至于完全丢位置。
            val pos = exo.currentPosition
            val dur = exo.duration
            if (pos > 0L && dur > 0L) onProgress(pos, dur)
            view.keepScreenOn = false
            exo.release()
            if (act != null && old != null) act.requestedOrientation = old
        }
    }

    LaunchedEffect(ci, playingEp) {
        errText = null
        bufferSince = 0L
        textTrackSel = -1
        audioTrackSel = -1
        val c = candidates.getOrNull(ci)
        if (c == null) {
            errText = "没有可播放的集数"
            return@LaunchedEffect
        }
        exo.stop()
        exo.setMediaItem(MediaItem.fromUri(c.url))
        if (startMs > 0L) exo.seekTo(startMs)
        exo.prepare()
        exo.playWhenReady = true
    }

    LaunchedEffect(Unit) {
        while (true) {
            val sec = exo.currentPosition / 1000
            stateText = when (exo.playbackState) {
                Player.STATE_IDLE -> "准备中"
                Player.STATE_BUFFERING -> "缓冲中 ${sec}s"
                Player.STATE_READY -> if (exo.isPlaying) "播放中 ${sec}s" else "暂停 ${sec}s"
                Player.STATE_ENDED -> "播放结束"
                else -> "?"
            }
            isPlaying = exo.isPlaying
            if (!dragging) position = exo.currentPosition.coerceAtLeast(0L)
            duration = exo.duration.coerceAtLeast(0L)
            val tracks = exo.currentTracks
            textTrackCount = tracks.groups.count { it.type == C.TRACK_TYPE_TEXT }
            audioTrackCount = tracks.groups.count { it.type == C.TRACK_TYPE_AUDIO }
            val err = exo.playerError
            // 真正开始播放后每 5 秒回报一次，历史里的进度条和续播才有意义。
            if (exo.isPlaying && exo.duration > 0) {
                val now = System.currentTimeMillis()
                if (now - lastProgressAt > 5_000L) {
                    onProgress(exo.currentPosition, exo.duration)
                    lastProgressAt = now
                }
            }
            if (err != null && !switching) {
                if (ci < candidates.lastIndex) {
                    switching = true
                    bufferSince = 0L
                    ci += 1
                    delay(1200)
                    switching = false
                } else {
                    errText = "${err.errorCodeName} · ${err.message}"
                }
            } else if (err == null && !switching) {
                // 有些源不报错，只是一直 0 秒缓冲；8 秒还不走就换下一条，不能让用户干等。
                if (exo.playbackState == Player.STATE_BUFFERING && exo.currentPosition == 0L) {
                    val now = System.currentTimeMillis()
                    if (bufferSince == 0L) bufferSince = now
                    else if (now - bufferSince > 8_000L) {
                        if (ci < candidates.lastIndex) {
                            switching = true
                            bufferSince = 0L
                            ci += 1
                            delay(800)
                            switching = false
                        } else {
                            errText = "所有线路都没有响应"
                        }
                    }
                } else {
                    bufferSince = 0L
                }
            }
            delay(500)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(4200)
            controlsVisible = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    player = exo
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            update = { it.resizeMode = resizeMode },
            modifier = Modifier.fillMaxSize()
        )

        // 点画面显示/收起控制层；视频控件不依赖 PlayerView 自带 controller。
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { controlsVisible = !controlsVisible }
        )

        if (controlsVisible || errText != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                        )
                    )
                    .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.ArrowBack, "返回", tint = Color.White, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(5.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "$stateText · 第 ${playingEp + 1} 集",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 9.sp
                    )
                }
                if (playingEp + 1 < maxEp) {
                    Text(
                        "下一集 ›",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable {
                                playingEp += 1
                                onEpChange(playingEp)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Text(
                    "线路 ${ci + 1}/${candidates.size}",
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable {
                            if (candidates.size > 1) ci = (ci + 1) % candidates.size
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            if (controlsVisible) {
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(30.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerSeekButton(Icons.Rounded.Replay10) {
                        exo.seekTo((exo.currentPosition - 10_000L).coerceAtLeast(0L))
                    }
                    Box(
                        Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                if (exo.isPlaying) exo.pause() else exo.play()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    PlayerSeekButton(Icons.Rounded.Forward10) {
                        val max = exo.duration.coerceAtLeast(0L)
                        val target = exo.currentPosition + 10_000L
                        exo.seekTo(if (max > 0L) target.coerceAtMost(max) else target)
                    }
                    if (playingEp + 1 < maxEp) {
                        Text(
                            "下一集",
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.16f))
                                .clickable {
                                    playingEp += 1
                                    onEpChange(playingEp)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                        )
                    )
                    .padding(start = 18.dp, end = 18.dp, top = 28.dp, bottom = 12.dp)
            ) {
                val max = duration.coerceAtLeast(1L).toFloat()
                Slider(
                    value = position.coerceIn(0L, max.toLong()).toFloat(),
                    onValueChange = {
                        dragging = true
                        position = it.toLong()
                    },
                    onValueChangeFinished = {
                        exo.seekTo(position)
                        dragging = false
                    },
                    valueRange = 0f..max,
                    colors = SliderDefaults.colors(
                        thumbColor = Accent,
                        activeTrackColor = Accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(24.dp)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(clock(position), color = Color.White.copy(alpha = 0.78f), fontSize = 10.sp)
                    Text(" / ", color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp)
                    Text(clock(duration), color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp)
                    Spacer(Modifier.weight(1f))
                    if (playingEp + 1 < maxEp) {
                        Text(
                            "下一集",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Accent.copy(alpha = 0.18f))
                                .clickable {
                                    playingEp += 1
                                    onEpChange(playingEp)
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    if (audioTrackCount > 1) {
                        Text(
                            if (audioTrackSel >= 0) "音轨 ${audioTrackSel + 1}" else "音轨",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable { audioTrackSel = cycleTrack(C.TRACK_TYPE_AUDIO, audioTrackSel) }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    if (textTrackCount > 0) {
                        Text(
                            if (textTrackSel >= 0) "字幕 ${textTrackSel + 1}" else "字幕",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable { textTrackSel = cycleTrack(C.TRACK_TYPE_TEXT, textTrackSel) }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        "${speed}x",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable {
                                val next = SPEEDS[(SPEEDS.indexOf(speed).coerceAtLeast(0) + 1) % SPEEDS.size]
                                speed = next
                                exo.setPlaybackSpeed(next)
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable {
                                resizeMode = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.AspectRatio,
                            null,
                            tint = Color.White.copy(alpha = 0.82f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "铺满"
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "裁切"
                                else -> "适应"
                            },
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        if (exo.playbackState == Player.STATE_BUFFERING && errText == null) {
            CircularProgressIndicator(color = Accent, modifier = Modifier.align(Alignment.Center))
        }
        errText?.let {
            Text(
                "所有线路都打不开\n$it",
                color = Color(0xFFFF9B8A),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }
    }
}

@Composable
private fun PlayerSeekButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(28.dp))
    }
}
