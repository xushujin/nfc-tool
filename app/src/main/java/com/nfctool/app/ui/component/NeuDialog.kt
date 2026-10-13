package com.nfctool.app.ui.component

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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nfctool.app.ui.theme.NeuDimens
import com.nfctool.app.ui.theme.NeuTheme

/**
 * 统一的拟态结果弹窗。
 *
 * 读卡、写卡、各类操作的结果都走这里。此前的底部横幅容易被忽略、也不够醒目，
 * 统一改成居中弹窗：有标题（成功 / 注意 / 失败）、正文与一个关闭按钮，
 * 点击按钮或弹窗外都能关闭。
 */
@Composable
fun NeuDialog(
    title: String,
    message: String,
    level: NeuBannerLevel,
    confirmText: String = "知道了",
    onDismiss: () -> Unit,
) {
    val palette = NeuTheme.palette
    val (icon, tint) = when (level) {
        NeuBannerLevel.INFO -> NeuIconKind.Info to palette.accent
        NeuBannerLevel.SUCCESS -> NeuIconKind.Check to palette.success
        NeuBannerLevel.WARNING -> NeuIconKind.Warning to palette.warning
        NeuBannerLevel.ERROR -> NeuIconKind.Warning to palette.danger
    }

    Dialog(
        onDismissRequest = onDismiss,
        // 关掉平台默认宽度限制，改用与页面一致的留白，避免小屏上弹窗被压得很窄
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NeuSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            cornerRadius = NeuDimens.cornerLarge,
            elevation = NeuElevation.RAISED,
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeuIcon(kind = icon, modifier = Modifier.size(22.dp), tint = tint)
                    Spacer(modifier = Modifier.width(NeuDimens.gapSmall))
                    Text(
                        text = title,
                        color = palette.onBackground,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(NeuDimens.gapMedium))
                Text(
                    text = message,
                    color = palette.onBackground,
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.height(NeuDimens.gapLarge))
                NeuButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    primary = true,
                ) {
                    NeuButtonContent(icon = NeuIconKind.Check, text = confirmText)
                }
            }
        }
    }
}
