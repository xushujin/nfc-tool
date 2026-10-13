package com.nfctool.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfctool.app.data.AppSettings
import com.nfctool.app.ui.component.NeuBanner
import com.nfctool.app.ui.component.NeuBannerLevel
import com.nfctool.app.ui.component.NeuButton
import com.nfctool.app.ui.component.NeuButtonContent
import com.nfctool.app.ui.component.NeuElevation
import com.nfctool.app.ui.component.NeuIcon
import com.nfctool.app.ui.component.NeuIconKind
import com.nfctool.app.ui.component.NeuScreen
import com.nfctool.app.ui.component.NeuSectionTitle
import com.nfctool.app.ui.component.NeuSurface
import com.nfctool.app.ui.component.NeuSwitch
import com.nfctool.app.ui.component.NeuTextField
import com.nfctool.app.ui.theme.NeuDimens
import com.nfctool.app.ui.theme.NeuPalette
import com.nfctool.app.ui.theme.NeuPalettes
import com.nfctool.app.ui.theme.NeuTheme

/**
 * 设置页。
 *
 * 每个设置项只有一处实现：全部经由 [com.nfctool.app.data.SettingsRepository] 读写，
 * 主题切换通过同一份状态驱动整个界面，不存在"某个页面改了主题、另一个页面没变"的情况。
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSelectTheme: (String) -> Unit,
    onToggleVibrate: (Boolean) -> Unit,
    onToggleAutoSave: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = NeuTheme.palette
    var languageDraft by remember(settings.ndefLanguage) { mutableStateOf(settings.ndefLanguage) }

    NeuScreen(title = "设置", modifier = modifier) {
        NeuSectionTitle(text = "配色主题（${NeuPalettes.ALL.size} 套）")
        Column(verticalArrangement = Arrangement.spacedBy(NeuDimens.gapSmall)) {
            NeuPalettes.ALL.forEach { candidate ->
                ThemeRow(
                    candidate = candidate,
                    selected = candidate.id == settings.themeId,
                    onClick = { onSelectTheme(candidate.id) },
                )
            }
        }

        NeuSectionTitle(text = "读卡行为")
        NeuSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                SettingSwitchRow(
                    title = "读卡振动",
                    description = "读到标签时给出振动反馈",
                    checked = settings.vibrateOnScan,
                    onCheckedChange = onToggleVibrate,
                )
                Spacer(modifier = Modifier.height(NeuDimens.gapMedium))
                SettingSwitchRow(
                    title = "自动记录历史",
                    description = "只记录 UID、技术类型与记录类别等摘要，不记录标签内容",
                    checked = settings.autoSaveHistory,
                    onCheckedChange = onToggleAutoSave,
                )
            }
        }

        NeuSectionTitle(text = "NDEF 文本记录语言")
        NeuTextField(
            value = languageDraft,
            onValueChange = {
                languageDraft = it
                onLanguageChange(it)
            },
            label = "语言代码",
            placeholder = "例如 zh 或 en",
            supportingText = "写入标题等文本记录时使用，取 RFC 5646 语言标签",
            isError = languageDraft.isBlank() || languageDraft.length > 63,
            modifier = Modifier.fillMaxWidth(),
        )

        if (languageDraft.isBlank() || languageDraft.length > 63) {
            Spacer(modifier = Modifier.height(NeuDimens.gapSmall))
            NeuBanner(
                text = "语言代码长度必须在 1..63 之间，当前为 ${languageDraft.length}。",
                level = NeuBannerLevel.WARNING,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        NeuSectionTitle(text = "其他")
        NeuButton(
            onClick = onOpenAbout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NeuButtonContent(icon = NeuIconKind.Info, text = "关于本应用")
        }

        Spacer(modifier = Modifier.height(NeuDimens.gapLarge))
        Text(
            text = "当前主题：${palette.displayName}（${if (palette.isDark) "深色" else "浅色"}）",
            color = palette.onBackgroundMuted,
            fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.height(NeuDimens.gapLarge))
    }
}

@Composable
private fun ThemeRow(
    candidate: NeuPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val current = NeuTheme.palette
    NeuSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        elevation = if (selected) NeuElevation.RAISED else NeuElevation.FLAT,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 色卡用候选主题自己的颜色绘制，与当前主题无关
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(color = candidate.background, shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(color = candidate.accent, shape = CircleShape),
                )
            }
            Spacer(modifier = Modifier.width(NeuDimens.gapMedium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.displayName,
                    color = current.onBackground,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (candidate.isDark) "深色主题" else "浅色主题",
                    color = current.onBackgroundMuted,
                    fontSize = 12.sp,
                )
            }
            if (selected) {
                NeuIcon(
                    kind = NeuIconKind.Check,
                    modifier = Modifier.size(18.dp),
                    tint = current.accent,
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = NeuTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = palette.onBackground, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = description, color = palette.onBackgroundMuted, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(NeuDimens.gapMedium))
        NeuSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
