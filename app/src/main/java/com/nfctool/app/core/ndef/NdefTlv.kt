package com.nfctool.app.core.ndef

import com.nfctool.app.core.bytes.Bytes

/**
 * NDEF TLV 解析。
 *
 * 依据：NFC Forum "Type 2 Tag Operation" 与 "MIFARE Classic Tag Operation" 中定义的 TLV 结构。
 * 线格式：`标签(1 字节) | 长度 | 值`；长度字段在标签为 0xFF 时扩展为后续 2 字节大端，
 * 否则为 1 字节。标签 0x00 为 NULL（填充），0xFE 为终止符。
 */
object NdefTlv {

    const val TAG_NULL = 0x00
    const val TAG_LOCK_CONTROL = 0x01
    const val TAG_MEMORY_CONTROL = 0x02
    const val TAG_NDEF_MESSAGE = 0x03
    const val TAG_TERMINATOR = 0xFE
    const val TAG_EXTENDED_LENGTH = 0xFF

    /** 一个 TLV 项。 */
    class Tlv(val tag: Int, val value: ByteArray) {
        override fun toString(): String = "Tlv(tag=0x${tag.toString(16)}, ${value.size} 字节)"
    }

    /**
     * 解析字节流中的 TLV 序列。
     *
     * 遇到 NULL 标签会跳过（填充），遇到终止符停止；数据在中途截断时返回已解析的部分，
     * 不抛异常 —— 读取转储时经常只能拿到部分扇区，此时能解出多少算多少更有用。
     */
    fun parse(bytes: ByteArray): List<Tlv> {
        val result = ArrayList<Tlv>()
        var offset = 0
        while (offset < bytes.size) {
            val tag = bytes[offset].toInt() and 0xFF
            offset++
            if (tag == TAG_NULL) continue
            if (tag == TAG_TERMINATOR) break

            if (offset >= bytes.size) break
            var length = bytes[offset].toInt() and 0xFF
            offset++
            if (length == TAG_EXTENDED_LENGTH) {
                if (offset + 2 > bytes.size) break
                length = Bytes.readU16be(bytes, offset)
                offset += 2
            }
            if (offset + length > bytes.size) break
            result.add(Tlv(tag, bytes.copyOfRange(offset, offset + length)))
            offset += length
        }
        return result
    }

    /** 提取第一条 NDEF 消息 TLV 的值；不存在或结构不完整时返回 null。 */
    fun findNdefMessage(bytes: ByteArray): ByteArray? =
        parse(bytes).firstOrNull { it.tag == TAG_NDEF_MESSAGE }?.value

    /** 把一条 NDEF 消息包装成短格式 TLV（消息超过 254 字节时使用扩展长度）。 */
    fun encodeNdefMessage(message: ByteArray): ByteArray {
        require(message.isNotEmpty()) { "NDEF 消息不能为空" }
        val header = if (message.size <= 0xFE) {
            byteArrayOf(TAG_NDEF_MESSAGE.toByte(), message.size.toByte())
        } else {
            byteArrayOf(TAG_NDEF_MESSAGE.toByte(), TAG_EXTENDED_LENGTH.toByte()) +
                Bytes.u16be(message.size)
        }
        return Bytes.concat(header, message, byteArrayOf(TAG_TERMINATOR.toByte()))
    }
}
