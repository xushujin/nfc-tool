package com.nfctool.app.core.media

import com.google.common.truth.Truth.assertThat
import com.nfctool.app.core.ndef.NdefMessage
import com.nfctool.app.core.ndef.NdefRecords
import org.junit.Test

class MediaPayloadsTest {

    @Test
    fun `网址补全协议头`() {
        assertThat(MediaPayloads.normalizeUrl("example.com")).isEqualTo("https://example.com")
        assertThat(MediaPayloads.normalizeUrl("  example.com/a?b=1  "))
            .isEqualTo("https://example.com/a?b=1")
        assertThat(MediaPayloads.normalizeUrl("http://example.com")).isEqualTo("http://example.com")
        assertThat(MediaPayloads.normalizeUrl("tel:10086")).isEqualTo("tel:10086")
        assertThat(MediaPayloads.normalizeUrl("")).isEmpty()
    }

    @Test
    fun `网址合法性检查`() {
        assertThat(MediaPayloads.urlProblem("https://example.com/a")).isNull()
        assertThat(MediaPayloads.urlProblem("http://example.com")).isNull()
        assertThat(MediaPayloads.urlProblem("tel:10086")).isNull()
        assertThat(MediaPayloads.urlProblem("mailto:a@b.com")).isNull()

        assertThat(MediaPayloads.urlProblem("")).contains("不能为空")
        assertThat(MediaPayloads.urlProblem("ftp://example.com")).contains("仅支持")
        assertThat(MediaPayloads.urlProblem("https://")).contains("缺少主机名")
        assertThat(MediaPayloads.urlProblem("https://exa mple.com")).contains("空格")
    }

    @Test
    fun `网页链接生成 URI 记录并补全协议头`() {
        val plan = MediaPayloads.webLink("example.com/page")
        assertThat(plan).isInstanceOf(MediaPlan.Ready::class.java)
        val ready = plan as MediaPlan.Ready
        assertThat(ready.records).hasSize(1)
        assertThat(NdefRecords.parseUri(ready.records[0])).isEqualTo("https://example.com/page")
        assertThat(ready.description).contains("https://example.com/page")
    }

    @Test
    fun `网页链接可附带标题文本记录`() {
        val plan = MediaPayloads.webLink("https://example.com", "示例站点", "zh") as MediaPlan.Ready
        assertThat(plan.records).hasSize(2)
        assertThat(plan.records[0].typeAscii).isEqualTo("T")
        assertThat(NdefRecords.parseText(plan.records[0])!!.languageCode).isEqualTo("zh")
        assertThat(plan.records[1].typeAscii).isEqualTo("U")
    }

    @Test
    fun `网址不合法时返回 Invalid 而不是抛异常`() {
        val plan = MediaPayloads.webLink("ftp://example.com/file")
        assertThat(plan).isInstanceOf(MediaPlan.Invalid::class.java)
        assertThat((plan as MediaPlan.Invalid).reason).contains("仅支持")
    }

    @Test
    fun `音视频网络地址必须是 http 或 https`() {
        val ok = MediaPayloads.remoteMedia("https://cdn.example.com/a.mp3", MediaKind.AUDIO)
        assertThat(ok).isInstanceOf(MediaPlan.Ready::class.java)

        val bad = MediaPayloads.remoteMedia("tel:10086", MediaKind.VIDEO)
        assertThat(bad).isInstanceOf(MediaPlan.Invalid::class.java)
        assertThat((bad as MediaPlan.Invalid).reason).contains("必须是 http / https 链接")
    }

    @Test
    fun `按扩展名推断 MIME 类型`() {
        assertThat(MediaPayloads.mimeTypeForFileName("song.mp3")).isEqualTo("audio/mpeg")
        assertThat(MediaPayloads.mimeTypeForFileName("clip.MP4")).isEqualTo("video/mp4")
        assertThat(MediaPayloads.mimeTypeForFileName("voice.m4a")).isEqualTo("audio/mp4")
        assertThat(MediaPayloads.mimeTypeForFileName("unknown.xyz"))
            .isEqualTo("application/octet-stream")
        assertThat(MediaPayloads.mimeTypeForFileName("noextension"))
            .isEqualTo("application/octet-stream")
    }

    @Test
    fun `内嵌媒体在容量充足时保留标题记录`() {
        val plan = MediaPayloads.embeddedMedia(
            kind = MediaKind.AUDIO,
            fileName = "tone.mp3",
            mimeType = "audio/mpeg",
            bytes = ByteArray(64) { 1 },
            capacityBytes = 512,
        ) as MediaPlan.Ready

        assertThat(plan.records).hasSize(2)
        assertThat(plan.records[0].typeAscii).isEqualTo("T")
        assertThat(plan.records[1].typeAscii).isEqualTo("audio/mpeg")
        assertThat(plan.requiredBytes).isAtMost(512)
    }

    @Test
    fun `容量只够裸媒体时省略标题记录`() {
        val bytes = ByteArray(200) { 2 }
        val withTitle = MediaPayloads.embeddedMedia(
            kind = MediaKind.AUDIO,
            fileName = "tone.mp3",
            mimeType = "audio/mpeg",
            bytes = bytes,
            capacityBytes = null,
        ) as MediaPlan.Ready
        assertThat(withTitle.records).hasSize(2)

        // 去掉标题记录后的长度：直接构造单条 MIME 记录量一下
        val bareSize = NdefMessage(listOf(NdefRecords.mime("audio/mpeg", bytes))).encodedSize
        assertThat(bareSize).isLessThan(withTitle.requiredBytes)

        val plan = MediaPayloads.embeddedMedia(
            kind = MediaKind.AUDIO,
            fileName = "tone.mp3",
            mimeType = "audio/mpeg",
            bytes = bytes,
            capacityBytes = bareSize,
        ) as MediaPlan.Ready
        assertThat(plan.records).hasSize(1)
        assertThat(plan.requiredBytes).isEqualTo(bareSize)
        assertThat(plan.description).contains("已省略标题")
    }

    @Test
    fun `容量不足时返回 TooLarge 并给出所需与可用字节数`() {
        val plan = MediaPayloads.embeddedMedia(
            kind = MediaKind.VIDEO,
            fileName = "clip.mp4",
            mimeType = "video/mp4",
            bytes = ByteArray(4096) { 3 },
            capacityBytes = 144,
        )
        assertThat(plan).isInstanceOf(MediaPlan.TooLarge::class.java)
        val tooLarge = plan as MediaPlan.TooLarge
        assertThat(tooLarge.capacityBytes).isEqualTo(144)
        assertThat(tooLarge.requiredBytes).isGreaterThan(4096)
    }

    @Test
    fun `不校验容量时直接生成记录`() {
        val plan = MediaPayloads.embeddedMedia(
            kind = MediaKind.AUDIO,
            fileName = "tone.mp3",
            mimeType = "audio/mpeg",
            bytes = ByteArray(4096) { 4 },
            capacityBytes = null,
        )
        assertThat(plan).isInstanceOf(MediaPlan.Ready::class.java)
        assertThat((plan as MediaPlan.Ready).description).contains("未校验容量")
    }

    @Test
    fun `空内容或空 MIME 类型返回 Invalid`() {
        assertThat(
            MediaPayloads.embeddedMedia(MediaKind.AUDIO, "a.mp3", "audio/mpeg", ByteArray(0), null),
        ).isInstanceOf(MediaPlan.Invalid::class.java)
        assertThat(
            MediaPayloads.embeddedMedia(MediaKind.AUDIO, "a.mp3", "  ", byteArrayOf(1), null),
        ).isInstanceOf(MediaPlan.Invalid::class.java)
    }
}
