package cn.ahuya.glasslive.ui.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.ahuya.glasslive.ui.glass.liquid.LiquidButton
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
            // 容器配方 = catalog LiquidBottomTabs：vibrancy + blur8 + lens24 + 浅色 surface
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(24f.dp.toPx(), 24f.dp.toPx())
                },
                onDrawSurface = { drawRect(Color(0xFFFAFAFA).copy(alpha = 0.4f)) }
            )
            .height(56.dp)
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 左侧输入胶囊 = 官方 LiquidButton（浅灰实心 surface）
        LiquidButton(
            onClick = onInputClick,
            backdrop = backdrop,
            modifier = Modifier.weight(1f),
            height = 38.dp,
            horizontalPadding = 12.dp,
            surfaceColor = Color(0xFFEDEFF2).copy(alpha = 0.9f),
        ) {
            Icon(Icons.Default.Edit, null, tint = PillGray, modifier = Modifier.size(16.dp))
            Text(inputHint, color = PillGray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        // 右侧操作项 = 官方 LiquidButton（透明玻璃，按压有官方果冻+光晕）
        actions.forEach { a ->
            LiquidButton(
                onClick = a.onClick,
                backdrop = backdrop,
                height = 44.dp,
                horizontalPadding = 6.dp,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Icon(a.icon, null, tint = IconDark, modifier = Modifier.size(22.dp))
                    if (a.label != null) {
                        Text(a.label, color = LabelGray, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
