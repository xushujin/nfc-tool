package com.nfctool.app.core.media

import com.nfctool.app.core.ndef.NdefMessage
import com.nfctool.app.core.ndef.NdefRecord
import com.nfctool.app.core.ndef.NdefRecords

/** 可写入标签的媒体类别。 */
enum class MediaKind(val label: String) {
    WEB("网页"),
    IMAGE("图片"),
    AUDIO("音频"),
    VIDEO("视频"),
}

/**
 * 媒体写入方案的计算结果。
 *
 * 之所以要在写入前算一遍，是因为 NDEF 标签容量普遍很小
 * （NTAG213 只有 144 字节可用），音频/视频不可能整段塞进去。
 */
sealed interface MediaPlan {

    /** 已生成可写入的记录集合。 */
    class Ready(
        val records: List<NdefRecord>,
        val description: String,
    ) : MediaPlan {
        val message: NdefMessage get() = NdefMessage(records)
        val requiredBytes: Int get() = message.encodedSize
    }

    /** 内容超出标签容量。 */
    class TooLarge(
        val requiredBytes: Int,
        val capacityBytes: Int,
    ) : MediaPlan

    /** 输入不合法。 */
    class Invalid(val reason: String) : MediaPlan
}

/**
 * 音频 / 视频 / 网页三种写入目标的记录构造。
 *
 * 策略：
 * - 网页：一律写 URI 记录，任何设备都能打开，可移植性最好。
 * - 音频 / 视频的网络地址：同样写 URI 记录（附一条标题文本记录）。
 * - 音频 / 视频的内嵌字节：写 MIME 记录；容量不够时先放弃标题记录，
 *   仍不够则明确报「超出容量」，不会截断数据。
 */
object MediaPayloads {

    /** 常见媒体扩展名到 MIME 类型的映射，用于内嵌写入时推断类型。 */
    private val MIME_BY_EXTENSION = mapOf(
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "oga" to "audio/ogg",
        "flac" to "audio/flac",
        "opus" to "audio/opus",
        "mp4" to "video/mp4",
        "m4v" to "video/mp4",
        "webm" to "video/webm",
        "mkv" to "video/x-matroska",
        "3gp" to "video/3gpp",
        "avi" to "video/x-msvideo",
    )

    /** 按文件名后缀推断 MIME 类型，未知时回退到 `application/octet-stream`。 */
    fun mimeTypeForFileName(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return MIME_BY_EXTENSION[extension] ?: "application/octet-stream"
    }

    /**
     * 补全用户输入的网址：没有协议头时补 `https://`。
     * 返回值可能仍是非法网址，交由 [webLink] 校验。
     */
    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        val hasScheme = trimmed.indexOf("://") > 0 ||
            trimmed.startsWith("tel:", ignoreCase = true) ||
            trimmed.startsWith("mailto:", ignoreCase = true)
        return if (hasScheme) trimmed else "https://$trimmed"
    }

    /** 网址合法性检查，返回问题描述；null 表示通过。 */
    fun urlProblem(url: String): String? {
        if (url.isBlank()) return "网址不能为空"
        val lower = url.lowercase()
        val supported = listOf("http://", "https://", "tel:", "mailto:")
        if (supported.none { lower.startsWith(it) }) {
            return "仅支持 http / https / tel / mailto 协议，当前为「${url.substringBefore("://", url)}」"
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            val schemeSeparator = url.indexOf("://")
            val host = url.substring(schemeSeparator + 3).substringBefore('/')
            if (host.isEmpty()) return "网址缺少主机名"
            if (host.contains(' ')) return "网址中不能包含空格"
        }
        return null
    }

    fun webLink(
        rawUrl: String,
        title: String? = null,
        languageCode: String = NdefRecords.DEFAULT_LANGUAGE,
    ): MediaPlan {
        val url = normalizeUrl(rawUrl)
        urlProblem(url)?.let { return MediaPlan.Invalid(it) }
        val records = ArrayList<NdefRecord>(2)
        if (!title.isNullOrBlank()) records.add(NdefRecords.text(title, languageCode))
        records.add(NdefRecords.uri(url))
        return MediaPlan.Ready(records, "网页链接 $url")
    }

    fun remoteMedia(
        rawUrl: String,
        kind: MediaKind,
        title: String? = null,
        languageCode: String = NdefRecords.DEFAULT_LANGUAGE,
    ): MediaPlan {
        val url = normalizeUrl(rawUrl)
        urlProblem(url)?.let { return MediaPlan.Invalid(it) }
        if (!url.lowercase().startsWith("http")) {
            return MediaPlan.Invalid("${kind.label}的网络地址必须是 http / https 链接")
        }
        val records = ArrayList<NdefRecord>(2)
        if (!title.isNullOrBlank()) records.add(NdefRecords.text(title, languageCode))
        records.add(NdefRecords.uri(url))
        return MediaPlan.Ready(records, "${kind.label}网络地址 $url")
    }

    /**
     * 内嵌媒体字节。
     *
     * @param capacityBytes 目标标签的 NDEF 可用容量；传 null 表示不校验容量。
     */
    fun embeddedMedia(
        kind: MediaKind,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        capacityBytes: Int?,
        languageCode: String = NdefRecords.DEFAULT_LANGUAGE,
    ): MediaPlan {
        if (bytes.isEmpty()) return MediaPlan.Invalid("${kind.label}内容为空")
        if (mimeType.isBlank()) return MediaPlan.Invalid("无法确定 ${kind.label} 的 MIME 类型")

        val mediaRecord = NdefRecords.mime(mimeType, bytes)
        val withTitle = listOf(NdefRecords.text(fileName, languageCode), mediaRecord)

        if (capacityBytes == null) {
            return MediaPlan.Ready(
                withTitle,
                "${kind.label}「$fileName」内嵌 ${bytes.size} 字节（$mimeType，未校验容量）",
            )
        }

        val withTitleSize = NdefMessage(withTitle).encodedSize
        if (withTitleSize <= capacityBytes) {
            return MediaPlan.Ready(
                withTitle,
                "${kind.label}「$fileName」内嵌 ${bytes.size} 字节（$mimeType）",
            )
        }

        val bareSize = NdefMessage(listOf(mediaRecord)).encodedSize
        if (bareSize <= capacityBytes) {
            return MediaPlan.Ready(
                listOf(mediaRecord),
                "${kind.label}「$fileName」内嵌 ${bytes.size} 字节（$mimeType，因容量不足已省略标题）",
            )
        }

        return MediaPlan.TooLarge(requiredBytes = bareSize, capacityBytes = capacityBytes)
    }
}
