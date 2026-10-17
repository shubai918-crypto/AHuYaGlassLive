package cn.ahuya.glasslive.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawImage
// ⭐ Kyant0 真玻璃管线
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow

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

    // ⭐ Kyant0 CanvasBackdrop：把"底栏背后那块视频截帧"精确对齐画成背景源
    val glassBackdrop = rememberCanvasBackdrop {
        val f = frame
        val b = barRect
        val p = playerRect
        if (f != null && b != null && p != null && p.width > 0 && p.height > 0) {
            val sx = f.width.toFloat() / p.width
            val sy = f.height.toFloat() / p.height
            val l = ((b.left - p.left) * sx).toInt().coerceIn(0, f.width)
            val t = ((b.top - p.top) * sy).toInt().coerceIn(0, f.height)
            val r = ((b.right - p.left) * sx).toInt().coerceIn(l, f.width)
            val bt = ((b.bottom - p.top) * sy).toInt().coerceIn(t, f.height)
            if (r > l && bt > t) {
                drawImage(
                    f,
                    sourceRect = IntRect(l, t, r, bt),
                    dstRect = IntRect(0, 0, size.width.toInt(), size.height.toInt()),
                )
            } else {
                drawRect(Color(0xFF1C1C1E))
            }
        } else {
            drawRect(Color(0xFF1C1C1E))
        }
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
            // ⭐ Kyant0 drawBackdrop：完整液态玻璃效果链
            .drawBackdrop(
                backdrop = glassBackdrop,
                shape = { shape },
                effects = {
                    vibrancy()                 // 色彩增强：透出视频原色
                    blur(28f.dp.toPx())        // 实时高斯模糊
                    lens(16f.dp.toPx(), 32f.dp.toPx()) // 边缘折射（API33+ 自动启用）
                },
                highlight = { Highlight.Default },     // 边缘高光
                innerShadow = { InnerShadow(radius = 8f.dp) }, // 内阴影
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.10f)) }, // 表面提亮
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DanmakuInputPill(Modifier.weight(1f), onSendClick)
            GlassIconAction(Icons.Default.Hd, "清晰度", onQualityClick)
            GlassIconAction(Icons.Default.Settings, "设置", onQualityClick)
            GlassIconAction(Icons.Default.EmojiEmotions, "表情", onEmoteClick)
        }
    }
}

@Composable
private fun DanmakuInputPill(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    Row(
        modifier = modifier
            .height(48.dp)
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
