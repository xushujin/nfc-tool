package com.nfctool.app.core.bytes

/** 十六进制编解码。解码时容忍空格、冒号、连字符与 `0x` 前缀，便于直接粘贴标签转储。 */
object Hex {

    private const val UPPER = "0123456789ABCDEF"

    fun encode(bytes: ByteArray, separator: String = ""): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder(bytes.size * (2 + separator.length))
        for (index in bytes.indices) {
            if (index > 0 && separator.isNotEmpty()) sb.append(separator)
            val value = bytes[index].toInt() and 0xFF
            sb.append(UPPER[value ushr 4]).append(UPPER[value and 0x0F])
        }
        return sb.toString()
    }

    /** 解析十六进制文本。长度非偶数或出现非法字符时抛 [IllegalArgumentException]。 */
    fun decode(text: String): ByteArray {
        val cleaned = buildString(text.length) {
            for (ch in text) {
                when {
                    ch.isWhitespace() || ch == ':' || ch == '-' || ch == '_' -> Unit
                    else -> append(ch)
                }
            }
        }.removePrefix("0x").removePrefix("0X")

        require(cleaned.length % 2 == 0) {
            "十六进制字符个数必须为偶数，实际为 ${cleaned.length}"
        }
        val out = ByteArray(cleaned.length / 2)
        for (index in out.indices) {
            val high = Character.digit(cleaned[index * 2], 16)
            val low = Character.digit(cleaned[index * 2 + 1], 16)
            require(high >= 0 && low >= 0) { "第 ${index * 2} 个字符不是合法的十六进制字符" }
            out[index] = ((high shl 4) or low).toByte()
        }
        return out
    }

    fun decodeOrNull(text: String): ByteArray? =
        try {
            decode(text)
        } catch (_: IllegalArgumentException) {
            null
        }
}
