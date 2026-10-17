package cn.ahuya.glasslive.player

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun HuyaPlayerView(
    url: String,
    onError: (String) -> Unit,
    onFirstFrame: () -> Unit,
    onFrame: ((ImageBitmap) -> Unit)? = null,
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
    var textureView by remember { mutableStateOf<TextureView?>(null) }

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

    // ⭐ 截帧循环：每 120ms 抓一帧真实视频画面，喂给液态玻璃
    LaunchedEffect(textureView, onFrame) {
        val tv = textureView ?: return@LaunchedEffect
        val cb = onFrame ?: return@LaunchedEffect
        while (isActive) {
            delay(120)
            runCatching { tv.getBitmap() }.getOrNull()?.let { bmp ->
                if (bmp.width > 0 && bmp.height > 0) cb(bmp.asImageBitmap())
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {
                        exoPlayer.setVideoTextureView(this@apply)
                    }
                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        exoPlayer.clearVideoTextureView()
                        return true
                    }
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        },
        update = { tv -> textureView = tv },
    )

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
}
