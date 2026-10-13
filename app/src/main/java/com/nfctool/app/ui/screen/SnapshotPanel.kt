package com.nfctool.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfctool.app.core.model.RecordSummary
import com.nfctool.app.core.model.RecordSummaries
import com.nfctool.app.core.model.TagSnapshot
import com.nfctool.app.ui.component.NeuBanner
import com.nfctool.app.ui.component.NeuBannerLevel
import com.nfctool.app.ui.component.NeuIcon
import com.nfctool.app.ui.component.NeuIconKind
import com.nfctool.app.ui.component.NeuKeyValueRow
import com.nfctool.app.ui.component.NeuSectionTitle
import com.nfctool.app.ui.component.NeuSurface
import com.nfctool.app.ui.theme.NeuDimens
import com.nfctool.app.ui.theme.NeuTheme

/** 标签快照面板，读卡页与复制页共用。 */
@Composable
fun SnapshotPanel(
    snapshot: TagSnapshot,
    modifier: Modifier = Modifier,
) {
    val palette = NeuTheme.palette

    Column(modifier = modifier.fillMaxWidth()) {
        NeuSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeuIcon(
                        kind = NeuIconKind.Tag,
                        modifier = Modifier.size(20.dp),
                        tint = palette.accent,
                    )
                    Spacer(modifier = Modifier.width(NeuDimens.gapSmall))
                    Text(
                        text = "标签信息",
                        color = palette.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(modifier = Modifier.height(NeuDimens.gapSmall))
                NeuKeyValueRow("UID", snapshot.uidHex.ifEmpty { "未提供" })
                NeuKeyValueRow("技术类型", snapshot.technologies.joinToString(" / ").ifEmpty { "无" })
                snapshot.ndef?.let { ndef ->
                    NeuKeyValueRow("NDEF 容量", "${ndef.maxSizeBytes} 字节")
                    NeuKeyValueRow("可写", if (ndef.writable) "是" else "否（只读）")
                    NeuKeyValueRow("可锁定", if (ndef.canMakeReadOnly) "是" else "否")
                    NeuKeyValueRow("已用", ndef.message?.let { "${it.encodedSize} 字节" } ?: "空标签")
                }
                snapshot.mifareClassic?.let { classic ->
                    NeuKeyValueRow("MIFARE", classic.size.label)
                    NeuKeyValueRow(
                        "可读扇区",
                        if (classic.readableSectors.isEmpty()) {
                            "无"
                        } else {
                            "${classic.readableSectors.size} / ${classic.size.sectorCount}"
                        },
                    )
                    NeuKeyValueRow("整卡转储", if (classic.isComplete) "已完成" else "不完整")
                    NeuKeyValueRow(
                        "转储内 NDEF",
                        when (val embedded = classic.embeddedNdef) {
                            null -> "未找到"
                            else -> "${embedded.records.size} 条记录（尽力解析）"
                        },
                    )
                }
            }
        }

        if (snapshot.warnings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(NeuDimens.gapMedium))
            Column(verticalArrangement = Arrangement.spacedBy(NeuDimens.gapSmall)) {
                snapshot.warnings.forEach { warning ->
                    NeuBanner(
                        text = warning,
                        level = NeuBannerLevel.WARNING,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        val summaries = snapshot.ndef?.message?.let { RecordSummaries.of(it) }.orEmpty()
        NeuSectionTitle(text = "NDEF 记录（${summaries.size} 条）")
        if (summaries.isEmpty()) {
            NeuSurface(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "该标签没有 NDEF 数据，可能是空标签或仅包含厂商私有数据。",
                    color = palette.onBackgroundMuted,
                    fontSize = 14.sp,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(NeuDimens.gapMedium)) {
                summaries.forEach { summary ->
                    RecordCard(summary)
                }
            }
        }
    }
}

@Composable
private fun RecordCard(summary: RecordSummary) {
    val palette = NeuTheme.palette
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            NeuIcon(
                kind = iconFor(summary.kind.name),
                modifier = Modifier.size(18.dp),
                tint = palette.accent,
            )
            Spacer(modifier = Modifier.width(NeuDimens.gapSmall))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.title,
                    color = palette.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary.detail,
                    color = palette.onBackgroundMuted,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "TNF ${summary.tnf} · 类型 ${summary.typeAscii.ifEmpty { "（空）" }} · " +
                        "${summary.payloadBytes} 字节",
                    color = palette.onBackgroundMuted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private fun iconFor(kindName: String): NeuIconKind = when (kindName) {
    "WEB_LINK" -> NeuIconKind.Media
    "WIFI" -> NeuIconKind.Wifi
    "TEXT" -> NeuIconKind.Info
    "SMART_POSTER" -> NeuIconKind.Media
    "ANDROID_APP" -> NeuIconKind.Settings
    "MEDIA" -> NeuIconKind.Media
    "LOCKED" -> NeuIconKind.Lock
    else -> NeuIconKind.Tag
}
