package cn.ahuya.glasslive.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.ahuya.glasslive.data.huya.HuyaDanmakuClient
import cn.ahuya.glasslive.data.huya.HuyaOfflineException
import cn.ahuya.glasslive.data.huya.HuyaStreamResolver
import cn.ahuya.glasslive.data.huya.model.CdnUrl
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
    private var qualityIndex = 0
    private var candidateIndex = 0
    private var retryCount = 0
    private var lastError = ""

    var danmakuClient: HuyaDanmakuClient? = null
        private set

    fun enterRoom(roomId: String) {
        viewModelScope.launch {
            _uiState.value = PlayState.Loading
            try {
                val result = HuyaStreamResolver.resolve(roomId)
                currentResult = result
                qualityIndex = 0
                candidateIndex = 0
                retryCount = 0
                runCatching { connectDanmaku(result) }
                playCurrent()
            } catch (e: HuyaOfflineException) {
                _uiState.value = PlayState.Error("主播未开播：${e.nick} 当前不在直播")
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
        val q = res.qualities.getOrNull(qualityIndex) ?: res.qualities.first()
        val c = q.candidates.getOrNull(candidateIndex) ?: q.candidates.first()
        _uiState.value = PlayState.Playing(c.url, q, c, res)
    }

    /** 播放器报错：先换 CDN 候选 → 再降清晰度 → 最后重新解析 */
    fun onPlayerError(errInfo: String) {
        lastError = errInfo
        val res = currentResult ?: return
        val q = res.qualities.getOrNull(qualityIndex)
        if (q != null && candidateIndex < q.candidates.size - 1) {
            candidateIndex++; playCurrent(); return
        }
        if (qualityIndex < res.qualities.size - 1) {
            qualityIndex++; candidateIndex = 0; playCurrent(); return
        }
        retryCount++
        if (retryCount < 3) { enterRoom(res.roomId); return }
        val last = q?.candidates?.lastOrNull()
        _uiState.value = PlayState.Error(
            "所有线路均不可用\n最后错误: $lastError\n末次: ${q?.name}/${last?.cdn}"
        )
    }

    fun switchQuality(index: Int) { qualityIndex = index; candidateIndex = 0; playCurrent() }
    fun switchCandidate(index: Int) { candidateIndex = index; playCurrent() }

    override fun onCleared() {
        super.onCleared()
        danmakuClient?.disconnect()
    }
}

sealed class PlayState {
    object Loading : PlayState()
    data class Playing(
        val url: String,
        val quality: StreamQuality,
        val candidate: CdnUrl,
        val result: HuyaStreamResult,
    ) : PlayState()
    data class Offline(val result: HuyaStreamResult?) : PlayState()
    data class Error(val msg: String) : PlayState()
}
