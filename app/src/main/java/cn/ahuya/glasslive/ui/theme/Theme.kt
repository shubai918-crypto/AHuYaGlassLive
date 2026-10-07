package cn.ahuya.glasslive.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00D2FF),      // 虎牙蓝
    secondary = Color(0xFFFF8800),    // 虎牙橙
    background = Color(0xFF101014),   // 极夜黑
    surface = Color(0xFF1C1C1E),      // 玻璃底色
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun AHuYaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
