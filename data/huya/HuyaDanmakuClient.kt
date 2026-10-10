package cn.ahuya.glasslive.data.huya

import cn.ahuya.glasslive.data.huya.model.DanmakuMessage
import cn.ahuya.glasslive.data.huya.model.DanmakuType
import cn.ahuya.glasslive.data.huya.tars.TarsWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HuyaDanmakuClient(
    private val ayyuid: Long,
    private val topSid: Long,
    private val subSid: Long
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val _messages = MutableSharedFlow<DanmakuMessage>(replay = 0, extraBufferCapacity = 128)
    val messages: SharedFlow<DanmakuMessage> = _messages

    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect() {
        val request = Request.Builder().url("wss://cdnws.api.huya.com/").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(ByteString.of(*buildRegisterPacket()))
                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                parseMessage(bytes.toByteArray())
            }
        })
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(30_000)
                ws.send(ByteString.of(*buildHeartbeatPacket()))
            }
        }
    }

    private fun parseMessage(bytes: ByteArray) {
        try {
            val raw = String(bytes, Charsets.UTF_8)
            val jsonStart = raw.indexOf("{\"")
            if (jsonStart != -1) {
                val jsonStr = raw.substring(jsonStart)
                val jsonEnd = jsonStr.lastIndexOf("}")
                if (jsonEnd != -1) {
                    val json = JSONObject(jsonStr.substring(0, jsonEnd + 1))
                    extractDanmaku(json)
                }
            }
        } catch (_: Exception) {}
    }

    private fun extractDanmaku(json: JSONObject) {
        val data = json.optJSONObject("data") ?: json
        val content = data.optString("content", "")
        val nick = data.optString("sendNick", "")
        if (content.isNotEmpty() && nick.isNotEmpty()) {
            scope.launch {
                _messages.emit(DanmakuMessage(
                    type = DanmakuType.NORMAL,
                    nickname = nick,
                    content = content,
                    uid = data.optLong("senderUid", 0),
                    color = data.optInt("fontColor", 16777215),
                    badges = emptyList(),
                    isVip = false
                ))
            }
        }
    }

    private fun buildRegisterPacket(): ByteArray {
        val w = TarsWriter()
        w.writeStructBegin(0)
        w.writeLong(0, ayyuid)
        w.writeLong(1, topSid)
        w.writeLong(2, subSid)
        w.writeInt(3, 0)
        w.writeStructEnd()
        return w.toByteArray()
    }

    private fun buildHeartbeatPacket(): ByteArray {
        val w = TarsWriter()
        w.writeStructBegin(0)
        w.writeInt(0, 0)
        w.writeStructEnd()
        return w.toByteArray()
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        webSocket?.close(1000, "Leaving")
        scope.cancel()
    }
}
