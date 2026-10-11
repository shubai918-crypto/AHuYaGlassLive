package cn.ahuya.glasslive.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.ahuya.glasslive.data.huya.HuyaDanmakuClient
import cn.ahuya.glasslive.data.huya.HuyaOfflineException
import cn.ahuya.glasslive.data.huya.HuyaStreamResolver
import cn.ahuya.glasslive.data.huya.model.HuyaLine
import cn.ahuya.glasslive.data.huya.model.HuyaStreamResult
import cn.ahuya.glasslive.data.huya.model.StreamQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LivePlayViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<PlayState>(PlayState.Loading)
    val uiState: StateFlow<PlayState> = _uiState.asStateFlow()

    private var currentResult: HuyaStreamResult? = null
    private var currentLineIndex = 0
    private var currentQualityIndex = 0
    private var retryCount = 0

    var danmakuClient: HuyaDanmakuClient? = null
        private set

fun enterRoom(roomId: String) {
        viewModelScope.launch {
            _uiState.value = PlayState.Loading
            try {
                val result = HuyaStreamResolver.resolve(roomId)
                if (!result.isLive || result.lines.isEmpty()) {
                    _uiState.value = PlayState.Offline(result)
                    return@launch
                }
                currentResult = result
                currentLineIndex = 0
                currentQualityIndex = 0
                retryCount = 0
                runCatching { connectDanmaku(result) } // 弹幕挂了也不影响播放
                playCurrent()
            } catch (e: HuyaOfflineException) {
                _uiState.value = PlayState.Error("主播未开播：房间存在但当前不在直播，请换一个正在开播的房间号")
            } catch (t: Throwable) {
                _uiState.value = PlayState.Error("解析失败: ${t.message}")
            }
        }
    }

    private fun connectDanmaku(result: HuyaStreamResult) {
        danmakuClient?.disconnect()
        danmakuClient = HuyaDanmakuClient(result.ayyuid, result.topSid, result.subSid)
            .also { it.connect() }
    }

    private fun playCurrent() {
        val res = currentResult ?: return
        val line = res.lines.getOrNull(currentLineIndex) ?: res.lines.first()
        val quality = res.qualities.getOrNull(currentQualityIndex) ?: res.qualities.first()
        val url = HuyaStreamResolver.buildPlayUrl(line, quality, res.ayyuid)
        _uiState.value = PlayState.Playing(url, line, quality, res)
    }

    fun onPlayerError() {
        val res = currentResult ?: return
        retryCount++
        if (currentQualityIndex < res.qualities.size - 1) { currentQualityIndex++; playCurrent(); return }
        if (currentLineIndex < res.lines.size - 1) { currentLineIndex++; currentQualityIndex = 0; playCurrent(); return }
        if (retryCount < 3) enterRoom(res.roomId)
        else _uiState.value = PlayState.Error("所有线路均不可用")
    }

    fun switchQuality(index: Int) { currentQualityIndex = index; playCurrent() }
    fun switchLine(index: Int) { currentLineIndex = index; playCurrent() }

    override fun onCleared() {
        super.onCleared()
        danmakuClient?.disconnect()
    }
}

sealed class PlayState {
    object Loading : PlayState()
    data class Playing(val url: String, val line: HuyaLine, val quality: StreamQuality, val result: HuyaStreamResult) : PlayState()
    data class Offline(val result: HuyaStreamResult?) : PlayState()
    data class Error(val msg: String) : PlayState()
}
