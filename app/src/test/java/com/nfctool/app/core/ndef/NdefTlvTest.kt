package com.nfctool.app.core.ndef

import com.google.common.truth.Truth.assertThat
import com.nfctool.app.core.bytes.Hex
import org.junit.Assert.assertThrows
import org.junit.Test

class NdefTlvTest {

    private val ndefBytes = Hex.decode("D1010C5402656E48656C6C6F204E4643")

    @Test
    fun `解析短格式 NDEF TLV`() {
        val bytes = byteArrayOf(0x03, 0x10) + ndefBytes + byteArrayOf(0xFE.toByte())
        val tlvs = NdefTlv.parse(bytes)
        assertThat(tlvs).hasSize(1)
        assertThat(tlvs[0].tag).isEqualTo(NdefTlv.TAG_NDEF_MESSAGE)
        assertThat(Hex.encode(tlvs[0].value)).isEqualTo(Hex.encode(ndefBytes))
    }

    @Test
    fun `NULL 标签被跳过 终止符后停止解析`() {
        val bytes = byteArrayOf(
            0x00, 0x00,
            0x03, 0x10,
        ) + ndefBytes + byteArrayOf(
            0xFE.toByte(),
            0x03, 0x01, 0x7F,
        )
        val tlvs = NdefTlv.parse(bytes)
        assertThat(tlvs).hasSize(1)
        assertThat(tlvs[0].tag).isEqualTo(NdefTlv.TAG_NDEF_MESSAGE)
    }

    @Test
    fun `解析扩展长度格式`() {
        val payload = ByteArray(300) { 0x5A }
        val bytes = byteArrayOf(0x03, 0xFF.toByte(), 0x01, 0x2C) + payload + byteArrayOf(0xFE.toByte())
        val tlvs = NdefTlv.parse(bytes)
        assertThat(tlvs).hasSize(1)
        assertThat(Hex.encode(tlvs[0].value)).isEqualTo(Hex.encode(payload))
    }

    @Test
    fun `数据截断时返回已解析的部分而不抛异常`() {
        val bytes = byteArrayOf(0x03, 0x10, 0x01, 0x02, 0x03)
        assertThat(NdefTlv.parse(bytes)).isEmpty()
        assertThat(NdefTlv.findNdefMessage(bytes)).isNull()
    }

    @Test
    fun `没有 NDEF TLV 时返回 null`() {
        assertThat(NdefTlv.findNdefMessage(ByteArray(16))).isNull()
        assertThat(NdefTlv.findNdefMessage(byteArrayOf(0xFE.toByte()))).isNull()
        assertThat(NdefTlv.findNdefMessage(ByteArray(0))).isNull()
    }

    @Test
    fun `编码 TLV 并可被自身解析回来`() {
        val short = NdefTlv.encodeNdefMessage(ndefBytes)
        assertThat(Hex.encode(short)).isEqualTo("0310" + Hex.encode(ndefBytes) + "FE")
        assertThat(Hex.encode(NdefTlv.findNdefMessage(short)!!)).isEqualTo(Hex.encode(ndefBytes))

        val longPayload = ByteArray(300) { 0x5A }
        val long = NdefTlv.encodeNdefMessage(longPayload)
        assertThat(Hex.encode(long.copyOfRange(0, 4))).isEqualTo("03FF012C")
        assertThat(Hex.encode(NdefTlv.findNdefMessage(long)!!)).isEqualTo(Hex.encode(longPayload))
    }

    @Test
    fun `空消息不允许编码`() {
        assertThrows(IllegalArgumentException::class.java) { NdefTlv.encodeNdefMessage(ByteArray(0)) }
    }
}
