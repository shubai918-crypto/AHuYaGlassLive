package cn.ahuya.glasslive.core.util

import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64

/** MD5，签名算法核心 */
fun String.md5(): String =
    MessageDigest.getInstance("MD5")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

fun String.urlDecode(): String = try {
    URLDecoder.decode(this, "UTF-8")
} catch (_: Exception) { this }

fun String.decodeBase64(): String =
    String(Base64.getMimeDecoder().decode(this), Charsets.UTF_8)

/** 12345678 -> 1234.6万 */
fun Long.fmtWan(): String = when {
    this >= 1_0000_0000 -> String.format("%.1f亿", this / 1_0000_0000.0)
    this >= 1_0000 -> String.format("%.1f万", this / 1_0000.0)
    else -> toString()
}
