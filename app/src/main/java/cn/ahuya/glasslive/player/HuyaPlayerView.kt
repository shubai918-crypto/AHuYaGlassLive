package cn.ahuya.glasslive.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.view.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Media3 播放器封装：
 * - 直播向小缓冲 LoadControl（贴近直播边缘，抗"播几秒冻住"）
 * - url 变化自动换源重备
 * - 播放错误回调上层（用于换 CDN / 降清晰度 / 重解析）
 */
@Composable
fun HuyaPlayerView(
    url: String,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 2_000,
                        /* maxBufferMs = */ 8_000,
                        /* bufferForPlaybackMs = */ 1_000,
                        /* bufferForPlaybackAfterRebufferMs = */ 2_000,
                    )
                    .build()
            )
            .build()
    }

    // 播放错误 → 回调上层换线/降档
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError("${error.errorCodeName}: ${error.message}")
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // url 变化 → 换源并重新准备；直播流跳到直播边缘
    LaunchedEffect(url) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        exoPlayer.seekToDefaultPosition()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                player = exoPlayer
            }
        },
        update = { view ->
            view.player = exoPlayer
        },
    )

    // 退出释放
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }
}
