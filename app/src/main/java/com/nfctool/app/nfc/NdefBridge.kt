package com.nfctool.app.nfc

import com.nfctool.app.core.ndef.NdefMessage
import com.nfctool.app.core.ndef.NdefRecord

/**
 * 核心层 NDEF 模型与 Android 平台模型之间的转换。
 *
 * 转换集中在这一处：核心层保持纯 Kotlin（可单测），
 * 平台类型只在真正读写标签时出现。
 */
object NdefBridge {

    fun toCore(record: android.nfc.NdefRecord): NdefRecord = NdefRecord(
        // 平台 API 的 tnf 是 short，核心层统一用 Int 表达
        tnf = record.tnf.toInt(),
        type = record.type,
        id = record.id ?: EMPTY,
        payload = record.payload,
    )

    fun toCore(message: android.nfc.NdefMessage): NdefMessage =
        NdefMessage(message.records.map(::toCore))

    fun toPlatform(record: NdefRecord): android.nfc.NdefRecord =
        android.nfc.NdefRecord(record.tnf.toShort(), record.type, record.id, record.payload)

    fun toPlatform(message: NdefMessage): android.nfc.NdefMessage =
        android.nfc.NdefMessage(message.records.map(::toPlatform).toTypedArray())

    private val EMPTY = ByteArray(0)
}
