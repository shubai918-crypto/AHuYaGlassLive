package cn.ahuya.glasslive.data.huya.model

/** 清晰度（bitRate=0 即原画） */
data class StreamQuality(val bitRate: Int, val name: String)

/** 播放线路 */
data class HuyaLine(
    val tag: String,          // al / tx / hs
    val flvUrl: String,
    val streamName: String,
    val suffix: String,       // flv / m3u8
    val antiCode: String,     // 原始防盗链参数
    val isHls: Boolean,
) {
    val displayName: String get() = when (tag) {
        "al" -> "阿里"
        "tx" -> "腾讯"
        "hs" -> "火山"
        else -> tag.uppercase()
    }
}

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
    val topSid: Long,   // 弹幕 WebSocket 订阅要用
    val subSid: Long,
    val title: String,
    val cover: String,
    val heat: Long,
    val startTime: Long,
    val isLive: Boolean,
    val streamer: StreamerInfo,
    val qualities: List<StreamQuality>,
    val lines: List<HuyaLine>,
)
