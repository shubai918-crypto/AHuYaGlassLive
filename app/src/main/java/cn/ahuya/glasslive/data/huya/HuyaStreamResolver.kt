package cn.ahuya.glasslive.data.huya

import cn.ahuya.glasslive.core.net.HttpClient
import cn.ahuya.glasslive.core.util.md5
import cn.ahuya.glasslive.data.huya.model.CdnUrl
import cn.ahuya.glasslive.data.huya.model.HuyaStreamResult
import cn.ahuya.glasslive.data.huya.model.StreamQuality
import cn.ahuya.glasslive.data.huya.model.StreamerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.random.Random

class HuyaOfflineException(val nick: String) : Exception("主播未开播")

object HuyaStreamResolver {

    private const val IOS_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
    private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    private const val WEBH5_COOKIE = "huya_ua=webh5&0.1.0&websocket"
    private const val SDK_VERSION = "2403051612"
    private const val API_URL = "https://mp.huya.com/cache.php?m=Live&do=profileRoom&roomid="

    private val random = Random(System.currentTimeMillis())

    // ================= 工具 =================
    private fun i(v: Any?): Int = when (v) {
        is Int -> v
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: 0
        else -> 0
    }
    private fun l(v: Any?): Long = when (v) {
        is Long -> v
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: 0L
        else -> 0L
    }
    private fun s(v: Any?): String = v?.toString() ?: ""
    private fun firstNonZeroL(vals: List<Any?>): Long {
        for (v in vals) { val n = l(v); if (n > 0) return n }
        return 0L
    }

    /** Dart: enforceHttp —— 虎牙 CDN 必须走 http */
    fun enforceHttp(url: String): String =
        if (url.startsWith("https://")) url.replaceFirst("https://", "http://") else url

    private fun trimSlash(u: String): String {
        var s = u.trim()
        while (s.endsWith("/")) s = s.substring(0, s.length - 1)
        return s
    }

    private fun parseQuery(qs: String): Map<String, String> {
        var trimmed = qs.trim()
        while (trimmed.startsWith("?") || trimmed.startsWith("&")) trimmed = trimmed.substring(1)
        if (trimmed.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (kv in trimmed.split("&")) {
            val idx = kv.indexOf('=')
            if (idx <= 0) continue
            map[kv.substring(0, idx)] = kv.substring(idx + 1)
        }
        return map
    }

    /** 对齐 Dart Uri.decodeComponent：只解 %XX，不把 + 变空格 */
    private fun decodeComponent(src: String): String {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            if (c == '%' && i + 2 < src.length) {
                val b = src.substring(i + 1, i + 3).toIntOrNull(16)
                if (b != null) { out.write(b); i += 3; continue }
            }
            if (c.code < 128) out.write(c.code)
            else out.write(c.toString().toByteArray(Charsets.UTF_8))
            i++
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private fun b64Decode(str: String): ByteArray =
        try { Base64.getDecoder().decode(str) }
        catch (_: Exception) { Base64.getMimeDecoder().decode(str) }

    // ================= dtv 同款签名 =================
    fun generateWebAntiCode(streamName: String, antiCode: String): String {
        return try {
            val sanitized = antiCode.replace("&amp;", "&")
            val params = parseQuery(sanitized)
            val fmValue = params["fm"] ?: return ""
            val ctype = params["ctype"] ?: return ""
            val fs = params["fs"] ?: return ""

            val fmPlain = String(b64Decode(decodeComponent(fmValue)), Charsets.UTF_8)
            val wsPrefix = fmPlain.split('_').first()
            if (wsPrefix.isEmpty()) return ""

            val paramsT = 100
            val t13 = System.currentTimeMillis()
            val sdkSid = t13
            val uid = 1400000000000L + (random.nextDouble() * 10000000000L).toLong()
            val seqId = uid + sdkSid
            val wsTime = ((t13 + 110624) / 1000).toString(16)
            val uuidSeed = (t13 % 10000000000L) * 1000 + (random.nextDouble() * 1000).toLong()
            val initUuid = uuidSeed % 4294967295L

            
val wsSecretHash = "$seqId|$ctype|$paramsT".md5()
val wsSecretPlain = "${wsPrefix}_${uid}_${streamName}_${wsSecretHash}_$wsTime"
val wsSecretMd5 = wsSecretPlain.md5()

            listOf(
                "wsSecret=$wsSecretMd5",
                "wsTime=$wsTime",
                "seqid=$seqId",
                "ctype=$ctype",
                "ver=1",
                "fs=$fs",
                "uuid=$initUuid",
                "u=$uid",
                "t=$paramsT",
                "sv=$SDK_VERSION",
                "sdk_sid=$sdkSid",
                "codec=264",
            ).joinToString("&")
        } catch (_: Exception) { "" }
    }

    // ================= dtv 同款 tx 修正 =================
    private fun adjustTxStreamUrl(url: String, cdn: String): String {
        if (!cdn.equals("tx", true)) return enforceHttp(url)
        var s = url.replace("&ctype=tars_mp", "&ctype=huya_webh5")
        s = s.replace("&fs=bhct", "&fs=bgct")
        return enforceHttp(s)
    }

    private fun cdnRank(cdn: String): Int = when (cdn.lowercase()) {
        "al" -> 0; "hs" -> 1; "tx" -> 2; else -> 3
    }

    // ================= 网页抓取（带 Cookie/Referer） =================
    private suspend fun fetchHtml(roomId: String, mobile: Boolean): String {
        val headers = if (mobile) mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer" to "https://m.huya.com/",
            "Accept-Language" to "zh-CN,zh;q=0.9,en-US;q=0.6,en;q=0.4",
            "Cookie" to WEBH5_COOKIE,
        ) else mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Referer" to "https://www.huya.com/",
            "Accept-Language" to "zh-CN,zh;q=0.9,en-US;q=0.6,en;q=0.4",
            "Cookie" to WEBH5_COOKIE,
        )
        return HttpClient.get(
            "https://www.huya.com/$roomId",
            ua = if (mobile) IOS_UA else DESKTOP_UA,
            headers = headers,
        )
    }

    /** dtv 同款：只解析网页 FLV 候选 */
    private fun parseFlvCandidates(html: String): List<CdnUrl> {
        if (html.isEmpty()) return emptyList()
        return try {
            val re = Regex("stream:\\s*(\\{\"data\".*?),\"iWebDefaultBitRate\"", setOf(RegexOption.DOT_MATCHES_ALL))
            val m = re.find(html) ?: return emptyList()
            val block = JSONObject(m.groupValues[1] + "}")
            val dataList = block.optJSONArray("data") ?: return emptyList()
            val first = dataList.optJSONObject(0) ?: return emptyList()
            val list = first.optJSONArray("gameStreamInfoList") ?: return emptyList()
            val result = mutableListOf<CdnUrl>()
            for (idx in 0 until list.length()) {
                val obj = list.optJSONObject(idx) ?: continue
                val cdn = s(obj.opt("sCdnType"))
                val flvUrl = trimSlash(s(obj.opt("sFlvUrl")))
                val streamName = s(obj.opt("sStreamName"))
                val suffix = s(obj.opt("sFlvUrlSuffix"))
                val anti = s(obj.opt("sFlvAntiCode"))
                if (cdn.isEmpty() || flvUrl.isEmpty() || streamName.isEmpty() || suffix.isEmpty() || anti.isEmpty()) continue
                val p = generateWebAntiCode(streamName, anti)
                if (p.isEmpty()) continue
                result += CdnUrl(cdn, adjustTxStreamUrl("$flvUrl/$streamName.$suffix?$p", cdn))
            }
            result
        } catch (_: Exception) { emptyList() }
    }

    // ================= 入口 =================
    suspend fun resolve(roomId: String): HuyaStreamResult = withContext(Dispatchers.IO) {
        val body = HttpClient.get(
            API_URL + roomId + "&showSecret=1",
            ua = IOS_UA,
            headers = mapOf("Referer" to "https://www.huya.com/"),
        )
        val root = JSONObject(body)
        if (root.optInt("status") != 200) throw RuntimeException("API status=${root.optInt("status")}")
        val data = root.optJSONObject("data") ?: throw RuntimeException("API 无 data")
        val profile = data.optJSONObject("profileInfo") ?: JSONObject()
        val liveData = data.optJSONObject("liveData") ?: JSONObject()
        val stream = data.optJSONObject("stream") ?: JSONObject()
        // ⭐ 虎牙官方拼写就是 baseSteamInfoList
        val baseList = stream.optJSONArray("baseSteamInfoList")
            ?: stream.optJSONArray("gameStreamInfoList")
            ?: JSONArray()

        var rates = JSONArray()
        val bitRateInfo = liveData.opt("bitRateInfo")
        if (bitRateInfo is String && bitRateInfo.isNotEmpty()) {
            rates = runCatching { JSONArray(bitRateInfo) }.getOrDefault(JSONArray())
        }
        if (rates.length() == 0) rates = stream.optJSONArray("vMultiStreamInfo") ?: JSONArray()

        val liveStatus = s(data.opt("liveStatus")).uppercase()
        val realLiveStatus = s(data.opt("realLiveStatus")).uppercase()
        val isLive = liveStatus == "ON" || realLiveStatus == "ON"

        val nickname = s(liveData.opt("nick")).ifEmpty { s(profile.opt("nick")) }
        val avatar = s(liveData.opt("avatar180")).ifEmpty { s(profile.opt("avatar180")) }
        val fansCount = firstNonZeroL(listOf(profile.opt("activityCount"), liveData.opt("activityCount")))
        val heat = firstNonZeroL(listOf(liveData.opt("totalCount"), liveData.opt("attendeeCount")))
        val title = s(liveData.opt("introduction"))
        val startTime = l(liveData.opt("startTime"))
        val cover = s(liveData.opt("screenshot"))

        val uidVal = firstNonZeroL(listOf(profile.opt("uid"), liveData.opt("uid")))
        val yyid = firstNonZeroL(listOf(profile.opt("yyid"), liveData.opt("yyid")))
        val topSid = firstNonZeroL(listOf(data.opt("chTopId"), liveData.opt("liveChannel"), uidVal, yyid))
        val subSid = firstNonZeroL(listOf(data.opt("subChId"), liveData.opt("shortChannel"), topSid))
        val uid = if (uidVal != 0L) uidVal else topSid

        if (!isLive) throw HuyaOfflineException(nickname)

        // ⭐ 线路优先级：网页 FLV(dtv同源) → API FLV → API HLS
        var webFlv = parseFlvCandidates(runCatching { fetchHtml(roomId, false) }.getOrDefault(""))
        if (webFlv.isEmpty()) {
            webFlv = parseFlvCandidates(runCatching { fetchHtml(roomId, true) }.getOrDefault(""))
        }

        val apiFlv = mutableListOf<CdnUrl>()
        val apiHls = mutableListOf<CdnUrl>()
        for (idx in 0 until baseList.length()) {
            val bm = baseList.optJSONObject(idx) ?: continue
            val streamName = s(bm.opt("sStreamName"))
            val cdn = s(bm.opt("sCdnType"))
            if (streamName.isEmpty()) continue

            val flvUrl = trimSlash(s(bm.opt("sFlvUrl")))
            val flvSuffix = s(bm.opt("sFlvUrlSuffix"))
            val flvAnti = s(bm.opt("sFlvAntiCode"))
            if (flvUrl.isNotEmpty() && flvAnti.isNotEmpty()) {
                val p = generateWebAntiCode(streamName, flvAnti)
                if (p.isNotEmpty()) apiFlv += CdnUrl(cdn, adjustTxStreamUrl("$flvUrl/$streamName.$flvSuffix?$p", cdn))
            }

            val hlsUrl = trimSlash(s(bm.opt("sHlsUrl")))
            val hlsSuffix = s(bm.opt("sHlsUrlSuffix")).ifEmpty { "m3u8" }
            val hlsAnti = s(bm.opt("sHlsAntiCode"))
            if (hlsUrl.isNotEmpty() && hlsAnti.isNotEmpty()) {
                val p = generateWebAntiCode(streamName, hlsAnti)
                if (p.isNotEmpty()) apiHls += CdnUrl(cdn, adjustTxStreamUrl("$hlsUrl/$streamName.$hlsSuffix?$p", cdn))
            }
        }

        val flvList = (if (webFlv.isNotEmpty()) webFlv else apiFlv).sortedBy { cdnRank(it.cdn) }
        val ordered = flvList + apiHls.sortedBy { cdnRank(it.cdn) }
        if (ordered.isEmpty()) throw RuntimeException("无可用线路(web/api 均未解析到流)")

        val qualities = mutableListOf<StreamQuality>()
        val rateList = if (rates.length() > 0) rates
        else JSONArray().put(JSONObject().put("sDisplayName", "原画").put("iBitRate", 0))
        for (idx in 0 until rateList.length()) {
            val rr = rateList.optJSONObject(idx) ?: continue
            val bitrate = i(rr.opt("iBitRate"))
            val ratio = if (bitrate > 0) "&ratio=$bitrate" else ""
            val candidates = ordered.map { CdnUrl(it.cdn, it.url + ratio) }
            if (candidates.isEmpty()) continue
            val name = s(rr.opt("sDisplayName")).ifEmpty {
                if (bitrate == 0) "原画" else "蓝光${bitrate / 1000}M"
            }
            qualities += StreamQuality(bitrate, name, candidates)
        }
        if (qualities.isEmpty()) throw RuntimeException("清晰度列表为空")

        HuyaStreamResult(
            roomId = roomId, ayyuid = uid, topSid = topSid, subSid = subSid,
            title = title, cover = cover, heat = heat, startTime = startTime,
            isLive = true,
            streamer = StreamerInfo(uid, nickname, avatar, fansCount, true),
            qualities = qualities,
        )
    }
}
