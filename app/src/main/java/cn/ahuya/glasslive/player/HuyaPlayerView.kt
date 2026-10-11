package cn.ahuya.glasslive.player

import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import cn.ahuya.glasslive.R

@Composable
fun HuyaPlayerView(
    url: String?,
    fitCrop: Boolean = false,
    onError: (String) -> Unit,
    onFirstFrame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }

    val currentError by rememberUpdatedState(onError)
    val currentFirst by rememberUpdatedState(onFirstFrame)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // ⭐ 把错误码 + 消息传出去
                currentError("${error.errorCodeName}: ${error.message}")
            }
            override fun onRenderedFirstFrame() { currentFirst() }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(url) {
        if (url.isNullOrEmpty()) return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).apply {
                this.player = player
            }
        },
        update = { view ->
            view.resizeMode = if (fitCrop) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                              else AspectRatioFrameLayout.RESIZE_MODE_FIT
        },
    )
}
