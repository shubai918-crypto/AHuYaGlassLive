package cn.ahuya.glasslive.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// ── Kyant0 AndroidLiquidGlass 真实 API（即本库的液态玻璃入口）──
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow

/** 底部操作项：图标 + 图标正下方 10sp 小字（计数或标签） */
data class BarAction(
    val icon: ImageVector,
    val label: String? = null,
    val onClick: () -> Unit = {},
)

private val BarShape = RoundedCornerShape(28.dp)
private val PillGray = Color(0xFF8A8D91)      // 输入胶囊文字/图标灰
private val IconDark = Color(0xFF3B3E42)      // 操作图标深灰
private const val SURFACE_ALPHA = 0.62f       // 半透明白表面（酷安浅色玻璃质感）

/**
 * 酷安同款悬浮液态玻璃底栏。
 * 玻璃管线 = Kyant drawBackdrop（blur + vibrancy + lens + 边缘高光 + 内阴影 + 半透明白表面），
 * 背景源 = 底栏背后那块视频截帧（rememberCanvasBackdrop 精确对齐裁剪）→ 真·实时穿透。
 */
@Composable
fun LiquidGlassBottomBar(
    modifier: Modifier = Modifier,
    frame: ImageBitmap? = null,
    playerRect: IntRect? = null,
    inputHint: String = "写弹幕",
    onInputClick: () -> Unit = {},
    actions: List<BarAction> = emptyList(),
) {
    var barRect by remember { mutableStateOf<IntRect?>(null) }

    // 背景源：裁出"底栏正背后"那块视频画面；拿不到帧时用浅灰兜底（仍是浅色玻璃观感）
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
            val bo = ((b.bottom - p.top) * sy).toInt().coerceIn(t, f.height)
            if (r > l && bo > t) {
                drawImage(
                    image = f,
                    srcOffset = IntOffset(l, t),
                    srcSize = IntSize(r - l, bo - t),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                )
            } else {
                drawRect(Color(0xFFD9DBDF))
            }
        } else {
            drawRect(Color(0xFFD9DBDF))
        }
    }

    Box(
        modifier = modifier
            // 悬浮外边距 + 系统底部导航栏遮挡处理
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .onGloballyPositioned { c ->
                val p = c.positionInWindow()
                barRect = IntRect(
                    p.x.toInt(), p.y.toInt(),
                    (p.x + c.size.width).toInt(),
                    (p.y + c.size.height).toInt()
                )
            }
            .clip(BarShape)
            // ⭐ 液态玻璃入口（= 设想中的 Modifier.liquidGlass()）
            .drawBackdrop(
                backdrop = glassBackdrop,
                shape = { BarShape },
                effects = {
                    vibrancy()                         // 透出背后画面色彩
                    blur(32f.dp.toPx())                // 实时高斯模糊
                    lens(14f.dp.toPx(), 28f.dp.toPx()) // 边缘折射（API33+ 自动生效）
                },
                highlight = { Highlight.Default },             // 边缘高光
                innerShadow = { InnerShadow(radius = 6f.dp) }, // 内阴影增厚玻璃体积感
                onDrawSurface = { drawRect(Color.White.copy(alpha = SURFACE_ALPHA)) },
            )
    ) {
        Row(
            Modifier
                .height(56.dp)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InputPill(hint = inputHint, onClick = onInputClick, modifier = Modifier.weight(1f))
            actions.forEach { a ->
                ActionItem(icon = a.icon, label = a.label, onClick = a.onClick)
            }
        }
    }
}

/** 左侧输入样式胶囊：38dp 高、浅灰微实心、铅笔图标 + 灰字 */
@Composable
private fun InputPill(
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(1f) }
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(Color(0xFFE1E3E6).copy(alpha = 0.9f))
            .scale(scale.value)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        scale.animateTo(0.94f, spring(stiffness = Spring.StiffnessMedium))
                        tryAwaitRelease()
                        scale.animateTo(1.04f, spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy))
                        scale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
                        onClick()
                    }
                )
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Default.Edit, null, tint = PillGray, modifier = Modifier.size(16.dp))
        Text(hint, color = PillGray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** 右侧操作图标：22dp 深灰图标 + 正下方 10sp 居中小字 + 果冻按压反馈 */
@Composable
fun ActionItem(
    icon: ImageVector,
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
    iconColor: Color = IconDark,
) {
    val scale = remember { Animatable(1f) }
    Column(
        modifier = modifier
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
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(iconSize))
        if (label != null) {
            Text(
                label,
                color = Color(0xFF6E7175),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
