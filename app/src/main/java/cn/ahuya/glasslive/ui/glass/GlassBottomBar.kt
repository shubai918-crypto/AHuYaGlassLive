package cn.ahuya.glasslive.ui.glass

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GlassBottomBar(
    frame: ImageBitmap?,
    playerRect: IntRect? = null,
    modifier: Modifier = Modifier,
    onSendClick: () -> Unit = {},
    onQualityClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onEmoteClick: () -> Unit = {},
) {
    val shape = RoundedCornerShape(28.dp)
    var barRect by remember { mutableStateOf<IntRect?>(null) }

    // ⭐ 精确裁出"底栏背后那块视频区域"，做到 1:1 真穿透
    val glassBitmap = remember(frame, barRect, playerRect) {
        computeGlassBitmap(frame, barRect, playerRect)
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val p = coords.positionInWindow()
                barRect = IntRect(
                    p.x.toInt(), p.y.toInt(),
                    (p.x + coords.size.width).toInt(),
                    (p.y + coords.size.height).toInt()
                )
            }
            .clip(shape)
    ) {
        if (glassBitmap != null) {
            Image(
                glassBitmap, null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize().blur(24.dp),
            )
        } else if (frame != null) {
            Image(
                frame, null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.BottomCenter,
                modifier = Modifier.matchParentSize().blur(24.dp),
            )
        } else {
            Box(Modifier.matchParentSize().background(Color(0xFF1C1C1E)))
        }
        Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.10f)))
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.18f)))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DanmakuInputPill(Modifier.weight(1f), onSendClick)
            GlassIconAction(Icons.Default.Hd, "清晰度", onQualityClick)
            GlassIconAction(Icons.Default.Settings, "设置", onSettingsClick)
            GlassIconAction(Icons.Default.EmojiEmotions, "表情", onEmoteClick)
        }
    }
}

private fun computeGlassBitmap(
    frame: ImageBitmap?,
    barRect: IntRect?,
    playerRect: IntRect?,
): ImageBitmap? {
    if (frame == null || barRect == null || playerRect == null) return null
    if (playerRect.width <= 0 || playerRect.height <= 0) return null
    return try {
        val ab = frame.asAndroidBitmap()
        val sx = ab.width.toFloat() / playerRect.width
        val sy = ab.height.toFloat() / playerRect.height
        var l = ((barRect.left - playerRect.left) * sx).toInt()
        var t = ((barRect.top - playerRect.top) * sy).toInt()
        var w = (barRect.width * sx).toInt()
        var h = (barRect.height * sy).toInt()
        if (w <= 0 || h <= 0) return null
        l = l.coerceIn(0, ab.width - 1)
        t = t.coerceIn(0, ab.height - 1)
        w = w.coerceIn(1, ab.width - l)
        h = h.coerceIn(1, ab.height - t)
        Bitmap.createBitmap(ab, l, t, w, h).asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun DanmakuInputPill(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .scale(scale.value)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        scale.animateTo(0.92f, spring(stiffness = Spring.StiffnessMedium))
                        tryAwaitRelease()
                        scale.animateTo(1.06f, spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy))
                        scale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
                        onClick()
                    }
                )
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Edit, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
        Text("发弹幕", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GlassIconAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    Column(
        modifier = Modifier
            .scale(scale.value)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        scale.animateTo(0.85f, spring(stiffness = Spring.StiffnessMedium))
                        tryAwaitRelease()
                        scale.animateTo(1.15f, spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioHighBouncy))
                        scale.animateTo(1f, spring())
                        onClick()
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
    }
}
