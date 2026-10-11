package cn.ahuya.glasslive.ui.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.ahuya.glasslive.player.HuyaPlayerView
import cn.ahuya.glasslive.player.LivePlayViewModel
import cn.ahuya.glasslive.player.PlayState

@Composable
fun LivePlayPage(
    roomId: String,
    viewModel: LivePlayViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var videoReady by remember { mutableStateOf(false) }

    LaunchedEffect(roomId) { viewModel.enterRoom(roomId) }

    // ⭐ 诊断期：移除 layerBackdrop / 玻璃栏，纯播放器，最小变量
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            PlayState.Loading -> CenterText("正在连接虎牙直播间…")
            is PlayState.Offline -> CenterText(
                s.result?.streamer?.nickname?.let { "$it 未开播" } ?: "房间不存在"
            )
            is PlayState.Error -> CenterText(s.msg)
            is PlayState.Playing -> {
                HuyaPlayerView(
                    url = s.url,
                    onError = { err ->
    videoReady = false
    viewModel.onPlayerError(err)
},
                    onFirstFrame = { videoReady = true },
                    modifier = Modifier.fillMaxSize(),
                )
                AnimatedVisibility(
                    visible = !videoReady,
                    enter = fadeIn(),
                    exit = fadeOut(animationSpec = tween(600)),
                ) {
                    Box(
                        Modifier.fillMaxSize().background(Color(0xFF101014)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color(0xFF00D2FF))
                    }
                }
                // 诊断期：暂不放任何底栏
            }
        }
    }
}

@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White)
    }
}
