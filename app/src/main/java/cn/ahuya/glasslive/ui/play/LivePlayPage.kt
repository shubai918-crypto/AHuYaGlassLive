package cn.ahuya.glasslive.ui.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
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
import cn.ahuya.glasslive.ui.glass.BarAction
import cn.ahuya.glasslive.ui.glass.LiquidGlassBottomBar
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun LivePlayPage(
    roomId: String,
    viewModel: LivePlayViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var videoReady by remember { mutableStateOf(false) }

    LaunchedEffect(roomId) { viewModel.enterRoom(roomId) }

    // ⭐ 教程 Step2：背景色也要画进 backdrop，避免透明像素
    val backdrop = rememberLayerBackdrop {
        drawRect(Color.Black)
        drawContent()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            PlayState.Loading -> CenterText("正在连接虎牙直播间…")
            is PlayState.Offline -> CenterText(
                s.result?.streamer?.nickname?.let { "$it 未开播" } ?: "房间不存在"
            )
            is PlayState.Error -> CenterText(s.msg)
            is PlayState.Playing -> {
                // ⭐ 教程 Step1：layerBackdrop 包在背景内容（播放器）上
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
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
                }

                LiquidGlassBottomBar(
                    backdrop = backdrop,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onInputClick = { /* TODO: 发弹幕面板 */ },
                    actions = listOf(
                        BarAction(Icons.Outlined.Hd, "清晰度") { /* TODO */ },
                        BarAction(Icons.Outlined.Settings, "设置") { /* TODO */ },
                        BarAction(Icons.Outlined.Share, "转发") { /* TODO */ },
                    ),
                )
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
