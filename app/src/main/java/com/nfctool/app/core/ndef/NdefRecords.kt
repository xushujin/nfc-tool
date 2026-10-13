package com.nfctool.app.core.ndef

import com.nfctool.app.core.bytes.Bytes

/**
 * NDEF 记录的构造与解析。
 *
 * 只覆盖本应用用到的记录类型，全部按 NFC Forum 规范手工编码，
 * 不依赖任何第三方 NDEF 库。
 */
object NdefRecords {

    const val TYPE_TEXT = "T"
    const val TYPE_URI = "U"
    const val TYPE_SMART_POSTER = "Sp"
    const val TYPE_ANDROID_APP = "android.com:pkg"
    const val TYPE_HANDOVER_SELECT = "Hs"
    const val TYPE_HANDOVER_REQUEST = "Hr"

    /** Wi-Fi Simple Configuration 的 MIME 类型，供写 WiFi 凭据使用。 */
    const val MIME_WIFI_WSC = "application/vnd.wfa.wsc"

    const val DEFAULT_LANGUAGE = "en"

    private val TYPE_BYTES_TEXT = Bytes.toAscii(TYPE_TEXT)
    private val TYPE_BYTES_URI = Bytes.toAscii(TYPE_URI)
    private val TYPE_BYTES_SMART_POSTER = Bytes.toAscii(TYPE_SMART_POSTER)
    private val TYPE_BYTES_ANDROID_APP = Bytes.toAscii(TYPE_ANDROID_APP)

    private val NO_ID = ByteArray(0)
    private val NO_PAYLOAD = ByteArray(0)

    // ------------------------------------------------------------------ 构造

    /** 文本记录：TNF=Well Known，类型 'T'。 */
    fun text(text: String, languageCode: String = DEFAULT_LANGUAGE): NdefRecord {
        require(text.isNotEmpty()) { "文本内容不能为空" }
        require(languageCode.isNotEmpty() && languageCode.length <= 63) {
            "语言代码长度必须在 1..63 之间，实际为 ${languageCode.length}"
        }
        val language = Bytes.toAscii(languageCode)
        // 状态字节 bit7=0 表示 UTF-8；低 6 位为语言代码长度
        val status = byteArrayOf(language.size.toByte())
        val payload = Bytes.concat(status, language, text.toByteArray(Charsets.UTF_8))
        return NdefRecord(NdefTnf.WELL_KNOWN, TYPE_BYTES_TEXT, NO_ID, payload)
    }

    /** URI 记录：TNF=Well Known，类型 'U'，负载使用前缀缩写表压缩。 */
    fun uri(uri: String): NdefRecord {
        require(uri.isNotEmpty()) { "URI 不能为空" }
        val (code, remainder) = UriPrefix.compress(uri)
        val payload = Bytes.concat(
            byteArrayOf(code.toByte()),
            remainder.toByteArray(Charsets.UTF_8),
        )
        return NdefRecord(NdefTnf.WELL_KNOWN, TYPE_BYTES_URI, NO_ID, payload)
    }

    /** MIME 记录：TNF=Media Type，类型为 MIME 字符串。 */
    fun mime(mimeType: String, payload: ByteArray): NdefRecord {
        require(mimeType.isNotEmpty()) { "MIME 类型不能为空" }
        return NdefRecord(NdefTnf.MIME, Bytes.toAscii(mimeType), NO_ID, payload)
    }

    /** 外部类型记录：TNF=External，类型为 `域名:类型名`。 */
    fun external(domain: String, typeName: String, payload: ByteArray): NdefRecord {
        require(domain.isNotEmpty() && typeName.isNotEmpty()) { "外部类型需要同时给出域名与类型名" }
        return NdefRecord(NdefTnf.EXTERNAL, Bytes.toAscii("$domain:$typeName"), NO_ID, payload)
    }

    /** 绝对 URI 记录：TNF=Absolute URI，类型字段即完整 URI，无负载。 */
    fun absoluteUri(uri: String): NdefRecord {
        require(uri.isNotEmpty()) { "URI 不能为空" }
        return NdefRecord(NdefTnf.ABSOLUTE_URI, uri.toByteArray(Charsets.UTF_8), NO_ID, NO_PAYLOAD)
    }

    /** Android 应用记录（AAR）：让标签被 Android 设备读取时唤起指定应用。 */
    fun androidApplication(packageName: String): NdefRecord {
        require(packageName.isNotEmpty()) { "包名不能为空" }
        return NdefRecord(
            NdefTnf.EXTERNAL,
            TYPE_BYTES_ANDROID_APP,
            NO_ID,
            packageName.toByteArray(Charsets.UTF_8),
        )
    }

    /** 智能海报：负载是一条内嵌 NDEF 消息，包含 URI 记录与可选标题记录。 */
    fun smartPoster(
        uri: String,
        title: String? = null,
        languageCode: String = DEFAULT_LANGUAGE,
    ): NdefRecord {
        val inner = ArrayList<NdefRecord>(2)
        inner.add(uri(uri))
        if (!title.isNullOrEmpty()) inner.add(text(title, languageCode))
        val payload = NdefCodec.encode(NdefMessage(inner))
        return NdefRecord(NdefTnf.WELL_KNOWN, TYPE_BYTES_SMART_POSTER, NO_ID, payload)
    }

    // ------------------------------------------------------------------ 解析

    fun isText(record: NdefRecord): Boolean =
        record.tnf == NdefTnf.WELL_KNOWN && record.type.contentEquals(TYPE_BYTES_TEXT)

    fun isUri(record: NdefRecord): Boolean =
        record.tnf == NdefTnf.WELL_KNOWN && record.type.contentEquals(TYPE_BYTES_URI)

    fun isSmartPoster(record: NdefRecord): Boolean =
        record.tnf == NdefTnf.WELL_KNOWN && record.type.contentEquals(TYPE_BYTES_SMART_POSTER)

    fun isAndroidApplication(record: NdefRecord): Boolean =
        record.tnf == NdefTnf.EXTERNAL && record.type.contentEquals(TYPE_BYTES_ANDROID_APP)

    fun isWifiConfiguration(record: NdefRecord): Boolean =
        record.tnf == NdefTnf.MIME && record.typeAscii.equals(MIME_WIFI_WSC, ignoreCase = true)

    fun parseText(record: NdefRecord): TextPayload? {
        if (!isText(record)) return null
        val payload = record.payload
        if (payload.isEmpty()) throw NdefFormatException("Text 记录负载为空")
        val status = payload[0].toInt() and 0xFF
        val languageLength = status and 0x3F
        val isUtf16 = status and 0x80 != 0
        if (payload.size < 1 + languageLength) {
            throw NdefFormatException("Text 记录的语言代码长度（$languageLength）超出负载范围")
        }
        val language = Bytes.ascii(payload.copyOfRange(1, 1 + languageLength))
        val textBytes = payload.copyOfRange(1 + languageLength, payload.size)
        val text = if (isUtf16) {
            String(textBytes, Charsets.UTF_16BE)
        } else {
            String(textBytes, Charsets.UTF_8)
        }
        return TextPayload(text = text, languageCode = language, isUtf16 = isUtf16)
    }

    fun parseUri(record: NdefRecord): String? {
        if (!isUri(record)) return null
        val payload = record.payload
        if (payload.isEmpty()) throw NdefFormatException("URI 记录负载为空")
        val code = payload[0].toInt() and 0xFF
        val remainder = String(payload, 1, payload.size - 1, Charsets.UTF_8)
        return UriPrefix.expand(code, remainder)
    }

    fun parseSmartPoster(record: NdefRecord): SmartPosterPayload? {
        if (!isSmartPoster(record)) return null
        val inner = NdefCodec.decode(record.payload)
        var uri: String? = null
        var title: String? = null
        for (innerRecord in inner.records) {
            if (uri == null) uri = parseUri(innerRecord)
            if (title == null) title = parseText(innerRecord)?.text
        }
        return SmartPosterPayload(uri = uri, title = title)
    }

    /** 智能海报负载。 */
    data class SmartPosterPayload(val uri: String?, val title: String?)

    /** 文本记录负载。 */
    data class TextPayload(val text: String, val languageCode: String, val isUtf16: Boolean)
}
