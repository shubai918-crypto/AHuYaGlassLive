package cn.ahuya.glasslive.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.ahuya.glasslive.data.huya.HuyaDanmakuClient
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

    var danmakuClient: HuyaDanmakuClient? = null
        private set

    fun enterRoom(roomId: String) {
        viewModelScope.launch {
            _uiState.value = PlayState.Loading
            val result = HuyaStreamResolver.resolve(roomId)
            if (result == null || !result.isLive) {
                _uiState.value = PlayState.Offline(result)
                return@launch
            }
            currentResult = result
            currentLineIndex = 0
            currentQualityIndex = 0

            danmakuClient = HuyaDanmakuClient(result.ayyuid, result.topSid, result.subSid)
            danmakuClient?.connect()

            playCurrent()
        }
    }

    private fun playCurrent() {
        val res = currentResult ?: return
        if (res.lines.isEmpty() || res.qualities.isEmpty()) {
            _uiState.value = PlayState.Error("无可用线路或清晰度")
            return
        }
        val line = res.lines[currentLineIndex]
        val quality = res.qualities[currentQualityIndex]
        val url = HuyaStreamResolver.buildPlayUrl(line, quality, res.ayyuid)
        _uiState.value = PlayState.Playing(url, line, quality, res)
    }

    /** 播放器报错时调用：先降清晰度，再换线路 */
    fun onPlayerError() {
        val res = currentResult ?: return
        if (currentQualityIndex < res.qualities.size - 1) {
            currentQualityIndex++
        } else if (currentLineIndex < res.lines.size - 1) {
            currentLineIndex++
            currentQualityIndex = 0
        } else {
            _uiState.value = PlayState.Error("所有线路均不可用")
            return
        }
        playCurrent()
    }

    override fun onCleared() {
        super.onCleared()
        danmakuClient?.disconnect()
    }
}

sealed class PlayState {
    object Loading : PlayState()
    data class Playing(
        val url: String,
        val line: HuyaLine,
        val quality: StreamQuality,
        val result: HuyaStreamResult,
    ) : PlayState()
    data class Offline(val result: HuyaStreamResult?) : PlayState()
    data class Error(val msg: String) : PlayState()
}
