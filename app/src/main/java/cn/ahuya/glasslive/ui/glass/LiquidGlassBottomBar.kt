package cn.ahuya.glasslive.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// ⭐ 官方教程标准导入
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

data class BarAction(
    val icon: ImageVector,
    val label: String? = null,
    val onClick: () -> Unit = {},
)

private val PillGray = Color(0xFF8A8D91)
private val IconDark = Color(0xFF5F6368)
private val LabelGray = Color(0xFF80868B)

@Composable
fun LiquidGlassBottomBar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    inputHint: String = "写弹幕",
    onInputClick: () -> Unit = {},
    actions: List<BarAction> = emptyList(),
) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            // ⭐ 教程 Step1/3/4 配方：lens 折射 + vibrancy 提饱和 + blur + 白 0.5 表面保证可读性
            //   边缘高光由 drawBackdrop 默认 Highlight 提供（API31 同样生效）
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(12f.dp.toPx())          // 教程值为 4；直播画面信息量大，调到 12 更耐看
                    lens(16f.dp.toPx(), 32f.dp.toPx())
                },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.5f)) }
            )
            .height(56.dp)
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        InputPill(hint = inputHint, onClick = onInputClick, modifier = Modifier.weight(1f))
        actions.forEach { a ->
            ActionItem(icon = a.icon, label = a.label, onClick = a.onClick)
        }
    }
}

@Composable
private fun InputPill(
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(1f) }
    Row(
        modifier
            .height(38.dp)
            .background(Color(0xFFEFF1F4).copy(alpha = 0.92f), RoundedCornerShape(19.dp))
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
        modifier
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
            Text(label, color = LabelGray, fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}
