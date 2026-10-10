package cn.ahuya.glasslive.data.huya.tars

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TarsWriter {
    private val out = ByteArrayOutputStream()

    private fun writeHead(type: Int, tag: Int) {
        if (tag < 15) out.write((tag shl 4) or type)
        else { out.write(0xF0 or type); out.write(tag) }
    }

    fun writeInt(tag: Int, v: Int) {
        if (v == 0) writeHead(12, tag)
        else if (v in -128..127) { writeHead(0, tag); out.write(v) }
        else if (v in -32768..32767) { writeHead(1, tag); out.write(short(v.toShort())) }
        else { writeHead(2, tag); out.write(int(v)) }
    }

    fun writeLong(tag: Int, v: Long) {
        if (v in Int.MIN_VALUE..Int.MAX_VALUE) writeInt(tag, v.toInt())
        else { writeHead(3, tag); out.write(long(v)) }
    }

    fun writeString(tag: Int, v: String) {
        val b = v.toByteArray(Charsets.UTF_8)
        if (b.size <= 255) { writeHead(6, tag); out.write(b.size); out.write(b) }
        else { writeHead(7, tag); out.write(int(b.size)); out.write(b) }
    }

    fun writeStructBegin(tag: Int) { writeHead(10, tag) }
    fun writeStructEnd() { writeHead(11, 0) }

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun short(v: Short) = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(v).array()
    private fun int(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(v).array()
    private fun long(v: Long) = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(v).array()
}
