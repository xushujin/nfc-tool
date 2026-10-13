package com.nfctool.app.ui

import com.google.common.truth.Truth.assertThat
import com.nfctool.app.core.model.TagSnapshot
import org.junit.Test

/**
 * 界面状态合并规则的回归测试。
 *
 * 这里锁住的是一条真实踩过的坑：写卡成功后会自动回读一次做校验，
 * 而回读原本沿用「用户主动读卡」的分支，把刚生成的成功提示一并清空，
 * 结果写入其实成功了，用户却什么都看不到 —— 表现为「复制功能没反应」。
 */
class NfcToolUiStateTest {

    private val snapshot = TagSnapshot(
        uid = byteArrayOf(0x04, 0x11, 0x22, 0x33),
        technologies = listOf("NfcA", "Ndef"),
        ndef = null,
        mifareClassic = null,
    )

    @Test
    fun `写卡后的回读必须保留提示`() {
        val afterWrite = NfcToolUiState(
            status = StatusMessage("已写入 42 字节", StatusLevel.SUCCESS),
            busy = true,
        )

        val afterVerify = afterWrite.afterRead(snapshot, clearStatus = false)

        assertThat(afterVerify.status?.text).isEqualTo("已写入 42 字节")
        assertThat(afterVerify.status?.level).isEqualTo(StatusLevel.SUCCESS)
        assertThat(afterVerify.snapshot).isEqualTo(snapshot)
        assertThat(afterVerify.busy).isFalse()
    }

    @Test
    fun `用户主动读卡时清空提示`() {
        val state = NfcToolUiState(
            status = StatusMessage("上一条提示", StatusLevel.INFO),
        )

        val after = state.afterRead(snapshot, clearStatus = true)

        assertThat(after.status).isNull()
        assertThat(after.snapshot).isEqualTo(snapshot)
        assertThat(after.busy).isFalse()
    }

    @Test
    fun `回读保留提示时也不残留待执行操作标记之外的脏状态`() {
        val state = NfcToolUiState(
            status = StatusMessage("部分成功", StatusLevel.WARNING),
            busy = true,
        )

        val after = state.afterRead(null, clearStatus = false)

        assertThat(after.snapshot).isNull()
        assertThat(after.busy).isFalse()
        assertThat(after.status?.level).isEqualTo(StatusLevel.WARNING)
    }
}
