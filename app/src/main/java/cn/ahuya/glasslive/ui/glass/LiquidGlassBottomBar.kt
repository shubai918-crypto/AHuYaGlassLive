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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.ahuya.glasslive.ui.glass.liquid.LiquidButton
import com.kyant.backdrop.Backdrop

data class BarAction(
    val icon: ImageVector,
    val label: String? = null,
    val onClick: () -> Unit = {},
)

private val PillGray = Color(0xFF8A8D91)
private val IconDark = Color(0xFF4E5155)
private val LabelGray = Color(0xFF80868B)

/**
 * 酷安同款底栏 = 两个独立悬浮液态玻璃胶囊，无外层容器：
 * [写弹幕 LiquidButton]  [清晰度/设置/表情/转发 LiquidButton]
 */
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
        // 左：写弹幕胶囊（浅灰实面 LiquidButton）
        LiquidButton(
            onClick = onInputClick,
            backdrop = backdrop,
            modifier = Modifier.weight(1f),
            height = 56.dp,
            horizontalPadding = 16.dp,
            surfaceColor = Color(0xFFEDEFF2).copy(alpha = 0.92f),
        ) {
            Icon(Icons.Default.Edit, null, tint = PillGray, modifier = Modifier.size(16.dp))
            Text(inputHint, color = PillGray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }

        // 右：独立 LiquidGlass 胶囊，四个操作项全部放在里面
        LiquidButton(
            onClick = { /* 点击由内部各 ActionColumn 自行处理 */ },
            backdrop = backdrop,
            height = 56.dp,
            horizontalPadding = 6.dp,
            surfaceColor = Color(0xFFFAFAFA).copy(alpha = 0.4f),
        ) {
            actions.forEach { a -> ActionColumn(a) }
        }
    }
}

/** 胶囊内的操作项：图标 + 9sp 小字，独立可点 */
@Composable
private fun ActionColumn(action: BarAction) {
    Column(
        Modifier
            .clickable(onClick = action.onClick)
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(action.icon, null, tint = IconDark, modifier = Modifier.size(22.dp))
        if (!action.label.isNullOrEmpty()) {
            Text(action.label, color = LabelGray, fontSize = 9.sp, maxLines = 1)
        }
    }
}
