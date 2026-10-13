package com.nfctool.app.core.ndef

import com.nfctool.app.core.bytes.Bytes
import java.io.ByteArrayOutputStream

/**
 * NDEF 二进制编解码器。
 * 依据：NFC Forum NDEF 1.0 规范 3.2 节（记录头、SR/IL 标志、分块记录）。
 *
 * 刻意不依赖 `android.nfc.NdefMessage`：核心能力自行实现，
 * 且纯 Kotlin 实现可在 JVM 单元测试中用真实二进制样本逐字节验证。
 */
object NdefCodec {

    private const val FLAG_MB = 0x80
    private const val FLAG_ME = 0x40
    private const val FLAG_CF = 0x20
    private const val FLAG_SR = 0x10
    private const val FLAG_IL = 0x08
    private const val TNF_MASK = 0x07

    /** 单条记录负载超过该长度时使用 4 字节长度字段（SR 置位阈值）。 */
    const val SHORT_RECORD_MAX_PAYLOAD = 0xFF

    // ------------------------------------------------------------------ 编码

    fun encode(message: NdefMessage): ByteArray {
        val records = message.records
        val out = ByteArrayOutputStream()
        for (index in records.indices) {
            writeRecord(out, records[index], isFirst = index == 0, isLast = index == records.lastIndex)
        }
        return out.toByteArray()
    }

    private fun writeRecord(out: ByteArrayOutputStream, record: NdefRecord, isFirst: Boolean, isLast: Boolean) {
        validateForEncode(record, isFirst)

        val isShort = record.payload.size <= SHORT_RECORD_MAX_PAYLOAD
        val hasId = record.id.isNotEmpty()

        var header = record.tnf
        if (isFirst) header = header or FLAG_MB
        if (isLast) header = header or FLAG_ME
        if (isShort) header = header or FLAG_SR
        if (hasId) header = header or FLAG_IL

        out.write(header)
        out.write(record.type.size)
        if (isShort) {
            out.write(record.payload.size)
        } else {
            out.write(Bytes.u32be(record.payload.size), 0, 4)
        }
        if (hasId) out.write(record.id.size)
        out.write(record.type, 0, record.type.size)
        if (hasId) out.write(record.id, 0, record.id.size)
        out.write(record.payload, 0, record.payload.size)
    }

    private fun validateForEncode(record: NdefRecord, isFirst: Boolean) {
        if (record.type.size > 0xFF) {
            throw NdefFormatException("记录类型长度 ${record.type.size} 超过 255 字节上限")
        }
        if (record.id.size > 0xFF) {
            throw NdefFormatException("记录 ID 长度 ${record.id.size} 超过 255 字节上限")
        }
        when (record.tnf) {
            NdefTnf.EMPTY -> if (record.type.isNotEmpty() || record.id.isNotEmpty() || record.payload.isNotEmpty()) {
                throw NdefFormatException("Empty 记录不允许携带类型、ID 或负载")
            }

            NdefTnf.UNCHANGED -> throw NdefFormatException(
                "Unchanged(0x06) 只允许出现在分块记录的后续分块中，不能作为独立记录编码",
            )

            NdefTnf.RESERVED -> throw NdefFormatException("Reserved(0x07) 为保留值，不可用于编码")

            NdefTnf.WELL_KNOWN, NdefTnf.MIME, NdefTnf.ABSOLUTE_URI, NdefTnf.EXTERNAL ->
                if (record.type.isEmpty()) {
                    throw NdefFormatException("TNF=${NdefTnf.name(record.tnf)} 的记录必须带类型字段")
                }

            NdefTnf.UNKNOWN -> Unit

            else -> throw NdefFormatException("未知的 TNF 取值：${record.tnf}")
        }
        if (isFirst && record.tnf == NdefTnf.UNCHANGED) {
            throw NdefFormatException("首条记录不允许为 Unchanged")
        }
    }

    // ------------------------------------------------------------------ 解码

    fun decode(bytes: ByteArray): NdefMessage {
        if (bytes.isEmpty()) throw NdefFormatException("NDEF 数据为空")

        val records = ArrayList<NdefRecord>()
        var offset = 0
        var beginSeen = false
        var endSeen = false
        var chunk: ChunkAccumulator? = null

        while (offset < bytes.size) {
            val header = bytes[offset].toInt() and 0xFF
            offset++

            val isBegin = header and FLAG_MB != 0
            val isEnd = header and FLAG_ME != 0
            val isChunk = header and FLAG_CF != 0
            val isShort = header and FLAG_SR != 0
            val hasId = header and FLAG_IL != 0
            val tnf = header and TNF_MASK

            if (isBegin) {
                if (beginSeen) throw NdefFormatException("第 ${records.size + 1} 条记录重复置位 MB 标志")
                beginSeen = true
            }
            if (!beginSeen) throw NdefFormatException("NDEF 消息缺少起始记录（首字节未置位 MB）")

            ensureAvailable(bytes, offset, 1, "类型长度字段")
            val typeLength = bytes[offset].toInt() and 0xFF
            offset++

            val payloadLength: Int
            if (isShort) {
                ensureAvailable(bytes, offset, 1, "负载长度字段")
                payloadLength = bytes[offset].toInt() and 0xFF
                offset++
            } else {
                ensureAvailable(bytes, offset, 4, "负载长度字段")
                payloadLength = Bytes.readU32be(bytes, offset)
                if (payloadLength < 0) throw NdefFormatException("负载长度超出 32 位可表示范围")
                offset += 4
            }

            var idLength = 0
            if (hasId) {
                ensureAvailable(bytes, offset, 1, "ID 长度字段")
                idLength = bytes[offset].toInt() and 0xFF
                offset++
            }

            ensureAvailable(bytes, offset, typeLength + idLength + payloadLength, "记录内容")
            val type = bytes.copyOfRange(offset, offset + typeLength)
            offset += typeLength
            val id = bytes.copyOfRange(offset, offset + idLength)
            offset += idLength
            val payload = bytes.copyOfRange(offset, offset + payloadLength)
            offset += payloadLength

            val openChunk = chunk
            if (openChunk == null) {
                if (tnf == NdefTnf.UNCHANGED) {
                    throw NdefFormatException("Unchanged(0x06) 不能作为独立记录出现")
                }
                if (isChunk) {
                    if (isEnd) throw NdefFormatException("分块记录的中间分块不允许置位 ME 标志")
                    chunk = ChunkAccumulator(tnf, type, id).apply { append(payload) }
                } else {
                    records.add(NdefRecord(tnf, type, id, payload))
                    if (isEnd) endSeen = true
                }
            } else {
                if (tnf != NdefTnf.UNCHANGED) {
                    throw NdefFormatException("分块记录的后续分块 TNF 必须为 Unchanged(0x06)，实际为 ${NdefTnf.name(tnf)}")
                }
                if (typeLength != 0) throw NdefFormatException("分块记录的后续分块不允许携带类型字段")
                if (hasId) throw NdefFormatException("分块记录的后续分块不允许携带 ID 字段")
                openChunk.append(payload)
                if (isChunk) {
                    if (isEnd) throw NdefFormatException("分块记录的中间分块不允许置位 ME 标志")
                } else {
                    records.add(openChunk.toRecord())
                    chunk = null
                    if (isEnd) endSeen = true
                }
            }

            if (endSeen) break
        }

        if (chunk != null) throw NdefFormatException("分块记录未闭合：数据结束前缺少 CF=0 的末块")
        if (!endSeen) throw NdefFormatException("NDEF 消息缺少结束记录（最后一条未置位 ME 标志）")
        if (offset != bytes.size) {
            throw NdefFormatException("NDEF 消息结束后仍有 ${bytes.size - offset} 字节残留数据")
        }
        return NdefMessage(records)
    }

    private fun ensureAvailable(bytes: ByteArray, offset: Int, needed: Int, what: String) {
        if (offset + needed > bytes.size) {
            throw NdefFormatException(
                "数据在读取「$what」时提前结束：需要 $needed 字节，仅剩 ${bytes.size - offset} 字节",
            )
        }
    }

    private class ChunkAccumulator(
        private val tnf: Int,
        private val type: ByteArray,
        private val id: ByteArray,
    ) {
        private val buffer = ByteArrayOutputStream()

        fun append(payload: ByteArray) {
            buffer.write(payload, 0, payload.size)
        }

        fun toRecord(): NdefRecord = NdefRecord(tnf, type, id, buffer.toByteArray())
    }
}
