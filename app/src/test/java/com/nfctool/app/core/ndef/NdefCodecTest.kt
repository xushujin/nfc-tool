package com.nfctool.app.core.ndef

import com.google.common.truth.Truth.assertThat
import com.nfctool.app.core.bytes.Hex
import org.junit.Assert.assertThrows
import org.junit.Test

class NdefCodecTest {

    @Test
    fun `规范示例文本记录编码为已知字节串`() {
        // 教科书示例：MB|ME|SR|TNF=1 => 0xD1；类型长度 1；负载长度 8；
        // 类型 'T'(0x54)；负载 = 状态字节 0x02 + "en" + "hello"
        val message = NdefMessage.of(NdefRecords.text("hello", "en"))
        assertThat(Hex.encode(NdefCodec.encode(message)))
            .isEqualTo("D101085402656E68656C6C6F")
    }

    @Test
    fun `规范示例 URI 记录编码为已知字节串`() {
        // 前缀缩写取最长匹配：https://www. 命中 0x02（而非 https:// 的 0x04），
        // 剩余部分为 "android.com/"（12 字节），负载共 13 字节。
        val message = NdefMessage.of(NdefRecords.uri("https://www.android.com/"))
        assertThat(Hex.encode(NdefCodec.encode(message)))
            .isEqualTo("D1010D5502616E64726F69642E636F6D2F")
    }

    @Test
    fun `单条记录同时置位 MB 与 ME`() {
        val encoded = NdefCodec.encode(NdefMessage.of(NdefRecords.text("x", "en")))
        assertThat(encoded[0].toInt() and 0x80).isEqualTo(0x80)
        assertThat(encoded[0].toInt() and 0x40).isEqualTo(0x40)
    }

    @Test
    fun `多记录消息只在首尾置位 MB 与 ME`() {
        val message = NdefMessage(
            listOf(
                NdefRecords.text("first", "en"),
                NdefRecords.uri("https://example.com/"),
            ),
        )
        val encoded = NdefCodec.encode(message)
        assertThat(encoded[0].toInt() and 0x80).isEqualTo(0x80)
        assertThat(encoded[0].toInt() and 0x40).isEqualTo(0)

        val decoded = NdefCodec.decode(encoded)
        assertThat(decoded.records).hasSize(2)
        assertThat(decoded.records[1].typeAscii).isEqualTo("U")
    }

    @Test
    fun `负载超过 255 字节时不置位 SR 并使用四字节长度字段`() {
        val payload = ByteArray(300) { 0x41 }
        val record = NdefRecords.mime("application/octet-stream", payload)
        val encoded = NdefCodec.encode(NdefMessage.of(record))

        assertThat(encoded[0].toInt() and 0x10).isEqualTo(0)
        val declared = Hex.encode(encoded.copyOfRange(2, 6))
        assertThat(declared).isEqualTo("0000012C")

        val decoded = NdefCodec.decode(encoded)
        assertThat(Hex.encode(decoded.records[0].payload)).isEqualTo(Hex.encode(payload))
    }

    @Test
    fun `带 ID 的记录置位 IL 并可往返`() {
        val record = NdefRecord(
            tnf = NdefTnf.WELL_KNOWN,
            type = byteArrayOf(0x54),
            id = Hex.decode("6964"),
            payload = byteArrayOf(0x02, 0x65, 0x6E, 0x78),
        )
        val encoded = NdefCodec.encode(NdefMessage.of(record))
        assertThat(encoded[0].toInt() and 0x08).isEqualTo(0x08)

        val decoded = NdefCodec.decode(encoded)
        assertThat(Hex.encode(decoded.records[0].id)).isEqualTo("6964")
    }

    @Test
    fun `分块记录解码后合并为一条记录`() {
        val textPayload = byteArrayOf(0x02) + "en".toByteArray() + "hello world".toByteArray()
        val first = textPayload.copyOfRange(0, 6)
        val second = textPayload.copyOfRange(6, textPayload.size)
        val encoded = byteArrayOf(0xB1.toByte(), 0x01, first.size.toByte(), 0x54) + first +
            byteArrayOf(0x56, 0x00, second.size.toByte()) + second

        val message = NdefCodec.decode(encoded)
        assertThat(message.records).hasSize(1)
        assertThat(message.records[0].typeAscii).isEqualTo("T")
        assertThat(NdefRecords.parseText(message.records[0])!!.text).isEqualTo("hello world")
    }

    @Test
    fun `分块记录缺少末块时报错`() {
        val first = byteArrayOf(0x02) + "en".toByteArray() + "hello".toByteArray()
        val encoded = byteArrayOf(0xB1.toByte(), 0x01, first.size.toByte(), 0x54) + first
        val error = assertThrows(NdefFormatException::class.java) { NdefCodec.decode(encoded) }
        assertThat(error.message).contains("分块")
    }

    @Test
    fun `负载被截断时报错而不是返回半截数据`() {
        val encoded = NdefCodec.encode(NdefMessage.of(NdefRecords.text("hello", "en")))
        val truncated = encoded.copyOfRange(0, encoded.size - 3)
        val error = assertThrows(NdefFormatException::class.java) { NdefCodec.decode(truncated) }
        assertThat(error.message).contains("提前结束")
    }

    @Test
    fun `缺少 ME 标志时报错`() {
        val encoded = NdefCodec.encode(NdefMessage.of(NdefRecords.text("hi", "en")))
        val withoutMe = encoded.copyOf()
        withoutMe[0] = (withoutMe[0].toInt() and 0x40.inv()).toByte()
        assertThrows(NdefFormatException::class.java) { NdefCodec.decode(withoutMe) }
    }

    @Test
    fun `ME 之后残留数据时报错`() {
        val encoded = NdefCodec.encode(NdefMessage.of(NdefRecords.text("hi", "en")))
        val withTrailing = encoded + byteArrayOf(0x00)
        val error = assertThrows(NdefFormatException::class.java) { NdefCodec.decode(withTrailing) }
        assertThat(error.message).contains("残留")
    }

    @Test
    fun `空数据报错`() {
        assertThrows(NdefFormatException::class.java) { NdefCodec.decode(ByteArray(0)) }
    }

    @Test
    fun `不允许编码 Unchanged 与 Reserved 记录`() {
        val unchanged = NdefRecord(NdefTnf.UNCHANGED, ByteArray(0), ByteArray(0), ByteArray(0))
        assertThrows(NdefFormatException::class.java) {
            NdefCodec.encode(NdefMessage.of(unchanged))
        }
        val reserved = NdefRecord(NdefTnf.RESERVED, ByteArray(0), ByteArray(0), ByteArray(0))
        assertThrows(NdefFormatException::class.java) {
            NdefCodec.encode(NdefMessage.of(reserved))
        }
    }

    @Test
    fun `Empty 记录不允许携带内容`() {
        val invalid = NdefRecord(NdefTnf.EMPTY, byteArrayOf(0x01), ByteArray(0), ByteArray(0))
        assertThrows(NdefFormatException::class.java) {
            NdefCodec.encode(NdefMessage.of(invalid))
        }
    }

    @Test
    fun `消息至少需要一条记录`() {
        assertThrows(IllegalArgumentException::class.java) { NdefMessage(emptyList()) }
    }
}
