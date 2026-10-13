package com.nfctool.app.core.capacity

import com.nfctool.app.core.ndef.NdefMessage

/** 容量校验结果。 */
sealed interface CapacityResult {

    /** 放得下。 */
    class Fits(val usedBytes: Int, val capacityBytes: Int) : CapacityResult {
        val remainingBytes: Int get() = capacityBytes - usedBytes
    }

    /** 放不下。 */
    class Exceeds(val usedBytes: Int, val capacityBytes: Int) : CapacityResult {
        val overflowBytes: Int get() = usedBytes - capacityBytes
    }

    /** 目标标签没有报告可用容量，无法判断。 */
    class Unknown(val usedBytes: Int) : CapacityResult
}

/**
 * 写入前的容量校验。
 *
 * 说明：`android.nfc.tech.Ndef#getMaxSize()` 返回的是「该标签能容纳的 NDEF 消息最大字节数」，
 * 已包含 TLV 与长度字段开销，因此直接与编码后的消息长度比较即可。
 */
object CapacityCheck {

    fun check(message: NdefMessage, capacityBytes: Int?): CapacityResult {
        val used = message.encodedSize
        if (capacityBytes == null || capacityBytes <= 0) return CapacityResult.Unknown(used)
        return if (used <= capacityBytes) {
            CapacityResult.Fits(used, capacityBytes)
        } else {
            CapacityResult.Exceeds(used, capacityBytes)
        }
    }
}
