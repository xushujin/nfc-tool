package com.nfctool.app.core.mifare

import com.google.common.truth.Truth.assertThat
import com.nfctool.app.core.model.MifareClassicInfo
import org.junit.Test

/**
 * 整卡复制可用性判定。
 *
 * 这组用例锁的是「三种不可用原因必须区分开」这个约定：
 * 开关没开 / 卡不是 MIFARE Classic / 扇区密钥未知，
 * 用户要做的处理完全不同，判错方向比不判还糟。
 */
class MifareCopyAvailabilityTest {

    private val size = MifareClassicSize.ONE_K

    private fun completeDump(): MifareDump = MifareDump(
        size = size,
        uid = byteArrayOf(1, 2, 3, 4),
        blocks = List(size.blockCount) { ByteArray(MifareClassicLayout.BLOCK_SIZE) },
    )

    private fun info(dump: MifareDump?, unreadable: List<Int> = emptyList()): MifareClassicInfo =
        MifareClassicInfo(
            size = size,
            dump = dump,
            readableSectors = (0 until size.sectorCount).filter { it !in unreadable },
            unreadableSectors = unreadable,
        )

    @Test
    fun `转储完整时可整卡复制`() {
        val availability = MifareCopyAvailability.of(info(completeDump()), readDumpRequested = true)
        assertThat(availability).isEqualTo(MifareCopyAvailability.Available)
    }

    @Test
    fun `开关没开且无数据时提示去开开关`() {
        val availability = MifareCopyAvailability.of(info(dump = null), readDumpRequested = false)
        assertThat(availability).isEqualTo(MifareCopyAvailability.DumpNotRequested)
    }

    @Test
    fun `开关开着但没有 MIFARE 数据时判为卡类型不符`() {
        // 开关开着时 info 仍为 null，只可能是没识别成 MIFARE Classic
        //（info 非 null 而 dump 为 null 是另一种情况，见下一条用例）
        val availability = MifareCopyAvailability.of(info = null, readDumpRequested = true)
        assertThat(availability).isEqualTo(MifareCopyAvailability.NotMifareClassic)
    }

    @Test
    fun `info 为空且开关没开时判为去开开关而不是卡类型不符`() {
        // 关键分支：开关没打开时 info 必然为 null，不能因此误报"卡不对"
        val availability = MifareCopyAvailability.of(info = null, readDumpRequested = false)
        assertThat(availability).isEqualTo(MifareCopyAvailability.DumpNotRequested)
    }

    @Test
    fun `转储不完整时带出密钥未知的扇区号`() {
        val availability = MifareCopyAvailability.of(
            info(dump = null, unreadable = listOf(2, 5)),
            readDumpRequested = true,
        )
        assertThat(availability).isInstanceOf(MifareCopyAvailability.IncompleteDump::class.java)
        val incomplete = availability as MifareCopyAvailability.IncompleteDump
        assertThat(incomplete.unreadableSectors).containsExactly(2, 5).inOrder()
    }
}
