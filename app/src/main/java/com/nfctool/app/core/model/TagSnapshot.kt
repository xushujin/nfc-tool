package com.nfctool.app.core.model

import com.nfctool.app.core.bytes.Hex
import com.nfctool.app.core.mifare.MifareClassicSize
import com.nfctool.app.core.mifare.MifareDump
import com.nfctool.app.core.ndef.NdefMessage

/** 标签的 NDEF 能力信息。 */
class NdefInfo(
    /** 标签已格式化但没有内容时为 null（`Ndef#getNdefMessage()` 返回 null 的情形）。 */
    val message: NdefMessage?,
    val writable: Boolean,
    /** `Ndef#getMaxSize()`：该标签能容纳的 NDEF 消息最大字节数。 */
    val maxSizeBytes: Int,
    val canMakeReadOnly: Boolean,
) {
    val isEmpty: Boolean get() = message == null
}

/** MIFARE Classic 的读取结果。 */
class MifareClassicInfo(
    val size: MifareClassicSize,
    /** 全部扇区都读到时才给出整卡转储；有扇区读不到时为 null。 */
    val dump: MifareDump?,
    val readableSectors: List<Int>,
    val unreadableSectors: List<Int>,
    /**
     * 从转储的数据块里尽力解析出的 NDEF 消息。
     *
     * 这是**启发式**结果：把转储中所有数据块（不含厂商块与扇区尾块）按块号拼起来后
     * 查找 NDEF TLV。标准 NDEF 格式化的 MIFARE Classic 卡能得到正确结果，
     * 但数据块内容非标准的卡片可能解析失败或解析出无关内容。
     * 平台 [android.nfc.tech.Ndef] 能直接读到 NDEF 时，以平台结果为准。
     */
    val embeddedNdef: NdefMessage? = null,
) {
    val isComplete: Boolean get() = dump != null
}

/**
 * 一次读卡的完整快照。
 *
 * 该对象只在内存中传递（读卡 → 写卡），不落库：
 * 标签内容可能包含 WiFi 密码等敏感数据，落库会带来不必要的泄露面。
 */
class TagSnapshot(
    val uid: ByteArray,
    val technologies: List<String>,
    val ndef: NdefInfo?,
    val mifareClassic: MifareClassicInfo?,
    /** 读取过程中遇到的问题，供界面提示；不影响快照本身可用。 */
    val warnings: List<String> = emptyList(),
) {
    val uidHex: String get() = Hex.encode(uid)

    val hasNdef: Boolean get() = ndef != null

    val isNdefWritable: Boolean get() = ndef?.writable == true

    override fun toString(): String =
        "TagSnapshot(uid=$uidHex, tech=${technologies.joinToString("/")}, " +
            "ndef=${ndef?.message?.records?.size ?: 0} 条记录, " +
            "mifare=${mifareClassic?.size?.name ?: "无"})"
}
