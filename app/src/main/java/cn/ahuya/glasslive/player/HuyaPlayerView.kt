package cn.ahuya.glasslive.player

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicLong

@Composable
fun HuyaPlayerView(
    url: String,
    onError: (String) -> Unit,
    onFirstFrame: () -> Unit,
    onFrame: ((ImageBitmap) -> Unit)? = null,
    onPlayerRect: ((IntRect) -> Unit)? = null,
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
    var videoAspect by remember { mutableStateOf<Float?>(null) }

    // ⭐ 修复：用 rememberUpdatedState 持有回调，LaunchedEffect 不再因 lambda 身份变化而重启
    val frameCb by rememberUpdatedState(onFrame)
    val lastGrab = remember { AtomicLong(0L) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError("${error.errorCodeName}: ${error.message}")
            }
            override fun onRenderedFirstFrame() { onFirstFrame() }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val w = videoSize.width
                val h = videoSize.height
                if (w > 0 && h > 0) videoAspect = w.toFloat() / h
            }
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

    // 截帧循环（单 key，稳定运行）
    LaunchedEffect(textureView) {
        val tv = textureView ?: return@LaunchedEffect
        while (isActive) {
            delay(120)
            val cb = frameCb ?: continue
            runCatching { tv.getBitmap() }.getOrNull()?.let { bmp ->
                if (bmp.width > 0 && bmp.height > 0) cb(bmp.asImageBitmap())
            }
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        val aspect = videoAspect
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .then(if (aspect != null) Modifier.aspectRatio(aspect) else Modifier)
                .onGloballyPositioned { coords ->
                    val p = coords.positionInWindow()
                    onPlayerRect?.invoke(
                        IntRect(
                            p.x.toInt(), p.y.toInt(),
                            (p.x + coords.size.width).toInt(),
                            (p.y + coords.size.height).toInt()
                        )
                    )
                },
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {
                            exoPlayer.setVideoTextureView(this@apply)
                        }
                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            exoPlayer.clearVideoTextureView(this@apply)
                            return true
                        }
                        // ⭐ 双保险：每帧更新时节流截帧
                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                            val now = System.currentTimeMillis()
                            if (now - lastGrab.get() < 100) return
                            lastGrab.set(now)
                            val cb = frameCb ?: return
                            runCatching { this@apply.getBitmap() }.getOrNull()?.let { bmp ->
                                if (bmp.width > 0 && bmp.height > 0) cb(bmp.asImageBitmap())
                            }
                        }
                    }
                }
            },
            update = { tv -> textureView = tv },
        )
    }

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
}
