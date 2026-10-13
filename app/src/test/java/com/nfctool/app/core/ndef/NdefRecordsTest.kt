package com.nfctool.app.core.ndef

import com.google.common.truth.Truth.assertThat
import com.nfctool.app.core.bytes.Hex
import org.junit.Assert.assertThrows
import org.junit.Test

class UriPrefixTest {

    @Test
    fun `缩写表共 36 项且首项为空`() {
        assertThat(UriPrefix.size).isEqualTo(36)
        assertThat(UriPrefix.prefixOf(0x00)).isEmpty()
        assertThat(UriPrefix.prefixOf(0x01)).isEqualTo("http://www.")
        assertThat(UriPrefix.prefixOf(0x04)).isEqualTo("https://")
        assertThat(UriPrefix.prefixOf(0x05)).isEqualTo("tel:")
        assertThat(UriPrefix.prefixOf(0x23)).isEqualTo("urn:nfc:")
    }

    @Test
    fun `压缩时选择最长匹配前缀`() {
        // https://www. 比 https:// 更长，应优先命中 0x02
        val (code, remainder) = UriPrefix.compress("https://www.example.com/a")
        assertThat(code).isEqualTo(0x02)
        assertThat(remainder).isEqualTo("example.com/a")

        val (httpCode, httpRemainder) = UriPrefix.compress("http://example.com/a")
        assertThat(httpCode).isEqualTo(0x03)
        assertThat(httpRemainder).isEqualTo("example.com/a")

        val (telCode, telRemainder) = UriPrefix.compress("tel:10086")
        assertThat(telCode).isEqualTo(0x05)
        assertThat(telRemainder).isEqualTo("10086")
    }

    @Test
    fun `无匹配前缀时使用 0x00 并保留完整 URI`() {
        val (code, remainder) = UriPrefix.compress("nfc-tool://open")
        assertThat(code).isEqualTo(0x00)
        assertThat(remainder).isEqualTo("nfc-tool://open")
    }

    @Test
    fun `展开与压缩互逆`() {
        val uris = listOf(
            "https://www.example.com/path?q=1",
            "http://example.com/",
            "tel:+8613800138000",
            "mailto:someone@example.com",
            "ftp://ftp.example.com/file",
            "urn:nfc:ext:example.com:type",
            "custom-scheme:payload",
        )
        for (uri in uris) {
            val (code, remainder) = UriPrefix.compress(uri)
            assertThat(UriPrefix.expand(code, remainder)).isEqualTo(uri)
        }
    }

    @Test
    fun `超出表范围的前缀代码报错`() {
        val error = assertThrows(NdefFormatException::class.java) { UriPrefix.prefixOf(0x24) }
        assertThat(error.message).contains("超出缩写表范围")
    }
}

class NdefRecordsTest {

    @Test
    fun `文本记录往返并保留语言与编码`() {
        val record = NdefRecords.text("测试文本", "zh")
        val payload = NdefRecords.parseText(record)
        assertThat(payload).isNotNull()
        assertThat(payload!!.text).isEqualTo("测试文本")
        assertThat(payload.languageCode).isEqualTo("zh")
        assertThat(payload.isUtf16).isFalse()
    }

    @Test
    fun `文本记录状态字节低六位为语言长度`() {
        val record = NdefRecords.text("hi", "en-US")
        assertThat(record.payload[0].toInt() and 0x3F).isEqualTo(5)
        assertThat(record.payload[0].toInt() and 0x80).isEqualTo(0)
    }

    @Test
    fun `文本记录拒绝空内容与超长语言代码`() {
        assertThrows(IllegalArgumentException::class.java) { NdefRecords.text("", "en") }
        assertThrows(IllegalArgumentException::class.java) { NdefRecords.text("x", "") }
        assertThrows(IllegalArgumentException::class.java) { NdefRecords.text("x", "a".repeat(64)) }
    }

    @Test
    fun `文本解析在语言长度越界时报错`() {
        val bad = NdefRecord(
            NdefTnf.WELL_KNOWN,
            byteArrayOf(0x54),
            ByteArray(0),
            byteArrayOf(0x0A, 0x65),
        )
        assertThrows(NdefFormatException::class.java) { NdefRecords.parseText(bad) }
    }

    @Test
    fun `URI 记录使用前缀缩写并可还原`() {
        val record = NdefRecords.uri("https://www.example.com/")
        assertThat(record.tnf).isEqualTo(NdefTnf.WELL_KNOWN)
        assertThat(record.typeAscii).isEqualTo("U")
        assertThat(record.payload[0].toInt() and 0xFF).isEqualTo(0x02)
        assertThat(NdefRecords.parseUri(record)).isEqualTo("https://www.example.com/")
    }

    @Test
    fun `非文本或非 URI 记录解析返回 null`() {
        val mime = NdefRecords.mime("image/png", byteArrayOf(1, 2, 3))
        assertThat(NdefRecords.parseText(mime)).isNull()
        assertThat(NdefRecords.parseUri(mime)).isNull()
        assertThat(NdefRecords.parseSmartPoster(mime)).isNull()
    }

    @Test
    fun `MIME 与外部类型记录的类型字段正确`() {
        val mime = NdefRecords.mime("image/png", byteArrayOf(0x89.toByte()))
        assertThat(mime.tnf).isEqualTo(NdefTnf.MIME)
        assertThat(mime.typeAscii).isEqualTo("image/png")

        val external = NdefRecords.external("example.com", "demo", byteArrayOf(1))
        assertThat(external.tnf).isEqualTo(NdefTnf.EXTERNAL)
        assertThat(external.typeAscii).isEqualTo("example.com:demo")
    }

    @Test
    fun `绝对 URI 记录的类型字段即完整 URI 且负载为空`() {
        val record = NdefRecords.absoluteUri("https://example.com/absolute")
        assertThat(record.tnf).isEqualTo(NdefTnf.ABSOLUTE_URI)
        assertThat(record.typeAscii).isEqualTo("https://example.com/absolute")
        assertThat(record.payload).isEmpty()
    }

    @Test
    fun `Android 应用记录的类型为 android_com_pkg`() {
        val record = NdefRecords.androidApplication("com.nfctool.app")
        assertThat(record.tnf).isEqualTo(NdefTnf.EXTERNAL)
        assertThat(record.typeAscii).isEqualTo("android.com:pkg")
        assertThat(String(record.payload, Charsets.UTF_8)).isEqualTo("com.nfctool.app")
        assertThat(NdefRecords.isAndroidApplication(record)).isTrue()
    }

    @Test
    fun `智能海报负载是一条内嵌 NDEF 消息`() {
        val record = NdefRecords.smartPoster("https://example.com/", "标题", "zh")
        assertThat(record.tnf).isEqualTo(NdefTnf.WELL_KNOWN)
        assertThat(record.typeAscii).isEqualTo("Sp")

        val inner = NdefCodec.decode(record.payload)
        assertThat(inner.records).hasSize(2)

        val poster = NdefRecords.parseSmartPoster(record)
        assertThat(poster).isNotNull()
        assertThat(poster!!.uri).isEqualTo("https://example.com/")
        assertThat(poster.title).isEqualTo("标题")
    }

    @Test
    fun `智能海报不带标题时只含 URI 记录`() {
        val record = NdefRecords.smartPoster("https://example.com/")
        val inner = NdefCodec.decode(record.payload)
        assertThat(inner.records).hasSize(1)
        assertThat(NdefRecords.parseSmartPoster(record)!!.title).isNull()
    }

    @Test
    fun `WSC 记录识别为 WiFi 配置`() {
        val record = NdefRecords.mime(NdefRecords.MIME_WIFI_WSC, byteArrayOf(1, 2, 3, 4))
        assertThat(NdefRecords.isWifiConfiguration(record)).isTrue()
        assertThat(NdefRecords.isWifiConfiguration(NdefRecords.mime("image/png", ByteArray(0))))
            .isFalse()
    }

    @Test
    fun `记录相等性按内容比较`() {
        val a = NdefRecords.text("same", "en")
        val b = NdefRecords.text("same", "en")
        val c = NdefRecords.text("other", "en")
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
        assertThat(a).isNotEqualTo(c)
    }

    @Test
    fun `类型字段必须为 ASCII`() {
        assertThrows(IllegalArgumentException::class.java) { NdefRecords.mime("图像/png", ByteArray(0)) }
    }

    @Test
    fun `空记录工厂产出 TNF 0`() {
        val empty = NdefRecord.empty()
        assertThat(empty.tnf).isEqualTo(NdefTnf.EMPTY)
        assertThat(empty.isEmpty).isTrue()
        // 单条空记录：MB|ME|SR|TNF=0 => 0xD0，随后类型长度 0、负载长度 0
        assertThat(Hex.encode(NdefCodec.encode(NdefMessage.of(empty)))).isEqualTo("D00000")
    }
}
