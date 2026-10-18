package cn.ahuya.glasslive.ui.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.ahuya.glasslive.ui.glass.liquid.LiquidButton
import com.kyant.backdrop.Backdrop

data class BarAction(
    val icon: ImageVector,
    val label: String? = null,
    val onClick: () -> Unit = {},
)

private val PillGray = Color(0xFF7C7F84)
private val IconDark = Color(0xFF3F4245)
private val LabelGray = Color(0xFF55585C)

private fun boldStyle(size: TextUnit, color: Color) = TextStyle(
    color = color,
    fontSize = size,
    lineHeight = size,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

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
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ⭐ 左侧让位：weight(1f) 只拿"右侧吃完后剩下"的空间，宽屏舒展、窄屏收缩
        LiquidButton(
            onClick = onInputClick,
            backdrop = backdrop,
            modifier = Modifier.weight(1f),
            height = 48.dp,
            horizontalPadding = 12.dp,
            surfaceColor = Color(0xFFEDEFF2).copy(alpha = 0.90f),
        ) {
            Icon(Icons.Default.Edit, null, tint = PillGray, modifier = Modifier.size(16.dp))
            Text(inputHint, style = boldStyle(14.sp, PillGray), maxLines = 1)
        }

        // ⭐ 右侧优先：无 weight，Row 先按理想宽度足量测量它，图标/小字/留白全舒展
        LiquidButton(
            onClick = { },
            backdrop = backdrop,
            height = 48.dp,
            horizontalPadding = 6.dp,
            blurRadius = 12.dp,
            surfaceColor = Color.White.copy(alpha = 0.30f),
        ) {
            actions.forEach { a -> ActionColumn(a) }
        }
    }
}

@Composable
private fun ActionColumn(action: BarAction) {
    Column(
        Modifier
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = action.onClick
            )
            .padding(horizontal = 6.dp),   // 图标间留白
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(action.icon, null, tint = IconDark, modifier = Modifier.size(22.dp))
        if (!action.label.isNullOrEmpty()) {
            Text(
                action.label,
                style = boldStyle(10.sp, LabelGray),
                maxLines = 1,   // ⭐ 兜底：任何极端宽度下绝不换行
            )
        }
    }
}
