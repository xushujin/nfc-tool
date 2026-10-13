package com.nfctool.app.core.ndef

import com.nfctool.app.core.bytes.Hex

/**
 * NDEF URI 记录的前缀缩写表。
 * 依据：NFC Forum NDEF 1.0 规范 3.2.6.2 节（URI Identifier Code）。
 */
object UriPrefix {

    private val TABLE: List<String> = listOf(
        "",                            // 0x00 无缩写
        "http://www.",                 // 0x01
        "https://www.",                // 0x02
        "http://",                     // 0x03
        "https://",                    // 0x04
        "tel:",                        // 0x05
        "mailto:",                     // 0x06
        "ftp://anonymous:anonymous@",  // 0x07
        "ftp://ftp.",                  // 0x08
        "ftps://",                     // 0x09
        "sftp://",                     // 0x0A
        "smb://",                      // 0x0B
        "nfs://",                      // 0x0C
        "ftp://",                      // 0x0D
        "dav://",                      // 0x0E
        "news:",                       // 0x0F
        "telnet://",                   // 0x10
        "imap:",                       // 0x11
        "rtsp://",                     // 0x12
        "urn:",                        // 0x13
        "pop:",                        // 0x14
        "sip:",                        // 0x15
        "sips:",                       // 0x16
        "tftp:",                       // 0x17
        "btspp://",                    // 0x18
        "btl2cap://",                  // 0x19
        "btgoep://",                   // 0x1A
        "tcpobex://",                  // 0x1B
        "irdaobex://",                 // 0x1C
        "file://",                     // 0x1D
        "urn:epc:id:",                 // 0x1E
        "urn:epc:tag:",                // 0x1F
        "urn:epc:pat:",                // 0x20
        "urn:epc:raw:",                // 0x21
        "urn:epc:",                    // 0x22
        "urn:nfc:",                    // 0x23
    )

    val size: Int get() = TABLE.size

    fun prefixOf(code: Int): String = TABLE.getOrNull(code)
        ?: throw NdefFormatException("URI 前缀代码 0x${Hex.encode(byteArrayOf(code.toByte()))} 超出缩写表范围")

    /**
     * 选择能匹配的最长前缀做压缩。
     * 返回值为「前缀代码 to 剩余部分」；无任何前缀匹配时返回 `(0x00, 完整 URI)`。
     */
    fun compress(uri: String): Pair<Int, String> {
        var bestCode = 0
        var bestPrefix = ""
        for (code in TABLE.indices) {
            val prefix = TABLE[code]
            if (prefix.isNotEmpty() && uri.startsWith(prefix) && prefix.length > bestPrefix.length) {
                bestCode = code
                bestPrefix = prefix
            }
        }
        return bestCode to uri.substring(bestPrefix.length)
    }

    fun expand(code: Int, remainder: String): String = prefixOf(code) + remainder
}
