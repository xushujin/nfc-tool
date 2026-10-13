package com.nfctool.app.core.model

import com.google.common.truth.Truth.assertThat
import com.nfctool.app.core.ndef.NdefMessage
import com.nfctool.app.core.ndef.NdefRecord
import com.nfctool.app.core.ndef.NdefRecords
import com.nfctool.app.core.ndef.NdefTnf
import com.nfctool.app.core.wsc.WifiCredential
import com.nfctool.app.core.wsc.WscAuthType
import com.nfctool.app.core.wsc.WscCodec
import com.nfctool.app.core.wsc.WscEncryptionType
import org.junit.Test

class RecordSummariesTest {

    @Test
    fun `文本记录摘要带语言与编码`() {
        val summary = RecordSummaries.of(NdefRecords.text("你好", "zh"))
        assertThat(summary.kind).isEqualTo(RecordKind.TEXT)
        assertThat(summary.title).contains("zh")
        assertThat(summary.title).contains("UTF-8")
        assertThat(summary.detail).isEqualTo("你好")
    }

    @Test
    fun `URI 记录摘要为网页链接`() {
        val summary = RecordSummaries.of(NdefRecords.uri("https://example.com/a"))
        assertThat(summary.kind).isEqualTo(RecordKind.WEB_LINK)
        assertThat(summary.detail).isEqualTo("https://example.com/a")
    }

    @Test
    fun `WiFi 记录摘要不泄露密码内容`() {
        val credential = WifiCredential(
            ssid = "Lab-Network",
            networkKey = "super-secret",
            authType = WscAuthType.WPA2_PERSONAL,
            encryptionType = WscEncryptionType.AES,
        )
        val summary = RecordSummaries.of(WscCodec.buildRecord(credential))
        assertThat(summary.kind).isEqualTo(RecordKind.WIFI)
        assertThat(summary.detail).contains("Lab-Network")
        assertThat(summary.detail).contains("密码已包含")
        assertThat(summary.detail).doesNotContain("super-secret")
    }

    @Test
    fun `开放网络摘要标注密码未包含`() {
        val credential = WifiCredential(
            ssid = "Guest",
            networkKey = "",
            authType = WscAuthType.OPEN,
            encryptionType = WscEncryptionType.NONE,
        )
        val summary = RecordSummaries.of(WscCodec.buildRecord(credential))
        assertThat(summary.detail).contains("密码未包含")
    }

    @Test
    fun `结构损坏的 WSC 负载给出明确提示而不是崩溃`() {
        val broken = NdefRecords.mime(NdefRecords.MIME_WIFI_WSC, byteArrayOf(0x10, 0x4A))
        val summary = RecordSummaries.of(broken)
        assertThat(summary.kind).isEqualTo(RecordKind.WIFI)
        assertThat(summary.detail).contains("无法解析")
    }

    @Test
    fun `智能海报摘要包含标题与目标`() {
        val summary = RecordSummaries.of(NdefRecords.smartPoster("https://example.com/", "标题", "zh"))
        assertThat(summary.kind).isEqualTo(RecordKind.SMART_POSTER)
        assertThat(summary.detail).contains("标题")
        assertThat(summary.detail).contains("https://example.com/")
    }

    @Test
    fun `Android 应用记录摘要包含包名`() {
        val summary = RecordSummaries.of(NdefRecords.androidApplication("com.nfctool.app"))
        assertThat(summary.kind).isEqualTo(RecordKind.ANDROID_APP)
        assertThat(summary.detail).contains("com.nfctool.app")
    }

    @Test
    fun `MIME 记录摘要标注类型与负载长度`() {
        val summary = RecordSummaries.of(NdefRecords.mime("image/png", ByteArray(32)))
        assertThat(summary.kind).isEqualTo(RecordKind.MEDIA)
        assertThat(summary.title).contains("image/png")
        assertThat(summary.detail).contains("32 字节")
    }

    @Test
    fun `外部类型记录摘要`() {
        val summary = RecordSummaries.of(NdefRecords.external("example.com", "demo", ByteArray(4)))
        assertThat(summary.kind).isEqualTo(RecordKind.EXTERNAL)
        assertThat(summary.title).contains("example.com:demo")
    }

    @Test
    fun `绝对 URI 记录归入网页链接`() {
        val summary = RecordSummaries.of(NdefRecords.absoluteUri("https://example.com/abs"))
        assertThat(summary.kind).isEqualTo(RecordKind.WEB_LINK)
        assertThat(summary.detail).isEqualTo("https://example.com/abs")
    }

    @Test
    fun `空记录摘要`() {
        val summary = RecordSummaries.of(NdefRecord.empty())
        assertThat(summary.kind).isEqualTo(RecordKind.EMPTY)
        assertThat(summary.payloadBytes).isEqualTo(0)
    }

    @Test
    fun `未知 TNF 记录归入未知类型`() {
        val unknown = NdefRecord(NdefTnf.UNKNOWN, byteArrayOf(0x01), ByteArray(0), byteArrayOf(0x02))
        val summary = RecordSummaries.of(unknown)
        assertThat(summary.kind).isEqualTo(RecordKind.UNKNOWN)
    }

    @Test
    fun `超长文本被截断并加省略号`() {
        val longText = "a".repeat(300)
        val summary = RecordSummaries.of(NdefRecords.text(longText, "en"))
        assertThat(summary.detail).hasLength(161)
        assertThat(summary.detail).endsWith("…")
    }

    @Test
    fun `消息整体摘要与类别串`() {
        val message = NdefMessage(
            listOf(
                NdefRecords.text("说明", "zh"),
                NdefRecords.uri("https://example.com/"),
                NdefRecords.uri("https://example.com/2"),
            ),
        )
        val summaries = RecordSummaries.of(message)
        assertThat(summaries).hasSize(3)
        assertThat(RecordSummaries.kindsLabel(summaries)).isEqualTo("文本 + 网页链接")
    }
}
