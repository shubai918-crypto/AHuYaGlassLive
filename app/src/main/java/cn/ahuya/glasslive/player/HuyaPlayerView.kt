package cn.ahuya.glasslive.player

import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import cn.ahuya.glasslive.R

@Composable
fun HuyaPlayerView(
    url: String,
    onError: (String) -> Unit,
    onFirstFrame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(2_000, 8_000, 1_000, 2_000)
                    .build()
            )
            .build()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError("${error.errorCodeName}: ${error.message}")
            }
            override fun onRenderedFirstFrame() { onFirstFrame() }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(url) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        exoPlayer.seekToDefaultPosition()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // ⭐ 强制 inflate TextureView 版本的 PlayerView，确保能被液态玻璃采样
            (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).apply {
                player = exoPlayer
            }
        },
        update = { view -> view.player = exoPlayer },
    )

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
}
