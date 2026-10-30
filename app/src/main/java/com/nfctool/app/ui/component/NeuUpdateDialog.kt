package com.nfctool.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nfctool.app.core.update.ReleaseInfo
import com.nfctool.app.ui.theme.NeuDimens
import com.nfctool.app.ui.theme.NeuTheme
import kotlin.math.roundToInt

/**
 * 发现新版本时的弹窗。
 *
 * 两种形态共用一个组件：未开始下载时给「立即更新 / 跳过此版本 / 稍后」三个选择；
 * 下载中则换成真实进度条与已下载字节数。下载过程中禁止点外部或返回键关闭 ——
 * 关掉弹窗并不会取消下载，让用户以为「已经取消了」比不让他关更糟。
 */
@Composable
fun NeuUpdateDialog(
    info: ReleaseInfo,
    currentVersion: String,
    onInstall: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    downloadingFraction: Float? = null,
    downloadedBytes: Long = 0L,
    totalBytes: Long = 0L,
    apkSizeBytes: Long = 0L,
) {
    val palette = NeuTheme.palette
    val downloading = downloadingFraction != null
    val notes = info.notes.trim().ifEmpty { "本次更新未提供说明。" }

    Dialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !downloading,
            dismissOnClickOutside = !downloading,
        ),
    ) {
        NeuSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            cornerRadius = NeuDimens.cornerLarge,
            elevation = NeuElevation.RAISED,
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeuIcon(
                        kind = NeuIconKind.Info,
                        modifier = Modifier.size(22.dp),
                        tint = palette.accent,
                    )
                    Spacer(modifier = Modifier.width(NeuDimens.gapSmall))
                    Text(
                        text = "发现新版本 ${info.version}",
                        color = palette.onBackground,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(NeuDimens.gapSmall))
                Text(
                    text = "当前版本 $currentVersion → ${info.version}　" +
                        "安装包 ${formatSize(if (totalBytes > 0L) totalBytes else apkSizeBytes)}",
                    color = palette.onBackgroundMuted,
                    fontSize = 12.sp,
                )

                Spacer(modifier = Modifier.height(NeuDimens.gapMedium))
                Text(
                    text = "更新说明",
                    color = palette.onBackgroundMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 168.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = notes.take(MAX_NOTES_CHARS),
                        color = palette.onBackground,
                        fontSize = 13.sp,
                    )
                }

                if (downloading) {
                    Spacer(modifier = Modifier.height(NeuDimens.gapMedium))
                    NeuProgressBar(
                        progress = downloadingFraction ?: 0f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = buildString {
                            append("正在下载 ${((downloadingFraction ?: 0f) * 100).roundToInt()}%")
                            if (totalBytes > 0L) {
                                append("（${formatSize(downloadedBytes)} / ${formatSize(totalBytes)}）")
                            }
                        },
                        color = palette.onBackgroundMuted,
                        fontSize = 12.sp,
                    )
                } else {
                    Spacer(modifier = Modifier.height(NeuDimens.gapLarge))
                    NeuButton(
                        onClick = onInstall,
                        modifier = Modifier.fillMaxWidth(),
                        primary = true,
                    ) {
                        NeuButtonContent(icon = NeuIconKind.Check, text = "立即更新")
                    }
                    Spacer(modifier = Modifier.height(NeuDimens.gapSmall))
                    NeuButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                        NeuButtonContent(icon = NeuIconKind.Close, text = "跳过此版本")
                    }
                    Spacer(modifier = Modifier.height(NeuDimens.gapSmall))
                    NeuButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        NeuButtonContent(icon = NeuIconKind.Back, text = "稍后")
                    }
                }
            }
        }
    }
}

/** 字节数转成人能读的大小。不用 String.format，避免默认 Locale 带来的格式差异。 */
private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "大小未知"
    bytes >= 1024L * 1024L -> "${(bytes / 1024.0 / 1024.0).roundToInt()} MB"
    bytes >= 1024L -> "${(bytes / 1024.0).roundToInt()} KB"
    else -> "$bytes B"
}

private const val MAX_NOTES_CHARS = 800
