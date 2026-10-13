package com.nfctool.app.core.model

import com.nfctool.app.core.ndef.NdefRecord
import com.nfctool.app.core.ndef.NdefRecords
import com.nfctool.app.core.ndef.NdefTnf
import com.nfctool.app.core.wsc.WscCodec

/** 记录类别，用于界面分类展示与历史统计。 */
enum class RecordKind(val label: String) {
    EMPTY("空记录"),
    TEXT("文本"),
    WEB_LINK("网页链接"),
    WIFI("WiFi 凭据"),
    SMART_POSTER("智能海报"),
    ANDROID_APP("Android 应用"),
    MEDIA("媒体数据"),
    EXTERNAL("外部类型"),
    UNKNOWN("未知类型"),
}

/** 一条 NDEF 记录的可读摘要。 */
data class RecordSummary(
    val kind: RecordKind,
    val title: String,
    val detail: String,
    val payloadBytes: Int,
    val tnf: Int,
    val typeAscii: String,
)

/**
 * 把 NDEF 记录翻译成界面可直接展示的摘要。
 *
 * 放在核心层是为了可单测：界面只负责渲染，不做任何解析判断。
 */
object RecordSummaries {

    private const val DETAIL_MAX_CHARS = 160

    fun of(record: NdefRecord): RecordSummary {
        val base = { kind: RecordKind, title: String, detail: String ->
            RecordSummary(
                kind = kind,
                title = title,
                detail = detail,
                payloadBytes = record.payload.size,
                tnf = record.tnf,
                typeAscii = record.typeAscii,
            )
        }

        if (record.tnf == NdefTnf.EMPTY) {
            return base(RecordKind.EMPTY, "空记录", "占位记录，不含数据")
        }

        NdefRecords.parseText(record)?.let { text ->
            val encoding = if (text.isUtf16) "UTF-16" else "UTF-8"
            return base(
                RecordKind.TEXT,
                "文本（${text.languageCode}，$encoding）",
                truncate(text.text),
            )
        }

        NdefRecords.parseUri(record)?.let { uri ->
            return base(RecordKind.WEB_LINK, "网页链接", truncate(uri))
        }

        if (NdefRecords.isWifiConfiguration(record)) {
            val credential = WscCodec.decode(record.payload)
            return if (credential == null) {
                base(RecordKind.WIFI, "WiFi 凭据", "负载结构无法解析（${record.payload.size} 字节）")
            } else {
                base(
                    RecordKind.WIFI,
                    "WiFi 凭据",
                    "名称「${credential.ssid}」，${credential.authType.label} / " +
                        "${credential.encryptionType.label}，密码" +
                        if (credential.networkKey.isEmpty()) "未包含" else "已包含",
                )
            }
        }

        NdefRecords.parseSmartPoster(record)?.let { poster ->
            val title = poster.title?.let { "标题「$it」，" } ?: ""
            return base(
                RecordKind.SMART_POSTER,
                "智能海报",
                truncate("${title}目标 ${poster.uri ?: "未包含 URI"}"),
            )
        }

        if (NdefRecords.isAndroidApplication(record)) {
            return base(
                RecordKind.ANDROID_APP,
                "Android 应用",
                "包名 ${record.payload.toString(Charsets.UTF_8)}",
            )
        }

        if (record.tnf == NdefTnf.ABSOLUTE_URI) {
            return base(
                RecordKind.WEB_LINK,
                "绝对 URI",
                truncate(record.typeAscii),
            )
        }

        return when (record.tnf) {
            NdefTnf.MIME -> base(
                RecordKind.MEDIA,
                "媒体数据（${record.typeAscii}）",
                "${record.payload.size} 字节负载",
            )

            NdefTnf.EXTERNAL -> base(
                RecordKind.EXTERNAL,
                "外部类型（${record.typeAscii}）",
                "${record.payload.size} 字节负载",
            )

            else -> base(
                RecordKind.UNKNOWN,
                "未知记录（${NdefTnf.name(record.tnf)}）",
                "类型 ${record.typeAscii.ifEmpty { "（空）" }}，${record.payload.size} 字节负载",
            )
        }
    }

    fun of(message: com.nfctool.app.core.ndef.NdefMessage): List<RecordSummary> =
        message.records.map(::of)

    /** 历史列表用的类别串，例如 `网页链接 + WiFi 凭据`。 */
    fun kindsLabel(summaries: List<RecordSummary>): String =
        summaries.map { it.kind.label }.distinct().joinToString(" + ")

    private fun truncate(text: String): String =
        if (text.length <= DETAIL_MAX_CHARS) text else text.take(DETAIL_MAX_CHARS) + "…"
}
