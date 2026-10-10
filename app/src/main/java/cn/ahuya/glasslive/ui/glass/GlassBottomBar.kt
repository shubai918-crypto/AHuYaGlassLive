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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy

@Composable
fun GlassBottomBar(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 左侧：发弹幕 Pill
        DanmakuInputPill(
            backdrop = backdrop,
            modifier = Modifier.weight(1f),
            onClick = { /* TODO: 唤起发弹幕面板 */ }
        )

        // 右侧：单胶囊图标组
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .drawBackdrop(backdrop) {
                    blur(24.dp)         // 核心：实时高斯模糊穿透视频
                    vibrancy(0.6f)      // 色彩增强：让背后的视频颜色透过来
                }
                .background(Color.White.copy(alpha = 0.05f)) // 微弱底色增强玻璃质感
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            GlassIconAction(Icons.Default.Hd, "清晰度") { /* TODO */ }
            GlassIconAction(Icons.Default.Settings, "设置") { /* TODO */ }
            GlassIconAction(Icons.Default.EmojiEmotions, "表情") { /* TODO */ }
        }
    }
}

@Composable
private fun DanmakuInputPill(
    backdrop: LayerBackdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 🍮 果冻物理引擎
    val scale = remember { Animatable(1f) }
    
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .drawBackdrop(backdrop) {
                blur(24.dp)
                vibrancy(0.8f)
            }
            .background(Color.White.copy(alpha = 0.08f))
            .scale(scale.value)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // 按下：瞬间缩小
                        scale.animateTo(0.92f, spring(stiffness = Spring.StiffnessMedium))
                        tryAwaitRelease()
                        // 松开：Q弹过冲放大，然后回弹
                        scale.animateTo(1.06f, spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy))
                        scale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
                        onClick()
                    }
                )
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Edit, contentDescription = "发弹幕", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
        Text("发弹幕", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GlassIconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
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
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
    }
}
