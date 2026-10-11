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

    suspend fun resolve(roomId: String): HuyaStreamResult = withContext(Dispatchers.IO) {
        val diag = mutableListOf<String>()
        try {
            resolveByApi(roomId, diag)?.let { return@withContext it }
        } catch (e: HuyaOfflineException) { throw e }
        catch (e: Exception) { diag.add("API异常:${e.message}") }

        try {
            resolveByWeb(roomId, diag)?.let { return@withContext it }
        } catch (e: HuyaOfflineException) { throw e }
        catch (e: Exception) { diag.add("WEB异常:${e.message}") }

        try {
            resolveByMobileWeb(roomId, diag)?.let { return@withContext it }
        } catch (e: Exception) { diag.add("M端异常:${e.message}") }

        throw RuntimeException(diag.joinToString(" || ").ifEmpty { "全部返回空" })
    }

    // ================= API 端 =================
    private suspend fun resolveByApi(roomId: String, diag: MutableList<String>): HuyaStreamResult? {
        val body = HttpClient.get(API_URL + roomId, ua = HttpClient.MOBILE_UA)
        val data = runCatching { JSONObject(body) }.getOrNull()?.optJSONObject("data")
        val status = data?.optString("liveStatus", "").orEmpty()
        if (status.isNotEmpty() && !status.equals("ON", true)) {
            throw HuyaOfflineException(data?.optJSONObject("liveData")?.optString("nick").orEmpty())
        }
        val liveData = data?.optJSONObject("liveData")
        var r = buildFromText(body,
            fallbackNick = liveData?.optString("nick").orEmpty(),
            fallbackAvatar = liveData?.optString("avatar18").orEmpty(),
            fallbackFans = liveData?.optLong("fansCount") ?: 0L,
            fallbackTitle = liveData?.optString("introduction").orEmpty(),
            fallbackCover = liveData?.optString("screenshot").orEmpty(),
            isLive = true, roomId = roomId)
        if (r == null) {
            diag.add("API无线路,status=$status,keys=${data?.keys()?.asSequence()?.take(8)?.toList()}")
            return null
        }
        // ⭐ 用 chTopId / subChId 兜底弹幕订阅 ID
        if (r.topSid == 0L || r.subSid == 0L) {
            r = r.copy(
                topSid = if (r.topSid != 0L) r.topSid else data?.optLong("chTopId") ?: 0L,
                subSid = if (r.subSid != 0L) r.subSid else data?.optLong("subChId") ?: 0L,
            )
        }
        return r
    }

    // ================= WEB / M端 =================
    private suspend fun resolveByWeb(roomId: String, diag: MutableList<String>): HuyaStreamResult? {
        val html = HttpClient.get(WEB_URL + roomId, ua = HttpClient.PC_UA)
        val r = buildFromText(html, "", "", 0L, "", "", true, roomId)
        if (r == null) diag.add("WEB无线路,len=${html.length}")
        return r
    }

    private suspend fun resolveByMobileWeb(roomId: String, diag: MutableList<String>): HuyaStreamResult? {
        val html = HttpClient.get(M_WEB_URL + roomId, ua = HttpClient.MOBILE_UA)
        val r = buildFromText(html, "", "", 0L, "", "", true, roomId)
        if (r == null) diag.add("M端无线路,len=${html.length}")
        return r
    }

    // ================= 三路提取器 =================
    private fun buildFromText(
        text: String,
        fallbackNick: String, fallbackAvatar: String, fallbackFans: Long,
        fallbackTitle: String, fallbackCover: String,
        isLive: Boolean, roomId: String,
    ): HuyaStreamResult? {
        // 路0：JSON 任意深度挖 base64Stream
        runCatching { JSONObject(text) }.getOrNull()?.let { root ->
            (deepFind(root, "base64Stream") as? String)?.let { b64 ->
                if (b64.isNotEmpty()) {
                    runCatching { JSONObject(b64.decodeBase64()) }.getOrNull()?.let { p ->
                        buildFromPayload(p, fallbackNick, fallbackAvatar, fallbackFans,
                            fallbackTitle, fallbackCover, isLive, roomId)?.let { return it }
                    }
                }
            }
        }
        // 路1：正则抓 base64（老版网页）
        extractBase64(text)?.let { b64 ->
            runCatching { JSONObject(b64.decodeBase64()) }.getOrNull()?.let { p ->
                buildFromPayload(p, fallbackNick, fallbackAvatar, fallbackFans,
                    fallbackTitle, fallbackCover, isLive, roomId)?.let { return it }
            }
        }
        // 路2：括号配对抠明文 JSON（新版网页 HNF_GLOBAL_INIT）
        val gsi = extractJsonArray(text, "gameStreamInfoList")
        val vml = extractJsonArray(text, "vMultiLine")
        if (gsi == null && vml == null) return null
        val synthetic = JSONObject()
        gsi?.let { synthetic.put("gameStreamInfoList", it) }
        vml?.let { synthetic.put("vMultiLine", it) }
        extractJsonArray(text, "vBitRate")?.let { synthetic.put("vBitRate", it) }
        extractJsonObject(text, "gameLiveInfo")?.let { synthetic.put("gameLiveInfo", it) }
        extractJsonObject(text, "playerInfo")?.let { synthetic.put("playerInfo", it) }
        if (vml != null && gsi == null) {
            Regex("\"sStreamName\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
                ?.let { synthetic.put("sStreamName", it) }
        }
        return buildFromPayload(synthetic, fallbackNick, fallbackAvatar, fallbackFans,
            fallbackTitle, fallbackCover, isLive, roomId)
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

    /** 括号配对扫描：从明文里抠出 key 对应的 [ ... ] */
    private fun extractJsonArray(text: String, key: String): JSONArray? =
        extractBracket(text, key, '[', ']')?.let { runCatching { JSONArray(it) }.getOrNull() }

    /** 括号配对扫描：从明文里抠出 key 对应的 { ... } */
    private fun extractJsonObject(text: String, key: String): JSONObject? =
        extractBracket(text, key, '{', '}')?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun extractBracket(text: String, key: String, open: Char, close: Char): String? {
        val k = "\"$key\""
        var from = 0
        while (true) {
            val idx = text.indexOf(k, from)
            if (idx < 0) return null
            val start = text.indexOf(open, idx + k.length)
            if (start < 0) return null
            if (text.substring(idx + k.length, start).trim() != ":") {
                from = idx + k.length
                continue
            }
            var depth = 0
            var i = start
            while (i < text.length) {
                val c = text[i]
                when {
                    c == '"' -> {
                        i++
                        while (i < text.length && text[i] != '"') {
                            if (text[i] == '\\') i++
                            i++
                        }
                    }
                    c == open -> depth++
                    c == close -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
                i++
            }
            return null
        }
    }

    // ================= 核心解析 =================
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
                // ⭐ HLS 线路
                val hlsUrl = o.optString("sHlsUrl")
                if (hlsUrl.isNotEmpty()) {
                    lines += HuyaLine(tag, hlsUrl, name,
                        o.optString("sHlsUrlSuffix").ifEmpty { "m3u8" },
                        o.optString("sHlsAntiCode"), true)
                }
                // ⭐ FLV 线路（同 CDN 备选协议）
                val flvUrl = o.optString("sFlvUrl")
                if (flvUrl.isNotEmpty()) {
                    lines += HuyaLine(tag, flvUrl, name,
                        o.optString("sFlvUrlSuffix").ifEmpty { "flv" },
                        o.optString("sFlvAntiCode"), false)
                }
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
