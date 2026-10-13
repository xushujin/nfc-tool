package com.nfctool.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfctool.app.BuildConfig
import com.nfctool.app.R
import com.nfctool.app.ui.component.NeuIcon
import com.nfctool.app.ui.component.NeuIconKind
import com.nfctool.app.ui.component.NeuKeyValueRow
import com.nfctool.app.ui.component.NeuScreen
import com.nfctool.app.ui.component.NeuSectionTitle
import com.nfctool.app.ui.component.NeuSurface
import com.nfctool.app.ui.theme.NeuDimens
import com.nfctool.app.ui.theme.NeuTheme

/** 关于页：应用名、版本号、技术栈、支持范围、权限说明。 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = NeuTheme.palette

    NeuScreen(title = "关于", modifier = modifier, onBack = onBack) {
        NeuSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeuIcon(
                        kind = NeuIconKind.Tag,
                        modifier = Modifier.size(28.dp),
                        tint = palette.accent,
                    )
                    Spacer(modifier = Modifier.width(NeuDimens.gapMedium))
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            color = palette.onBackground,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "版本 ${BuildConfig.VERSION_NAME}（versionCode ${BuildConfig.VERSION_CODE}）",
                            color = palette.onBackgroundMuted,
                            fontSize = 13.sp,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(NeuDimens.gapSmall))
                NeuKeyValueRow("包名", BuildConfig.APPLICATION_ID)
                NeuKeyValueRow("构建类型", BuildConfig.BUILD_TYPE)
            }
        }

        NeuSectionTitle(text = AppInfo.TECH_STACK_TITLE)
        NeuSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                AppInfo.TECH_STACK.forEach { (label, value) -> NeuKeyValueRow(label, value) }
            }
        }

        NeuSectionTitle(text = AppInfo.SUPPORT_TITLE)
        NeuSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                AppInfo.SUPPORTED.forEach { (label, value) -> NeuKeyValueRow(label, value) }
            }
        }

        NeuSectionTitle(text = "限制与不支持")
        NeuSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                AppInfo.LIMITATIONS.forEach { (label, value) -> NeuKeyValueRow(label, value) }
            }
        }

        NeuSectionTitle(text = AppInfo.PERMISSION_TITLE)
        Column(verticalArrangement = Arrangement.spacedBy(NeuDimens.gapMedium)) {
            AppInfo.PERMISSIONS.forEach { (name, level, reason) ->
                NeuSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = name,
                            color = palette.onBackground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "级别：$level",
                            color = palette.onBackgroundMuted,
                            fontSize = 12.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = reason,
                            color = palette.onBackgroundMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        NeuSectionTitle(text = AppInfo.PRIVACY_TITLE)
        NeuSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                AppInfo.PRIVACY.forEach { (label, value) -> NeuKeyValueRow(label, value) }
            }
        }

        NeuSectionTitle(text = AppInfo.WIFI_NOTE_TITLE)
        NeuSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                AppInfo.WIFI_NOTES.forEach { (label, value) -> NeuKeyValueRow(label, value) }
            }
        }

        Spacer(modifier = Modifier.height(NeuDimens.gapLarge))
    }
}
