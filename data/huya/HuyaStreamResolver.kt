package cn.ahuya.glasslive.data.huya

import cn.ahuya.glasslive.core.net.HttpClient
import cn.ahuya.glasslive.core.util.decodeBase64
import cn.ahuya.glasslive.core.util.md5
import cn.ahuya.glasslive.core.util.urlDecode
import cn.ahuya.glasslive.data.huya.model.HuyaLine
import cn.ahuya.glasslive.data.huya.model.HuyaStreamResult
import cn.ahuya.glasslive.data.huya.model.StreamQuality
import cn.ahuya.glasslive.data.huya.model.StreamerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64

object HuyaStreamResolver {

    private const val API_URL = "https://mp.huya.com/cache.php?m=Live&do=profileRoom&roomid="
    private const val WEB_URL = "https://www.huya.com/"

    /** 线路优先级 al > hs > tx（对齐 Dart 版修正逻辑） */
    private val LINE_PRIORITY = mapOf("al" to 0, "hs" to 1, "tx" to 2)

    private val QUALITY_NAMES = mapOf(
        0 to "原画", 8000 to "蓝光8M", 4000 to "蓝光4M",
        2000 to "蓝光2M", 1000 to "超清", 500 to "流畅",
    )

    /** 入口：API 端优先，失败自动回退网页端 */
    suspend fun resolve(roomId: String): HuyaStreamResult? =
        withContext(Dispatchers.IO) {
            try {
                resolveByApi(roomId) ?: resolveByWeb(roomId)
            } catch (_: Exception) { null }
        }

    // ================= API 端 =================
    private suspend fun resolveByApi(roomId: String): HuyaStreamResult? {
        return try {
            val root = JSONObject(HttpClient.get(API_URL + roomId, ua = HttpClient.MOBILE_UA))
            val data = root.optJSONObject("data") ?: return null
            val liveData = data.optJSONObject("liveData")
            val b64 = data.optJSONObject("stream")?.optString("base64Stream").orEmpty()
            if (b64.isEmpty()) return null

            val stream = extractStreamNode(JSONObject(b64.decodeBase64())) ?: return null
            buildResult(
                roomId = roomId, stream = stream,
                uid = liveData?.optLong("yyid") ?: 0L,
                topSid = liveData?.optLong("lTid") ?: liveData?.optLong("tid") ?: 0L,
                subSid = liveData?.optLong("lSid") ?: liveData?.optLong("sid") ?: 0L,
                nickname = liveData?.optString("nick").orEmpty(),
                avatar = liveData?.optString("avatar18").orEmpty(),
                fans = liveData?.optLong("fansCount") ?: 0L,
                title = liveData?.optString("introduction").orEmpty(),
                cover = liveData?.optString("screenshot").orEmpty(),
                heat = liveData?.optLong("totalCount") ?: 0L,
                startTime = liveData?.optLong("startTime") ?: 0L,
                isLive = data.optString("liveStatus", "ON").equals("ON", true),
            )
        } catch (_: Exception) { null }
    }

    // ================= 网页端兜底 =================
    private suspend fun resolveByWeb(roomId: String): HuyaStreamResult? {
        return try {
            val html = HttpClient.get(WEB_URL + roomId)
            val b64 = Regex("\"stream\"\\s*:\\s*\"([A-Za-z0-9+/=\\n]+)\"")
                .find(html)?.groupValues?.get(1) ?: return null
            val stream = extractStreamNode(JSONObject(b64.decodeBase64())) ?: return null
            buildResult(
                roomId = roomId, stream = stream,
                uid = html.longOf("\"yyid\"\\s*:\\s*(\\d+)"),
                topSid = html.longOf("\"lTid\"\\s*:\\s*(\\d+)"),
                subSid = html.longOf("\"lSid\"\\s*:\\s*(\\d+)"),
                nickname = html.strOf("\"nick\"\\s*:\\s*\"([^\"]*)\""),
                avatar = html.strOf("\"avatar18\"\\s*:\\s*\"([^\"]*)\""),
                fans = html.longOf("\"fansCount\"\\s*:\\s*(\\d+)"),
                title = html.strOf("\"introduction\"\\s*:\\s*\"([^\"]*)\""),
                cover = html.strOf("\"screenshot\"\\s*:\\s*\"([^\"]*)\""),
                heat = html.longOf("\"totalCount\"\\s*:\\s*\"?(\\d+)"),
                startTime = html.longOf("\"startTime\"\\s*:\\s*(\\d+)"),
                isLive = true,
            )
        } catch (_: Exception) { null }
    }

    // ================= 公共解析 =================
    private fun extractStreamNode(root: JSONObject): JSONObject? {
        root.optJSONObject("stream")?.let { return it }
        val arr = root.optJSONArray("data") ?: return null
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.optJSONObject("stream")?.let { return it }
        }
        return null
    }

    private fun buildResult(
        roomId: String, stream: JSONObject,
        uid: Long, topSid: Long, subSid: Long,
        nickname: String, avatar: String, fans: Long,
        title: String, cover: String, heat: Long,
        startTime: Long, isLive: Boolean,
    ): HuyaStreamResult? {
        val streamName = stream.optString("sStreamName")
        val multi = stream.optJSONArray("vMultiLine")
        if (streamName.isEmpty() || multi == null) return null

        // ---- 线路 ----
        val lines = mutableListOf<HuyaLine>()
        for (i in 0 until multi.length()) {
            val o = multi.optJSONObject(i) ?: continue
            val url = o.optString("sFlvUrl")
            lines += HuyaLine(
                tag = url.substringAfter("://").substringBefore("."),
                flvUrl = url,
                streamName = streamName,
                suffix = o.optString("sFlvUrlSuffix")
                    .ifEmpty { if (o.optInt("iIsHls") == 1) "m3u8" else "flv" },
                antiCode = o.optString("sFlvAntiCode"),
                isHls = o.optInt("iIsHls") == 1,
            )
        }
        if (lines.isEmpty()) return null

        // ---- 清晰度 ----
        val qualities = mutableListOf(StreamQuality(0, "原画"))
        val bits = stream.optJSONArray("vBitRate")
        for (i in 0 until (bits?.length() ?: 0)) {
            val o = bits?.optJSONObject(i) ?: continue
            val rate = o.optInt("iBitRate")
            if (rate == 0) continue
            qualities += StreamQuality(
                rate,
                o.optString("sBitRateDisplayName")
                    .ifEmpty { QUALITY_NAMES[rate] ?: "档位$rate" }
            )
        }

        return HuyaStreamResult(
            roomId = roomId, ayyuid = uid, topSid = topSid, subSid = subSid,
            title = title, cover = cover, heat = heat, startTime = startTime,
            isLive = isLive,
            streamer = StreamerInfo(uid, nickname, avatar, fans, isLive),
            qualities = qualities,
            lines = lines.sortedBy { LINE_PRIORITY[it.tag] ?: 99 },
        )
    }

    // ================= 签名 & 播放地址 =================

    /** 对齐 Dart 版 generateWebAntiCode：三段 MD5 派生 wsSecret */
    fun generateWebAntiCode(antiCode: String, streamName: String, uid: Long): String {
        val p = antiCode.split("&").mapNotNull { kv ->
            val i = kv.indexOf('=')
            if (i > 0) kv.substring(0, i) to kv.substring(i + 1).urlDecode() else null
        }.toMutableMap()

        val ss = (p["fm"] ?: "").urlDecode().substringBefore("_")
        val wsTime = p["wsTime"] ?: ""
        val ctype = p["ctype"] ?: "web"
        val t = p["t"] ?: "100"
        val seqid = System.currentTimeMillis() + uid

        val ss2 = "$seqid|$ctype|$t".md5()
        val ss3 = "${ss}_${wsTime}_${streamName}_${seqid}".md5()
        p["wsSecret"] = "${ss}_${ss2}_${ss3}".md5()
        p["seqid"] = seqid.toString()

        return p.entries.joinToString("&") { (k, v) ->
            "$k=" + URLEncoder.encode(v, "UTF-8")
        }
    }

    /** 拼装最终播放地址（quality.bitRate=0 时不附加 ratio，即原画） */
    fun buildPlayUrl(line: HuyaLine, quality: StreamQuality, uid: Long): String {
        val code = generateWebAntiCode(line.antiCode, line.streamName, uid)
        val ratio = if (quality.bitRate == 0) "" else "&ratio=${quality.bitRate}"
        return "${line.flvUrl}/${line.streamName}.${line.suffix}?$code$ratio"
    }

    // ---- 正则小工具 ----
    private fun String.longOf(regex: String): Long =
        Regex(regex).find(this)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    private fun String.strOf(regex: String): String =
        Regex(regex).find(this)?.groupValues?.get(1) ?: ""
}
