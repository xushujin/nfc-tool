package com.nfctool.app.core.ndef

/**
 * NDEF 记录的 TNF（Type Name Format）取值。
 * 依据：NFC Forum NDEF 1.0 规范 3.2.6 节表 2。
 */
object NdefTnf {

    const val EMPTY = 0x00
    const val WELL_KNOWN = 0x01
    const val MIME = 0x02
    const val ABSOLUTE_URI = 0x03
    const val EXTERNAL = 0x04
    const val UNKNOWN = 0x05
    const val UNCHANGED = 0x06
    const val RESERVED = 0x07

    fun name(tnf: Int): String = when (tnf) {
        EMPTY -> "Empty"
        WELL_KNOWN -> "NFC Forum Well Known"
        MIME -> "Media Type"
        ABSOLUTE_URI -> "Absolute URI"
        EXTERNAL -> "NFC Forum External"
        UNKNOWN -> "Unknown"
        UNCHANGED -> "Unchanged"
        RESERVED -> "Reserved"
        else -> "非法取值"
    }
}
