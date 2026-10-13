package com.nfctool.app.nfc

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.nfctool.app.core.bytes.Hex
import com.nfctool.app.core.ndef.NdefCodec
import com.nfctool.app.core.ndef.NdefMessage
import com.nfctool.app.core.ndef.NdefRecord
import com.nfctool.app.core.ndef.NdefRecords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 用**真实的 Android NDEF 类**验证复制链路的编码环节。
 *
 * 复制 = 读出源卡 → 转成核心层模型 → 再转成平台模型写进目标卡。
 * 这中间任何一环出错，表现都是"复制没反应"。端到端的写卡只能在真机上验，
 * 但「核心层模型 ↔ 平台模型」这一步可以用真实的
 * `android.nfc.NdefRecord` / `NdefMessage` 在本地跑通，
 * 因为它们是纯 Java 实现，不依赖 NFC 硬件。
 */
@RunWith(RobolectricTestRunner::class)
class NdefBridgeTest {

    private fun sample(): NdefMessage = NdefMessage(
        listOf(
            NdefRecords.text("会议室 A", "zh"),
            NdefRecords.uri("https://example.com/room"),
            NdefRecords.mime("audio/mpeg", byteArrayOf(0x01, 0x02, 0x03)),
            NdefRecords.androidApplication("com.nfctool.app"),
        ),
    )

    @Test
    fun `核心层消息转平台模型后能被平台重新编码`() {
        val core = sample()
        val platform = NdefBridge.toPlatform(core)

        val platformBytes = platform.toByteArray()
        val coreBytes = NdefCodec.encode(core)

        assertWithMessage("自实现编码与 Android 平台编码的字节应完全一致")
            .that(Hex.encode(coreBytes))
            .isEqualTo(Hex.encode(platformBytes))
    }

    @Test
    fun `平台编码结果能被核心层解码回同样的记录`() {
        val core = sample()
        val platformBytes = NdefBridge.toPlatform(core).toByteArray()

        val reparsed = NdefCodec.decode(platformBytes)
        assertThat(reparsed.records).hasSize(core.records.size)
        for (index in core.records.indices) {
            val expected = core.records[index]
            val actual = reparsed.records[index]
            assertWithMessage("第 $index 条记录的 TNF")
                .that(actual.tnf).isEqualTo(expected.tnf)
            assertWithMessage("第 $index 条记录的 type")
                .that(Hex.encode(actual.type)).isEqualTo(Hex.encode(expected.type))
            assertWithMessage("第 $index 条记录的 payload")
                .that(Hex.encode(actual.payload)).isEqualTo(Hex.encode(expected.payload))
        }
    }

    @Test
    fun `平台模型转核心层不丢字段`() {
        val platform = NdefBridge.toPlatform(sample())
        val back = NdefBridge.toCore(platform)

        assertThat(back.records).hasSize(platform.records.size)
        for (index in platform.records.indices) {
            assertWithMessage("第 $index 条记录")
                .that(Hex.encode(NdefCodec.encode(NdefMessage(listOf(back.records[index])))))
                .isEqualTo(Hex.encode(NdefCodec.encode(NdefMessage(listOf(sample().records[index])))))
        }
    }

    /**
     * 空记录是唯一一处平台编码与规范不一致的地方，这里把实测结果钉住。
     *
     * 实测（Robolectric 跑真实 Android 框架代码）：
     * - 核心层（按 NDEF 1.0 §3.2）编码空记录为 `D0 00 00`：
     *   头部 + 无条件存在的类型长度、负载长度两个字段，共 3 字节；
     * - Android `NdefRecord.toByteArray()` 编码为 `D8 00 00 00`：
     *   多置了一个 IL 位并多写一个取值为 0 的 ID 长度字节，共 4 字节。
     *
     * 试过在 `toPlatform` 里把空 ID 传成 null，结果仍是 `D8 00 00 00`，
     * 因此这是平台自身行为，应用层改不掉。
     *
     * 影响可控：复制走的是平台编码器，落到卡上的是 Android 那种 4 字节形式，
     * 而 Android 自己能读回 —— 也就是说同一台设备上的读写闭环不受影响。
     * 只在"把空记录写到卡上，再用严格按规范实现的第三方读卡器去读"这种
     * 跨实现场景下才可能出问题，实际极少遇到，故如实记录而不做规避。
     */
    @Test
    fun `空记录的平台编码与核心层编码存在差异`() {
        val core = NdefMessage(listOf(NdefRecord.empty()))

        assertWithMessage("核心层按规范编码空记录应为 3 字节")
            .that(Hex.encode(NdefCodec.encode(core)))
            .isEqualTo("D00000")

        assertWithMessage("Android 平台编码空记录实测为 4 字节（多一个 ID 长度字段）")
            .that(Hex.encode(NdefBridge.toPlatform(core).toByteArray()))
            .isEqualTo("D8000000")
    }

    @Test
    fun `常见记录类型的平台编码与核心层编码逐字节相同`() {
        val core = sample()

        assertWithMessage("文本/URI/MIME/AAR 四类记录的编码必须与 Android 完全一致")
            .that(Hex.encode(NdefBridge.toPlatform(core).toByteArray()))
            .isEqualTo(Hex.encode(NdefCodec.encode(core)))
    }
}
