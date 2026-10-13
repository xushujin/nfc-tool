package com.nfctool.app.core.bytes

/**
 * 字节序与 ASCII 转换的最小工具集。
 *
 * 本工程所有多字节整数一律使用大端序（网络字节序），与 NFC Forum 规范一致。
 */
object Bytes {

    /** 按字节直接映射为字符（Latin-1 语义），用于展示 NDEF 的类型字段等 ASCII 内容。 */
    fun ascii(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            sb.append((b.toInt() and 0xFF).toChar())
        }
        return sb.toString()
    }

    /** 严格 ASCII 编码：出现非 ASCII 字符直接失败，避免生成非法类型字段。 */
    fun toAscii(text: String): ByteArray {
        val out = ByteArray(text.length)
        for (index in text.indices) {
            val code = text[index].code
            require(code <= 0x7F) { "期望 ASCII 字符，实际遇到 U+${code.toString(16).uppercase()}" }
            out[index] = code.toByte()
        }
        return out
    }

    fun concat(vararg parts: ByteArray): ByteArray {
        var total = 0
        for (part in parts) total += part.size
        val out = ByteArray(total)
        var offset = 0
        for (part in parts) {
            part.copyInto(out, offset)
            offset += part.size
        }
        return out
    }

    fun u16be(value: Int): ByteArray {
        require(value in 0..0xFFFF) { "u16 越界：$value" }
        return byteArrayOf((value ushr 8).toByte(), value.toByte())
    }

    fun u32be(value: Int): ByteArray {
        require(value >= 0) { "u32 越界：$value" }
        return byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte(),
        )
    }

    fun readU16be(bytes: ByteArray, offset: Int): Int {
        require(offset + 2 <= bytes.size) { "读取 u16 越界：offset=$offset size=${bytes.size}" }
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }

    fun readU32be(bytes: ByteArray, offset: Int): Int {
        require(offset + 4 <= bytes.size) { "读取 u32 越界：offset=$offset size=${bytes.size}" }
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }
}
