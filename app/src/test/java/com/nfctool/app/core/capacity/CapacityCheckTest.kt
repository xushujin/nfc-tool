package com.nfctool.app.core.capacity

import com.google.common.truth.Truth.assertThat
import com.nfctool.app.core.ndef.NdefMessage
import com.nfctool.app.core.ndef.NdefRecords
import org.junit.Test

class CapacityCheckTest {

    private val textMessage = NdefMessage.of(NdefRecords.text("Hello NFC", "en"))

    @Test
    fun `容量足够时返回 Fits 并给出剩余字节`() {
        val used = textMessage.encodedSize
        val result = CapacityCheck.check(textMessage, used + 10)
        assertThat(result).isInstanceOf(CapacityResult.Fits::class.java)
        val fits = result as CapacityResult.Fits
        assertThat(fits.usedBytes).isEqualTo(used)
        assertThat(fits.capacityBytes).isEqualTo(used + 10)
        assertThat(fits.remainingBytes).isEqualTo(10)
    }

    @Test
    fun `刚好等于容量时也算放得下`() {
        val used = textMessage.encodedSize
        val result = CapacityCheck.check(textMessage, used)
        assertThat(result).isInstanceOf(CapacityResult.Fits::class.java)
        assertThat((result as CapacityResult.Fits).remainingBytes).isEqualTo(0)
    }

    @Test
    fun `超出容量时返回 Exceeds 并给出超出字节数`() {
        val used = textMessage.encodedSize
        val result = CapacityCheck.check(textMessage, used - 1)
        assertThat(result).isInstanceOf(CapacityResult.Exceeds::class.java)
        val exceeds = result as CapacityResult.Exceeds
        assertThat(exceeds.usedBytes).isEqualTo(used)
        assertThat(exceeds.overflowBytes).isEqualTo(1)
    }

    @Test
    fun `容量未知或非正时返回 Unknown`() {
        assertThat(CapacityCheck.check(textMessage, null))
            .isInstanceOf(CapacityResult.Unknown::class.java)
        assertThat(CapacityCheck.check(textMessage, 0))
            .isInstanceOf(CapacityResult.Unknown::class.java)
        assertThat(CapacityCheck.check(textMessage, -1))
            .isInstanceOf(CapacityResult.Unknown::class.java)
    }

    @Test
    fun `NTAG213 典型容量下 16 字节文本可以写入`() {
        // NTAG213 的 NDEF 可用容量通常为 144 字节
        val result = CapacityCheck.check(textMessage, 144)
        assertThat(result).isInstanceOf(CapacityResult.Fits::class.java)
    }
}
