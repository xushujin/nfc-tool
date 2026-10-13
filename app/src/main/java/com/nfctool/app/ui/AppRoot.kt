package com.nfctool.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nfctool.app.ui.component.NeuBannerLevel
import com.nfctool.app.ui.component.NeuBottomNav
import com.nfctool.app.ui.component.NeuDialog
import com.nfctool.app.ui.component.NeuIconKind
import com.nfctool.app.ui.component.NeuNavItem
import com.nfctool.app.ui.screen.AboutScreen
import com.nfctool.app.ui.screen.CopyScreen
import com.nfctool.app.ui.screen.HistoryScreen
import com.nfctool.app.ui.screen.ReadScreen
import com.nfctool.app.ui.screen.SettingsScreen
import com.nfctool.app.ui.screen.WriteScreen
import com.nfctool.app.ui.theme.NeuTheme

private val NAV_ITEMS = listOf(
    NeuNavItem(NeuIconKind.Read, "读卡"),
    NeuNavItem(NeuIconKind.Copy, "复制"),
    NeuNavItem(NeuIconKind.Wifi, "写入"),
    NeuNavItem(NeuIconKind.History, "历史"),
    NeuNavItem(NeuIconKind.Settings, "设置"),
)

/** 应用主界面：五个主页面 + 关于页，以及全局提示条。 */
@Composable
fun AppRoot(
    state: NfcToolUiState,
    viewModel: NfcToolViewModel,
    modifier: Modifier = Modifier,
) {
    val palette = NeuTheme.palette
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                if (showAbout) {
                    AboutScreen(onBack = { showAbout = false })
                } else {
                    when (tabIndex) {
                        0 -> ReadScreen(
                            state = state,
                            onToggleMifareDump = viewModel::setReadMifareDump,
                        )

                        1 -> CopyScreen(
                            state = state,
                            onCopyNdef = viewModel::requestCopyCurrentNdef,
                            onCopyMifare = viewModel::requestCopyCurrentMifare,
                            onCancelPending = viewModel::cancelPending,
                        )

                        2 -> WriteScreen(
                            state = state,
                            onWriteWifi = viewModel::requestWifiWrite,
                            onWriteText = viewModel::requestWriteText,
                            onWriteImage = viewModel::requestWriteImage,
                            onWriteApp = viewModel::requestWriteApp,
                            onWriteMedia = viewModel::requestMediaWrite,
                            onStartWifiRead = viewModel::startWifiRead,
                            onWifiNotice = viewModel::setWifiReadNotice,
                            onCancelPending = viewModel::cancelPending,
                        )

                        3 -> HistoryScreen(
                            entries = state.history,
                            onUse = viewModel::useHistoryEntry,
                            onDelete = viewModel::deleteHistory,
                            onClear = viewModel::clearHistory,
                        )

                        else -> SettingsScreen(
                            settings = state.settings,
                            onSelectTheme = viewModel::setThemeId,
                            onToggleVibrate = viewModel::setVibrateOnScan,
                            onToggleAutoSave = viewModel::setAutoSaveHistory,
                            onLanguageChange = viewModel::setNdefLanguage,
                            onOpenAbout = { showAbout = true },
                        )
                    }
                }
            }

            if (!showAbout) {
                NeuBottomNav(
                    items = NAV_ITEMS,
                    selectedIndex = tabIndex,
                    onSelect = { tabIndex = it },
                )
            }
        }

        // 统一的全局结果弹窗：读卡 / 写卡 / 各类操作的结果都走这里，不再用底部横幅
        val status = state.status
        if (status != null) {
            NeuDialog(
                title = status.level.title(),
                message = status.text,
                level = status.level.toBannerLevel(),
                onDismiss = viewModel::dismissStatus,
            )
        }
    }
}

private fun StatusLevel.toBannerLevel(): NeuBannerLevel = when (this) {
    StatusLevel.INFO -> NeuBannerLevel.INFO
    StatusLevel.SUCCESS -> NeuBannerLevel.SUCCESS
    StatusLevel.WARNING -> NeuBannerLevel.WARNING
    StatusLevel.ERROR -> NeuBannerLevel.ERROR
}

private fun StatusLevel.title(): String = when (this) {
    StatusLevel.INFO -> "提示"
    StatusLevel.SUCCESS -> "成功"
    StatusLevel.WARNING -> "注意"
    StatusLevel.ERROR -> "失败"
}
