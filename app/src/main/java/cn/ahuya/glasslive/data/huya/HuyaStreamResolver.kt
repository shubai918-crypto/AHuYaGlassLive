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

/** 房间存在但主播未开播 */
class HuyaOfflineException(val nick: String) : Exception("主播未开播")

object HuyaStreamResolver {

    private const val API_URL = "https://mp.huya.com/cache.php?m=Live&do=profileRoom&roomid="
    private const val WEB_URL = "https://www.huya.com/"
    private const val M_WEB_URL = "https://m.huya.com/"

    private val LINE_PRIORITY = mapOf("al" to 0, "hs" to 1, "tx" to 2)
    private val QUALITY_NAMES = mapOf(
        0 to "原画", 8000 to "蓝光8M", 4000 to "蓝光4M",
        2000 to "蓝光2M", 1000 to "超清", 500 to "流畅",
    )

    /** 入口：API -> WEB -> M端，全部失败则抛出带原始响应的诊断信息 */
    suspend fun resolve(roomId: String): HuyaStreamResult = withContext(Dispatchers.IO) {
        val diag = mutableListOf<String>()

        try {
            resolveByApi(roomId, diag)?.let { return@withContext it }
        } catch (e: HuyaOfflineException) {
            throw e
        } catch (e: Exception) {
            diag.add("API异常:${e.message}")
        }

        try {
            resolveByWeb(roomId, diag)?.let { return@withContext it }
        } catch (e: HuyaOfflineException) {
            throw e
        } catch (e: Exception) {
            diag.add("WEB异常:${e.message}")
        }

        try {
            resolveByMobileWeb(roomId, diag)?.let { return@withContext it }
        } catch (e: Exception) {
            diag.add("M端异常:${e.message}")
        }

        throw RuntimeException(diag.joinToString(" || ").ifEmpty { "全部返回空" })
    }

    // ================= API 端 =================
    private suspend fun resolveByApi(roomId: String, diag: MutableList<String>): HuyaStreamResult? {
        val body = HttpClient.get(API_URL + roomId, ua = HttpClient.MOBILE_UA)
        val root = JSONObject(body)
        val data = root.optJSONObject("data")
        if (data == null) {
            diag.add("API无data:${body.take(100)}")
            return null
        }
        val status = data.optString("liveStatus", "")
        if (status.isNotEmpty() && !status.equals("ON", true)) {
            throw HuyaOfflineException(data.optJSONObject("liveData")?.optString("nick").orEmpty())
        }
        val b64 = when (val sn = data.opt("stream")) {
            is JSONObject -> sn.optString("base64Stream")
            is String -> sn
            else -> ""
        }
        if (b64.isEmpty()) {
            diag.add("API无base64,status=$status,keys=${data.keys().asSequence().take(8).toList()}")
            return null
        }
        val payload = JSONObject(b64.decodeBase64())
        val r = buildFromPayload(payload,
            fallbackNick = data.optJSONObject("liveData")?.optString("nick").orEmpty(),
            fallbackAvatar = data.optJSONObject("liveData")?.optString("avatar18").orEmpty(),
            fallbackFans = data.optJSONObject("liveData")?.optLong("fansCount") ?: 0L,
            fallbackTitle = data.optJSONObject("liveData")?.optString("introduction").orEmpty(),
            fallbackCover = data.optJSONObject("liveData")?.optString("screenshot").orEmpty(),
            isLive = true, roomId = roomId)
        if (r == null) diag.add("API payload无线路,keys=${payload.keys().asSequence().take(8).toList()}")
        return r
    }

    // ================= WEB 端 =================
    private suspend fun resolveByWeb(roomId: String, diag: MutableList<String>): HuyaStreamResult? {
        val html = HttpClient.get(WEB_URL + roomId, ua = HttpClient.PC_UA)
        val b64 = extractBase64(html)
        if (b64 == null) {
            diag.add("WEB无base64,len=${html.length},head=${html.take(60)}")
            return null
        }
        val payload = JSONObject(b64.decodeBase64())
        val r = buildFromPayload(payload, "", "", 0L, "", "", true, roomId)
        if (r == null) diag.add("WEB payload无线路")
        return r
    }

    // ================= 移动端页面兜底 =================
    private suspend fun resolveByMobileWeb(roomId: String, diag: MutableList<String>): HuyaStreamResult? {
        val html = HttpClient.get(M_WEB_URL + roomId, ua = HttpClient.MOBILE_UA)
        val b64 = extractBase64(html)
        if (b64 == null) {
            diag.add("M端无base64,len=${html.length}")
            return null
        }
        val payload = JSONObject(b64.decodeBase64())
        return buildFromPayload(payload, "", "", 0L, "", "", true, roomId)
    }

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

    // ================= 核心解析（deepFind 兼容所有结构） =================
    private fun buildFromPayload(
        payload: JSONObject,
        fallbackNick: String, fallbackAvatar: String, fallbackFans: Long,
        fallbackTitle: String, fallbackCover: String,
        isLive: Boolean, roomId: String,
    ): HuyaStreamResult? {
        val streamNameGlobal = deepFind(payload, "sStreamName") as? String ?: ""
        val lines = mutableListOf<HuyaLine>()

        val gsi = deepFind(payload, "gameStreamInfoList") as? JSONArray
        if (gsi != null) {
            for (i in 0 until gsi.length()) {
                val o = gsi.optJSONObject(i) ?: continue
                val tag = o.optString("sCdnType").lowercase()
                val name = o.optString("sStreamName").ifEmpty { streamNameGlobal }
                val useHls = o.optInt("iIsHls") == 1 || o.optString("sHlsUrl").isNotEmpty()
                lines += if (useHls) HuyaLine(tag, o.optString("sHlsUrl"), name,
                    o.optString("sHlsUrlSuffix").ifEmpty { "m3u8" }, o.optString("sHlsAntiCode"), true)
                else HuyaLine(tag, o.optString("sFlvUrl"), name,
                    o.optString("sFlvUrlSuffix").ifEmpty { "flv" }, o.optString("sFlvAntiCode"), false)
            }
        } else {
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

        val qualities = mutableListOf(StreamQuality(0, "原画"))
        val vbr = deepFind(payload, "vBitRate") as? JSONArray
        if (vbr != null) {
            for (i in 0 until vbr.length()) {
                val o = vbr.optJSONObject(i) ?: continue
                val rate = o.optInt("iBitRate")
                if (rate == 0) continue
                qualities += StreamQuality(rate,
                    o.optString("sBitRateDisplayName").ifEmpty { QUALITY_NAMES[rate] ?: "档位$rate" })
            }
        }

        val gli = deepFind(payload, "gameLiveInfo") as? JSONObject
        val pi = deepFind(payload, "playerInfo") as? JSONObject
        val ayyuid = gli?.optLong("lYyid") ?: gli?.optLong("lPresenterUid") ?: 0L
        val topSid = gli?.optLong("lChannelId") ?: gli?.optLong("lTid") ?: 0L
        val subSid = gli?.optLong("lSubChannelId") ?: gli?.optLong("lSid") ?: 0L
        val nick = pi?.optString("sNick").orEmpty().ifEmpty { fallbackNick }
        val avatar = pi?.optString("sAvatar18").orEmpty().ifEmpty { fallbackAvatar }
        val fans = pi?.optLong("lFansCount") ?: fallbackFans
        val title = gli?.optString("sRoomName").orEmpty().ifEmpty { fallbackTitle }
        val cover = gli?.optString("sScreenshot").orEmpty().ifEmpty { fallbackCover }
        val startTime = gli?.optLong("lLiveStartTime") ?: 0L
        val heat = gli?.optLong("lTotalCount") ?: 0L

        return HuyaStreamResult(roomId, ayyuid, topSid, subSid, title, cover, heat, startTime,
            isLive, StreamerInfo(ayyuid, nick, avatar, fans, isLive), qualities,
            validLines.sortedBy { LINE_PRIORITY[it.tag] ?: 99 })
    }

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
            if (i > 0) p[kv.substring(0, i)] = kv.substring(i + 1).urlDecode()
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
        return p.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }
    }

    fun buildPlayUrl(line: HuyaLine, quality: StreamQuality, uid: Long): String {
        val code = generateWebAntiCode(line.antiCode, line.streamName, uid)
        val ratio = if (quality.bitRate == 0) "" else "&ratio=${quality.bitRate}"
        return "${line.flvUrl}/${line.streamName}.${line.suffix}?$code$ratio"
    }
}
