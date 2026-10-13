package com.nfctool.app.core.wsc

import com.google.common.truth.Truth.assertThat
import com.nfctool.app.core.bytes.Bytes
import com.nfctool.app.core.bytes.Hex
import com.nfctool.app.core.ndef.NdefCodec
import com.nfctool.app.core.ndef.NdefFormatException
import com.nfctool.app.core.ndef.NdefMessage
import com.nfctool.app.core.ndef.NdefRecords
import org.junit.Assert.assertThrows
import org.junit.Test

class WscCodecTest {

    private val wpa2 = WifiCredential(
        ssid = "Lab-Network",
        networkKey = "correct horse battery",
        authType = WscAuthType.WPA2_PERSONAL,
        encryptionType = WscEncryptionType.AES,
    )

    @Test
    fun `属性 ID 与两套独立实现一致`() {
        // 与 hostap wps_defs.h 及 ndeflib wifi.py 逐条比对过的取值，这里固化为回归断言
        assertThat(WscAttributeId.AUTH_TYPE).isEqualTo(0x1003)
        assertThat(WscAttributeId.CREDENTIAL).isEqualTo(0x100E)
        assertThat(WscAttributeId.ENCRYPTION_TYPE).isEqualTo(0x100F)
        assertThat(WscAttributeId.MAC_ADDRESS).isEqualTo(0x1020)
        assertThat(WscAttributeId.NETWORK_INDEX).isEqualTo(0x1026)
        assertThat(WscAttributeId.NETWORK_KEY).isEqualTo(0x1027)
        assertThat(WscAttributeId.SSID).isEqualTo(0x1045)
        assertThat(WscAttributeId.VERSION).isEqualTo(0x104A)
    }

    @Test
    fun `认证与加密类型取值与规范一致`() {
        assertThat(WscAuthType.OPEN.code).isEqualTo(0x0001)
        assertThat(WscAuthType.WPA_PERSONAL.code).isEqualTo(0x0002)
        assertThat(WscAuthType.SHARED.code).isEqualTo(0x0004)
        assertThat(WscAuthType.WPA_ENTERPRISE.code).isEqualTo(0x0008)
        assertThat(WscAuthType.WPA2_ENTERPRISE.code).isEqualTo(0x0010)
        assertThat(WscAuthType.WPA2_PERSONAL.code).isEqualTo(0x0020)

        assertThat(WscEncryptionType.NONE.code).isEqualTo(0x0001)
        assertThat(WscEncryptionType.WEP.code).isEqualTo(0x0002)
        assertThat(WscEncryptionType.TKIP.code).isEqualTo(0x0004)
        assertThat(WscEncryptionType.AES.code).isEqualTo(0x0008)

        assertThat(WscAuthType.fromCode(0x0020)).isEqualTo(WscAuthType.WPA2_PERSONAL)
        assertThat(WscAuthType.fromCode(0x1234)).isNull()
        assertThat(WscEncryptionType.fromCode(0x0008)).isEqualTo(WscEncryptionType.AES)
        assertThat(WscEncryptionType.fromCode(0x1234)).isNull()
    }

    @Test
    fun `编码负载的 TLV 结构与长度字段正确`() {
        val payload = WscCodec.encode(wpa2)

        // 外层：Version(0x104A, 1 字节 0x10) + Credential(0x100E, 57 字节)
        assertThat(payload.size).isEqualTo(66)
        assertThat(Bytes.readU16be(payload, 0)).isEqualTo(WscAttributeId.VERSION)
        assertThat(Bytes.readU16be(payload, 2)).isEqualTo(1)
        assertThat(payload[4]).isEqualTo(0x10)
        assertThat(Bytes.readU16be(payload, 5)).isEqualTo(WscAttributeId.CREDENTIAL)
        assertThat(Bytes.readU16be(payload, 7)).isEqualTo(57)

        // 内层逐条比对
        val inner = payload.copyOfRange(9, payload.size)
        val tlvs = parseTlvs(inner)
        assertThat(tlvs.map { it.id }).containsExactly(
            WscAttributeId.NETWORK_INDEX,
            WscAttributeId.SSID,
            WscAttributeId.AUTH_TYPE,
            WscAttributeId.ENCRYPTION_TYPE,
            WscAttributeId.NETWORK_KEY,
        ).inOrder()
        assertThat(Hex.encode(tlvs.first { it.id == WscAttributeId.SSID }.value))
            .isEqualTo("4C61622D4E6574776F726B")
        assertThat(Hex.encode(tlvs.first { it.id == WscAttributeId.AUTH_TYPE }.value))
            .isEqualTo("0020")
        assertThat(Hex.encode(tlvs.first { it.id == WscAttributeId.ENCRYPTION_TYPE }.value))
            .isEqualTo("0008")
        assertThat(Hex.encode(tlvs.first { it.id == WscAttributeId.NETWORK_KEY }.value))
            .isEqualTo("636F727265637420686F7273652062617474657279")
    }

    @Test
    fun `编码结果与已验证的黄金字节串一致`() {
        // 该字节串已用独立实现 ndeflib 0.3.3 解码验证：
        // 解出的 SSID / 密码 / 认证类型 / 加密类型与本用例构造的凭据完全一致。
        // 验证方式见 README「测试与验证」章节。
        val encoded = NdefCodec.encode(NdefMessage.of(WscCodec.buildRecord(wpa2)))
        assertThat(Hex.encode(encoded)).isEqualTo(GOLDEN_WPA2_RECORD)
    }
    @Test
    fun `编码解码可往返`() {
        val decoded = WscCodec.decode(WscCodec.encode(wpa2))
        assertThat(decoded).isNotNull()
        assertThat(decoded!!.ssid).isEqualTo("Lab-Network")
        assertThat(decoded.networkKey).isEqualTo("correct horse battery")
        assertThat(decoded.authType).isEqualTo(WscAuthType.WPA2_PERSONAL)
        assertThat(decoded.encryptionType).isEqualTo(WscEncryptionType.AES)
        assertThat(decoded.networkIndex).isEqualTo(1)
    }

    @Test
    fun `开放网络不写 Network Key 属性`() {
        val open = WifiCredential(
            ssid = "Guest-WiFi",
            networkKey = "",
            authType = WscAuthType.OPEN,
            encryptionType = WscEncryptionType.NONE,
        )
        val payload = WscCodec.encode(open)
        val inner = payload.copyOfRange(9, payload.size)
        assertThat(parseTlvs(inner).map { it.id })
            .doesNotContain(WscAttributeId.NETWORK_KEY)

        val decoded = WscCodec.decode(payload)
        assertThat(decoded!!.networkKey).isEmpty()
        assertThat(decoded.authType).isEqualTo(WscAuthType.OPEN)
        assertThat(decoded.encryptionType).isEqualTo(WscEncryptionType.NONE)
    }

    @Test
    fun `带 MAC 地址时可往返`() {
        val credential = wpa2.copy(macAddress = Hex.decode("AABBCCDDEEFF"))
        val decoded = WscCodec.decode(WscCodec.encode(credential))
        assertThat(Hex.encode(decoded!!.macAddress!!)).isEqualTo("AABBCCDDEEFF")
    }

    @Test
    fun `生成的 NDEF 记录为 WSC MIME 记录`() {
        val record = WscCodec.buildRecord(wpa2)
        assertThat(NdefRecords.isWifiConfiguration(record)).isTrue()
        assertThat(record.typeAscii).isEqualTo("application/vnd.wfa.wsc")
    }

    @Test
    fun `结构不合法时解码返回 null`() {
        assertThat(WscCodec.decode(ByteArray(0))).isNull()
        assertThat(WscCodec.decode(byteArrayOf(0x10, 0x4A, 0x00, 0x01, 0x10))).isNull()
        // 只有 Version 没有 Credential
        assertThat(WscCodec.decode(byteArrayOf(0x10, 0x4A, 0x00, 0x01, 0x10, 0x00))).isNull()
        // 长度字段越界
        assertThat(WscCodec.decode(byteArrayOf(0x10, 0x0E, 0x7F, 0x7F, 0x01))).isNull()
    }

    @Test
    fun `凭据不合法时编码抛错`() {
        val bad = wpa2.copy(networkKey = "short")
        assertThrows(NdefFormatException::class.java) { WscCodec.encode(bad) }
    }

    private class Tlv(val id: Int, val value: ByteArray)

    private fun parseTlvs(bytes: ByteArray): List<Tlv> {
        val result = ArrayList<Tlv>()
        var offset = 0
        while (offset < bytes.size) {
            val id = Bytes.readU16be(bytes, offset)
            val length = Bytes.readU16be(bytes, offset + 2)
            offset += 4
            result.add(Tlv(id, bytes.copyOfRange(offset, offset + length)))
            offset += length
        }
        return result
    }

    private companion object {
        /**
         * 本工程 WSC 编码器对 [wpa2] 的期望输出。
         *
         * 该字节串已用独立实现 ndeflib 0.3.3 解码反验：
         * SSID 字节 `4C61622D4E6574776F726B` = "Lab-Network"、
         * Network Key 字节 `636F727265637420686F7273652062617474657279` = "correct horse battery"、
         * Authentication Type 0x0020 = WPA2-Personal、Encryption Type 0x0008 = AES、Version = 1.0。
         */
        const val GOLDEN_WPA2_RECORD =
            "D217426170706C69636174696F6E2F766E642E7766612E777363" +
                "104A000110" +
                "100E0039" +
                "1026000101" +
                "1045000B4C61622D4E6574776F726B" +
                "100300020020" +
                "100F00020008" +
                "10270015636F727265637420686F7273652062617474657279"
    }
}
