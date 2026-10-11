package cn.ahuya.glasslive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.ahuya.glasslive.ui.play.LivePlayPage
import cn.ahuya.glasslive.ui.theme.AHuYaTheme
import java.io.File

// TODO: 换成正在开播的真实房间号
private const val DEFAULT_ROOM_ID = "152746"

class MainActivity : ComponentActivity() {

    private val crashFile: File get() = File(filesDir, "crash_log.txt")

    override fun onCreate(savedInstanceState: Bundle?) {
        // ⭐ 崩溃黑匣子：崩了就把堆栈写进文件，下次启动显示
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                crashFile.writeText("Thread: ${thread.name}\n" + e.stackTraceToString())
            } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, e)
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val savedCrash = crashFile.takeIf { it.exists() }?.readText()

        setContent {
            AHuYaTheme {
                var crashText by remember { mutableStateOf(savedCrash) }
                if (crashText != null) {
                    CrashScreen(crashText!!) {
                        crashFile.delete()
                        crashText = null
                    }
                } else {
                    LivePlayPage(roomId = DEFAULT_ROOM_ID)
                }
            }
        }
    }
}

@Composable
private fun CrashScreen(text: String, onDismiss: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Color(0xFF101014)) {
        Column(Modifier.padding(16.dp)) {
            Text("💥 上次崩溃堆栈（请截图发给开发者）", color = Color(0xFFFF5252), fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxSize()) {
                Text("清除并进入应用")
            }
        }
    }
}
