package cn.ahuya.glasslive.data.huya.model

/** 一条 CDN 候选线路（url 已含签名与 ratio） */
data class CdnUrl(val cdn: String, val url: String) {
    val displayName: String get() = when (cdn.lowercase()) {
        "al" -> "阿里"
        "hs" -> "火山"
        "tx" -> "腾讯"
        else -> cdn.uppercase()
    }
}

/** 清晰度：每个档位自带按 CDN 优先级排序的候选 url 列表（对齐 Dart） */
data class StreamQuality(
    val bitRate: Int,
    val name: String,
    val candidates: List<CdnUrl>,
)

data class StreamerInfo(
    val uid: Long,
    val nickname: String,
    val avatar: String,
    val fansCount: Long,
    val isLive: Boolean,
)

data class HuyaStreamResult(
    val roomId: String,
    val ayyuid: Long,
    val topSid: Long,
    val subSid: Long,
    val title: String,
    val cover: String,
    val heat: Long,
    val startTime: Long,
    val isLive: Boolean,
    val streamer: StreamerInfo,
    val qualities: List<StreamQuality>,
)
