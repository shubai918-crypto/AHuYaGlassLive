package cn.ahuya.glasslive.data.huya.model

data class DanmakuMessage(
    val type: DanmakuType,
    val nickname: String,
    val content: String,
    val uid: Long,
    val color: Int,
    val badges: List<Badge>,
    val isVip: Boolean,
    val comboCount: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isGift: Boolean get() = type == DanmakuType.GIFT
}

enum class DanmakuType { NORMAL, GIFT, ENTER, NOTICE }

data class Badge(val name: String, val level: Int, val color: Int)
