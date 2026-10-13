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
import com.nfctool.app.ui.NfcToolUiState
import com.nfctool.app.ui.component.NeuBanner
import com.nfctool.app.ui.component.NeuBannerLevel
import com.nfctool.app.ui.component.NeuIcon
import com.nfctool.app.ui.component.NeuIconKind
import com.nfctool.app.ui.component.NeuScreen
import com.nfctool.app.ui.component.NeuSectionTitle
import com.nfctool.app.ui.component.NeuSurface
import com.nfctool.app.ui.component.NeuSwitch
import com.nfctool.app.ui.theme.NeuDimens
import com.nfctool.app.ui.theme.NeuTheme

/** 读卡页：展示 NFC 状态与最近一次读到的标签内容。 */
@Composable
fun ReadScreen(
    state: NfcToolUiState,
    onToggleMifareDump: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = NeuTheme.palette

    NeuScreen(title = "读卡", modifier = modifier) {
        when {
            !state.nfcSupported -> NeuBanner(
                text = "本机没有 NFC 硬件，读卡与写卡功能不可用。",
                level = NeuBannerLevel.ERROR,
                modifier = Modifier.fillMaxWidth(),
            )

            !state.nfcEnabled -> NeuBanner(
                text = "NFC 已关闭。请到系统「设置 → 已连接的设备 → 连接偏好设置」中开启 NFC。",
                level = NeuBannerLevel.WARNING,
                modifier = Modifier.fillMaxWidth(),
            )

            state.pending != null -> NeuBanner(
                text = state.pending.prompt,
                level = NeuBannerLevel.INFO,
                modifier = Modifier.fillMaxWidth(),
            )

            else -> NeuBanner(
                text = "把标签贴近手机背面的 NFC 感应区即可读取。",
                level = NeuBannerLevel.INFO,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(NeuDimens.gapMedium))

        NeuSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeuIcon(
                    kind = NeuIconKind.Read,
                    modifier = Modifier.size(18.dp),
                    tint = palette.accent,
                )
                Spacer(modifier = Modifier.width(NeuDimens.gapSmall))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "读取 MIFARE 整卡转储",
                        color = palette.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "开启后会逐个扇区尝试常见密钥，读卡耗时变长。" +
                            "密钥未知的扇区无法读取，也就无法整卡复制。",
                        color = palette.onBackgroundMuted,
                        fontSize = 12.sp,
                    )
                }
                Spacer(modifier = Modifier.width(NeuDimens.gapSmall))
                NeuSwitch(
                    checked = state.readMifareDump,
                    onCheckedChange = onToggleMifareDump,
                )
            }
        }

        val snapshot = state.snapshot
        if (snapshot == null) {
            NeuSectionTitle(text = "标签内容")
            NeuSurface(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "还没有读到标签。",
                        color = palette.onBackground,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = "贴上标签后，这里会显示 UID、技术类型、NDEF 记录与容量信息。",
                        color = palette.onBackgroundMuted,
                        fontSize = 13.sp,
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(NeuDimens.gapLarge))
            SnapshotPanel(snapshot = snapshot)
        }

        Spacer(modifier = Modifier.height(NeuDimens.gapLarge))
    }
}
