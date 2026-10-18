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

/** ⭐ 粗黑体 + 光学居中 + 去字体内边距（解决"没居中/发毛"） */
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
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 左：写弹幕胶囊（浅灰实面，字粗黑光学居中）
        LiquidButton(
            onClick = onInputClick,
            backdrop = backdrop,
            modifier = Modifier.weight(1f),
            height = 56.dp,
            horizontalPadding = 16.dp,
            surfaceColor = Color(0xFFEDEFF2).copy(alpha = 0.90f),
        ) {
            Icon(Icons.Default.Edit, null, tint = PillGray, modifier = Modifier.size(16.dp))
            Text(inputHint, style = boldStyle(15.sp, PillGray))
        }

        // 右：通透玻璃胶囊（白 0.30 + blur10），四个"图标+粗黑小字"直排，无内嵌按压块
        LiquidButton(
            onClick = { },
            backdrop = backdrop,
            height = 56.dp,
            horizontalPadding = 4.dp,
            blurRadius = 10.dp,
            surfaceColor = Color.White.copy(alpha = 0.30f),
        ) {
            actions.forEach { a -> ActionColumn(a) }
        }
    }
}

/** 图标 + 粗黑小字；点击无涟漪（灰色按压块已移除），物理反馈交给外层 LiquidButton */
@Composable
private fun ActionColumn(action: BarAction) {
    Column(
        Modifier
            .clickable(
                interactionSource = null,
                indication = null,   // ⭐ 干掉内嵌灰色按压效果
                onClick = action.onClick
            )
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(action.icon, null, tint = IconDark, modifier = Modifier.size(22.dp))
        if (!action.label.isNullOrEmpty()) {
            Text(action.label, style = boldStyle(10.sp, LabelGray))
        }
    }
}
