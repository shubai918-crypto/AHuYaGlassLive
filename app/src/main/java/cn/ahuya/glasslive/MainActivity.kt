package cn.ahuya.glasslive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cn.ahuya.glasslive.ui.play.LivePlayPage
import cn.ahuya.glasslive.ui.theme.AHuYaTheme

// TODO: 换成你想测试的房间号；首页房间列表在后续批次接入
private const val DEFAULT_ROOM_ID = "152742"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AHuYaTheme {
                LivePlayPage(roomId = DEFAULT_ROOM_ID)
            }
        }
    }
}
