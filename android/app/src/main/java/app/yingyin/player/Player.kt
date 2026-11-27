package app.yingyin.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import kotlinx.coroutines.delay

private fun Context.activityOrNull(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    title: String,
    lines: List<Line>,
    epIdx: Int,
    onBack: () -> Unit,
    onProgress: (Long, Long) -> Unit = { _, _ -> }
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val exo = remember { ExoPlayer.Builder(ctx).build() }

    // 同一条剧集在不同线路里的候选地址：优先没被体检判坏的，再优先 m3u8/mp4 直链。
    val candidates = remember(lines, epIdx) {
        val pool = lines.filter { !it.bad }.ifEmpty { lines }
        // 不要用“第 1 集”替换缺失的第 N 集，否则自动切线会播错集。
        pool.mapNotNull { l -> l.eps.getOrNull(epIdx) }
            .distinctBy { it.url }
            .sortedByDescending { looksMedia(it.url) }
    }
    var ci by remember { mutableStateOf(0) }
    var stateText by remember { mutableStateOf("准备中") }
    var errText by remember { mutableStateOf<String?>(null) }
    var switching by remember { mutableStateOf(false) }
    var bufferSince by remember { mutableStateOf(0L) }
    var lastProgressAt by remember { mutableStateOf(0L) }

    DisposableEffect(Unit) {
        val act = ctx.activityOrNull()
        val old = act?.requestedOrientation
        act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            exo.release()
            if (act != null && old != null) act.requestedOrientation = old
        }
    }

    LaunchedEffect(ci) {
        errText = null
        bufferSince = 0L
        val c = candidates.getOrNull(ci)
        if (c == null) {
            errText = "没有可播放的集数"
            return@LaunchedEffect
        }
        exo.stop()
        exo.setMediaItem(MediaItem.fromUri(c.url))
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
            delay(600)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    player = exo
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(6.dp)) {
            Text("‹ 返回", color = Color.White)
        }
        Text(
            title, color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
        )
        Text(
            "$stateText · 线路 ${ci + 1}/${candidates.size}",
            color = Color(0x99FFFFFF),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
        )
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
