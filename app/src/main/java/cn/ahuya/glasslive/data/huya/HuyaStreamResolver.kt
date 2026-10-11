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
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object HuyaStreamResolver {

    private const val API_URL = "https://mp.huya.com/cache.php?m=Live&do=profileRoom&roomid="
    private const val WEB_URL = "https://www.huya.com/"

    /** 线路优先级 al > hs > tx（对齐 Dart 版） */
    private val LINE_PRIORITY = mapOf("al" to 0, "hs" to 1, "tx" to 2)

    private val QUALITY_NAMES = mapOf(
        0 to "原画", 8000 to "蓝光8M", 4000 to "蓝光4M",
        2000 to "蓝光2M", 1000 to "超清", 500 to "流畅",
    )

    /** 入口：API 优先，Web 兜底；都失败则抛出具体原因 */
    suspend fun resolve(roomId: String): HuyaStreamResult = withContext(Dispatchers.IO) {
        val apiResult = runCatching { resolveByApi(roomId) }
        apiResult.getOrNull()?.let { return@withContext it }

        val webResult = runCatching { resolveByWeb(roomId) }
        webResult.getOrNull()?.let { return@withContext it }

        val apiErr = apiResult.exceptionOrNull()?.message ?: "空结果"
        val webErr = webResult.exceptionOrNull()?.message ?: "空结果"
        throw RuntimeException("API:$apiErr | WEB:$webErr")
    }

    // ================= API 端 =================
    private suspend fun resolveByApi(roomId: String): HuyaStreamResult? {
        val body = HttpClient.get(API_URL + roomId, ua = HttpClient.MOBILE_UA)
        val root = JSONObject(body)
        val data = root.optJSONObject("data") ?: return null
        val b64 = data.optJSONObject("stream")?.optString("base64Stream").orEmpty()
        if (b64.isEmpty()) return null
        val payload = JSONObject(b64.decodeBase64())

        val liveData = data.optJSONObject("liveData")
        return buildFromPayload(
            payload = payload,
            fallbackNick = liveData?.optString("nick").orEmpty(),
            fallbackAvatar = liveData?.optString("avatar18").orEmpty(),
            fallbackFans = liveData?.optLong("fansCount") ?: 0L,
            fallbackTitle = liveData?.optString("introduction").orEmpty(),
            fallbackCover = liveData?.optString("screenshot").orEmpty(),
            isLive = data.optString("liveStatus", "ON").equals("ON", true),
            roomId = roomId,
        )
    }

    // ================= Web 端兜底 =================
    private suspend fun resolveByWeb(roomId: String): HuyaStreamResult? {
        val html = HttpClient.get(WEB_URL + roomId, ua = HttpClient.PC_UA)
        val b64 = extractBase64(html) ?: return null
        val payload = JSONObject(b64.decodeBase64())
        return buildFromPayload(
            payload = payload,
            fallbackNick = html.strOf("\"nick\"\\s*:\\s*\"([^\"]*)\""),
            fallbackAvatar = html.strOf("\"avatar18\"\\s*:\\s*\"([^\"]*)\""),
            fallbackFans = html.longOf("\"fansCount\"\\s*:\\s*(\\d+)"),
            fallbackTitle = html.strOf("\"introduction\"\\s*:\\s*\"([^\"]*)\""),
            fallbackCover = html.strOf("\"screenshot\"\\s*:\\s*\"([^\"]*)\""),
            isLive = true,
            roomId = roomId,
        )
    }

    /** 多正则兜底提取 base64 流数据 */
    private fun extractBase64(html: String): String? {
        val patterns = listOf(
            "\"stream\"\\s*:\\s*\"([A-Za-z0-9+/=\\n]+)\"",
            "TT_STREAM_DATA\\s*=\\s*\"([A-Za-z0-9+/=\\n]+)\"",
            "\"base64Stream\"\\s*:\\s*\"([A-Za-z0-9+/=\\n]+)\"",
        )
        for (p in patterns) {
            val m = Regex(p).find(html)?.groupValues?.get(1)
            if (!m.isNullOrEmpty()) return m.replace(Regex("\\s"), "")
        }
        return null
    }

    // ================= 核心：兼容所有 JSON 结构 =================
    private fun buildFromPayload(
        payload: JSONObject,
        fallbackNick: String, fallbackAvatar: String, fallbackFans: Long,
        fallbackTitle: String, fallbackCover: String,
        isLive: Boolean, roomId: String,
    ): HuyaStreamResult? {
        val streamNameGlobal = deepFind(payload, "sStreamName") as? String ?: ""
        val lines = mutableListOf<HuyaLine>()

        // 结构 A：gameStreamInfoList（PC/通用）
        val gsi = deepFind(payload, "gameStreamInfoList") as? JSONArray
        if (gsi != null) {
            for (i in 0 until gsi.length()) {
                val o = gsi.optJSONObject(i) ?: continue
                val tag = o.optString("sCdnType").lowercase()
                val name = o.optString("sStreamName").ifEmpty { streamNameGlobal }
                val useHls = o.optInt("iIsHls") == 1 || o.optString("sHlsUrl").isNotEmpty()
                lines += if (useHls) {
                    HuyaLine(
                        tag = tag,
                        flvUrl = o.optString("sHlsUrl"),
                        streamName = name,
                        suffix = o.optString("sHlsUrlSuffix").ifEmpty { "m3u8" },
                        antiCode = o.optString("sHlsAntiCode"),
                        isHls = true,
                    )
                } else {
                    HuyaLine(
                        tag = tag,
                        flvUrl = o.optString("sFlvUrl"),
                        streamName = name,
                        suffix = o.optString("sFlvUrlSuffix").ifEmpty { "flv" },
                        antiCode = o.optString("sFlvAntiCode"),
                        isHls = false,
                    )
                }
            }
        } else {
            // 结构 B：vMultiLine（移动端）
            val vml = deepFind(payload, "vMultiLine") as? JSONArray
            if (vml != null) {
                for (i in 0 until vml.length()) {
                    val o = vml.optJSONObject(i) ?: continue
                    val url = o.optString("sFlvUrl")
                    lines += HuyaLine(
                        tag = url.substringAfter("://").substringBefore("."),
                        flvUrl = url,
                        streamName = o.optString("sStreamName").ifEmpty { streamNameGlobal },
                        suffix = o.optString("sFlvUrlSuffix")
                            .ifEmpty { if (o.optInt("iIsHls") == 1) "m3u8" else "flv" },
                        antiCode = o.optString("sFlvAntiCode"),
                        isHls = o.optInt("iIsHls") == 1,
                    )
                }
            }
        }

        val validLines = lines.filter {
            it.flvUrl.isNotEmpty() && it.streamName.isNotEmpty() && it.antiCode.isNotEmpty()
        }
        if (validLines.isEmpty()) return null

        // 清晰度
        val qualities = mutableListOf(StreamQuality(0, "原画"))
        val vbr = deepFind(payload, "vBitRate") as? JSONArray
        if (vbr != null) {
            for (i in 0 until vbr.length()) {
                val o = vbr.optJSONObject(i) ?: continue
                val rate = o.optInt("iBitRate")
                if (rate == 0) continue
                qualities += StreamQuality(
                    rate,
                    o.optString("sBitRateDisplayName")
                        .ifEmpty { QUALITY_NAMES[rate] ?: "档位$rate" }
                )
            }
        }

        // 房间 / 主播信息
        val gli = deepFind(payload, "gameLiveInfo") as? JSONObject
        val pi = deepFind(payload, "playerInfo") as? JSONObject
        val ayyuid = gli?.optLong("lYyid") ?: gli?.optLong("lPresenterUid") ?: 0L
        val topSid = gli?.optLong("lChannelId") ?: gli?.optLong("lTid") ?: 0L
        val subSid = gli?.optLong("lSubChannelId") ?: gli?.optLong("lSid") ?: 0L
        val nick = pi?.optString("sNick").orEmpty()
            .ifEmpty { gli?.optString("sNick").orEmpty() }
            .ifEmpty { fallbackNick }
        val avatar = pi?.optString("sAvatar18").orEmpty().ifEmpty { fallbackAvatar }
        val fans = pi?.optLong("lFansCount") ?: fallbackFans
        val title = gli?.optString("sRoomName").orEmpty()
            .ifEmpty { gli?.optString("sIntroduction").orEmpty() }
            .ifEmpty { fallbackTitle }
        val cover = gli?.optString("sScreenshot").orEmpty().ifEmpty { fallbackCover }
        val startTime = gli?.optLong("lLiveStartTime") ?: 0L
        val heat = gli?.optLong("lTotalCount") ?: gli?.optLong("lAttendeeCount") ?: 0L

        return HuyaStreamResult(
            roomId = roomId,
            ayyuid = ayyuid,
            topSid = topSid,
            subSid = subSid,
            title = title,
            cover = cover,
            heat = heat,
            startTime = startTime,
            isLive = isLive,
            streamer = StreamerInfo(ayyuid, nick, avatar, fans, isLive),
            qualities = qualities,
            lines = validLines.sortedBy { LINE_PRIORITY[it.tag] ?: 99 },
        )
    }

    /** 深度递归搜索：无论虎牙把字段藏在哪一层都能挖出来 */
    private fun deepFind(node: Any?, key: String): Any? {
        when (node) {
            is JSONObject -> {
                if (node.has(key)) return node.opt(key)
                val it = node.keys()
                while (it.hasNext()) {
                    val r = deepFind(node.opt(it.next()), key)
                    if (r != null) return r
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val r = deepFind(node.opt(i), key)
                    if (r != null) return r
                }
            }
        }
        return null
    }

    // ================= 签名 & 播放地址 =================
    fun generateWebAntiCode(antiCode: String, streamName: String, uid: Long): String {
        val p = mutableMapOf<String, String>()
        for (kv in antiCode.split("&")) {
            val i = kv.indexOf('=')
            if (i > 0) {
                p[kv.substring(0, i)] = kv.substring(i + 1).urlDecode()
            }
        }
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

    fun buildPlayUrl(line: HuyaLine, quality: StreamQuality, uid: Long): String {
        val code = generateWebAntiCode(line.antiCode, line.streamName, uid)
        val ratio = if (quality.bitRate == 0) "" else "&ratio=${quality.bitRate}"
        return "${line.flvUrl}/${line.streamName}.${line.suffix}?$code$ratio"
    }

    private fun String.longOf(regex: String): Long =
        Regex(regex).find(this)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    private fun String.strOf(regex: String): String =
        Regex(regex).find(this)?.groupValues?.get(1) ?: ""
}
