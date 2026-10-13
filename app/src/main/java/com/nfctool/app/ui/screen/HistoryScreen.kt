package com.nfctool.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfctool.app.data.db.HistoryEntry
import com.nfctool.app.ui.component.NeuButton
import com.nfctool.app.ui.component.NeuButtonContent
import com.nfctool.app.ui.component.NeuEmptyState
import com.nfctool.app.ui.component.NeuIcon
import com.nfctool.app.ui.component.NeuIconButton
import com.nfctool.app.ui.component.NeuIconKind
import com.nfctool.app.ui.component.NeuScreen
import com.nfctool.app.ui.component.NeuSectionTitle
import com.nfctool.app.ui.component.NeuSurface
import com.nfctool.app.ui.format.TimeFormat
import com.nfctool.app.ui.theme.NeuDimens
import com.nfctool.app.ui.theme.NeuTheme

/**
 * 历史页。
 *
 * 摘要用于列表展示；保存了原文的记录可以直接「用这条写入新卡」——
 * 有 NDEF 的写 NDEF，门禁卡一类没有 NDEF 的走 MIFARE 整卡回写。
 */
@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    onUse: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = NeuTheme.palette

    NeuScreen(
        title = "历史",
        modifier = modifier,
        actions = {
            if (entries.isNotEmpty()) {
                NeuIconButton(
                    kind = NeuIconKind.Delete,
                    onClick = onClear,
                    contentDescription = "清空历史",
                )
            }
        },
    ) {
        if (entries.isEmpty()) {
            NeuEmptyState(
                icon = NeuIconKind.History,
                title = "还没有读卡记录",
                description = "读卡成功后会自动记录摘要，可在设置里关闭。",
            )
            return@NeuScreen
        }

        NeuSectionTitle(text = "共 ${entries.size} 条")
        Column(verticalArrangement = Arrangement.spacedBy(NeuDimens.gapMedium)) {
            entries.forEach { entry ->
                NeuSurface(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeuIcon(
                            kind = NeuIconKind.Tag,
                            modifier = Modifier.size(18.dp),
                            tint = palette.accent,
                        )
                        Spacer(modifier = Modifier.width(NeuDimens.gapSmall))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.uidHex.ifEmpty { "无 UID" },
                                color = palette.onBackground,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = entry.recordKinds,
                                color = palette.onBackgroundMuted,
                                fontSize = 13.sp,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${entry.technologies.ifEmpty { "未知技术" }} · " +
                                    "${entry.recordCount} 条记录 · ${entry.ndefBytes} 字节 · " +
                                    TimeFormat.shortDateTime(entry.createdAtMillis),
                                color = palette.onBackgroundMuted,
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(modifier = Modifier.width(NeuDimens.gapSmall))
                        if (entry.hasContent) {
                            NeuIconButton(
                                kind = NeuIconKind.Copy,
                                onClick = { onUse(entry.id) },
                                contentDescription = "用这条记录写入新卡",
                            )
                        }
                        NeuIconButton(
                            kind = NeuIconKind.Close,
                            onClick = { onDelete(entry.id) },
                            contentDescription = "删除该条记录",
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(NeuDimens.gapLarge))
        NeuButton(
            onClick = onClear,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NeuButtonContent(icon = NeuIconKind.Delete, text = "清空全部历史")
        }
        Spacer(modifier = Modifier.height(NeuDimens.gapLarge))
    }
}
