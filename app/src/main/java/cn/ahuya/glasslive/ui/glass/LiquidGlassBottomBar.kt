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

// ⭐ 酷安墨色层级：近黑图标 / 深灰标签 / 近黑胶囊文字
private val PillInk = Color(0xFF2B2D31)
private val IconInk = Color(0xFF24262B)
private val LabelInk = Color(0xFF4A4C50)

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
        // 左：输入胶囊（比外杯更白一档）
        LiquidButton(
            onClick = onInputClick,
            backdrop = backdrop,
            modifier = Modifier.weight(1f),
            height = 56.dp,
            horizontalPadding = 16.dp,
            blurRadius = 10.dp,
            surfaceColor = Color.White.copy(alpha = 0.50f),
        ) {
            Icon(Icons.Default.Edit, null, tint = PillInk, modifier = Modifier.size(16.dp))
            Text(inputHint, style = boldStyle(15.sp, PillInk))
        }

        // 右：操作胶囊（亮白奶玻璃）
        LiquidButton(
            onClick = { },
            backdrop = backdrop,
            height = 56.dp,
            horizontalPadding = 4.dp,
            blurRadius = 12.dp,
            surfaceColor = Color.White.copy(alpha = 0.42f),
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
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(action.icon, null, tint = IconInk, modifier = Modifier.size(22.dp))
        if (!action.label.isNullOrEmpty()) {
            Text(action.label, style = boldStyle(10.sp, LabelInk))
        }
    }
}
