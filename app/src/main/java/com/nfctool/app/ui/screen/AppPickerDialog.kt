package com.nfctool.app.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nfctool.app.ui.InstalledApp
import com.nfctool.app.ui.component.NeuButton
import com.nfctool.app.ui.component.NeuButtonContent
import com.nfctool.app.ui.component.NeuElevation
import com.nfctool.app.ui.component.NeuIcon
import com.nfctool.app.ui.component.NeuIconKind
import com.nfctool.app.ui.component.NeuSurface
import com.nfctool.app.ui.component.NeuTextField
import com.nfctool.app.ui.theme.NeuDimens
import com.nfctool.app.ui.theme.NeuTheme

/**
 * 「写入应用」的包名选择器：列出手机上可启动的应用，可按名称或包名搜索。
 *
 * 只列有启动图标的应用（见 `InstalledAppsLoader`）；
 * 没有图标的组件仍可在写入页手动输入包名。
 */
@Composable
internal fun AppPickerDialog(
    apps: List<InstalledApp>,
    onSelect: (InstalledApp) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = NeuTheme.palette
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.label.contains(keyword, ignoreCase = true) ||
                    it.packageName.contains(keyword, ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NeuSurface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .padding(horizontal = 18.dp),
            cornerRadius = NeuDimens.cornerLarge,
            elevation = NeuElevation.RAISED,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeuIcon(
                        kind = NeuIconKind.Settings,
                        modifier = Modifier.size(20.dp),
                        tint = palette.accent,
                    )
                    Spacer(modifier = Modifier.width(NeuDimens.gapSmall))
                    Text(
                        text = "选择应用",
                        color = palette.onBackground,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(NeuDimens.gapMedium))
                NeuTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "搜索",
                    placeholder = "应用名或包名",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(NeuDimens.gapMedium))

                if (filtered.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = if (apps.isEmpty()) {
                                "没有读取到可启动的应用。请直接手动输入包名。"
                            } else {
                                "没有匹配「$query」的应用。"
                            },
                            color = palette.onBackgroundMuted,
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        items(items = filtered, key = { it.packageName }) { app ->
                            AppRow(app = app, onClick = { onSelect(app) })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(NeuDimens.gapMedium))
                NeuButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    NeuButtonContent(icon = NeuIconKind.Close, text = "取消")
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, onClick: () -> Unit) {
    val palette = NeuTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = app.icon
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(36.dp))
        } else {
            NeuIcon(
                kind = NeuIconKind.Settings,
                modifier = Modifier.size(36.dp),
                tint = palette.onBackgroundMuted,
            )
        }
        Spacer(modifier = Modifier.width(NeuDimens.gapMedium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                color = palette.onBackground,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                color = palette.onBackgroundMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
