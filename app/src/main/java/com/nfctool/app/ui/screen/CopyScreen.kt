package com.nfctool.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfctool.app.core.mifare.MifareCopyAvailability
import com.nfctool.app.ui.NfcToolUiState
import com.nfctool.app.ui.component.NeuBanner
import com.nfctool.app.ui.component.NeuBannerLevel
import com.nfctool.app.ui.component.NeuButton
import com.nfctool.app.ui.component.NeuButtonContent
import com.nfctool.app.ui.component.NeuIconKind
import com.nfctool.app.ui.component.NeuPendingCard
import com.nfctool.app.ui.component.NeuScreen
import com.nfctool.app.ui.component.NeuSectionTitle
import com.nfctool.app.ui.component.NeuSurface
import com.nfctool.app.ui.theme.NeuDimens
import com.nfctool.app.ui.theme.NeuTheme

/**
 * 复制页。
 *
 * 复制在内存中完成：先读源卡，再点"写入"并贴目标卡。
 * 应用不会把标签内容（可能含 WiFi 密码）落盘，因此跨会话复制需要重新读一次源卡。
 */
@Composable
fun CopyScreen(
    state: NfcToolUiState,
    onCopyNdef: () -> Unit,
    onCopyMifare: () -> Unit,
    onCancelPending: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = NeuTheme.palette
    val snapshot = state.snapshot

    NeuScreen(title = "复制", modifier = modifier) {
        if (state.pending != null) {
            NeuPendingCard(
                prompt = state.pending.prompt,
                busy = state.busy,
                onCancel = onCancelPending,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(NeuDimens.gapLarge))
        } else {
            NeuBanner(
                text = "复制流程：先在「读卡」页读入源卡，回到本页点写入，再把目标卡贴上去。",
                level = NeuBannerLevel.INFO,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        NeuSectionTitle(text = "源卡")
        if (snapshot == null) {
            NeuSurface(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "还没有读入源卡。请先到「读卡」页贴一张卡。",
                    color = palette.onBackgroundMuted,
                    fontSize = 14.sp,
                )
            }
            Spacer(modifier = Modifier.height(NeuDimens.gapLarge))
            return@NeuScreen
        }

        SnapshotPanel(snapshot = snapshot)

        // 判定逻辑在 core（可单测），这里只负责把结果措辞成用户看得懂的话。
        val mifareAvailability = MifareCopyAvailability.of(snapshot.mifareClassic, state.readMifareDump)

        // 整卡复制不可用时，必须**就地**说明原因。三种情况的处理方式完全不同
        // （去开开关 / 换卡或换设备 / 该卡无法复制），只给一个点不动的灰按钮
        // 用户只会以为功能坏了。
        val mifareUnavailableReason: String? = when (mifareAvailability) {
            MifareCopyAvailability.Available -> null

            MifareCopyAvailability.DumpNotRequested ->
                "还没有读取 MIFARE 数据：请到「读卡」页打开「读取 MIFARE 整卡转储」开关，" +
                    "再贴一次源卡。该开关默认关闭是为了让普通读卡更快。"

            MifareCopyAvailability.NotMifareClassic ->
                "未能把源卡识别为 MIFARE Classic。常见原因有两个：" +
                    "这张卡不是 MIFARE Classic（银行卡、公交卡、CPU 卡以及不少门禁卡用的是别的芯片），" +
                    "或本机 NFC 芯片不支持 MIFARE Classic（部分非 NXP 芯片机型读不到这类卡）。"

            is MifareCopyAvailability.IncompleteDump ->
                "源卡有 ${mifareAvailability.unreadableSectors.size} 个扇区（扇区 " +
                    "${mifareAvailability.unreadableSectors.joinToString("、")}）密钥未知，读不到内容，" +
                    "因此无法整卡复制。这类卡一般使用了厂商自定义密钥，本应用无法破解。"
        }

        NeuSectionTitle(text = "写入方式")
        Column(verticalArrangement = Arrangement.spacedBy(NeuDimens.gapMedium)) {
            NeuButton(
                onClick = onCopyNdef,
                modifier = Modifier.fillMaxWidth(),
                primary = true,
                enabled = snapshot.ndef?.message != null,
            ) {
                NeuButtonContent(icon = NeuIconKind.Copy, text = "复制 NDEF 内容到新卡")
            }

            NeuButton(
                onClick = onCopyMifare,
                modifier = Modifier.fillMaxWidth(),
                enabled = mifareAvailability == MifareCopyAvailability.Available,
            ) {
                NeuButtonContent(icon = NeuIconKind.Tag, text = "整卡回写 MIFARE Classic")
            }

            mifareUnavailableReason?.let { reason ->
                NeuBanner(
                    text = reason,
                    level = NeuBannerLevel.WARNING,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        NeuSectionTitle(text = "能复制到什么程度")
        NeuSurface(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LimitItem(
                    "门禁卡一般没有 NDEF",
                    "门禁卡（多为 MIFARE Classic）通常不写 NDEF 记录，所以「复制 NDEF 内容」对它是灰的，" +
                        "这不是故障。要复制门禁卡请用下面的「整卡回写 MIFARE Classic」。",
                )
                LimitItem("NDEF 内容复制", "读取源卡的 NDEF 记录并写入目标卡。适用于 NTAG 系列等标准标签，效果与源卡一致。")
                LimitItem(
                    "MIFARE 整卡复制",
                    "逐个数据块回写。厂商块（含 UID）出厂只读，扇区尾块（含访问位与密钥）为安全起见不写入，"
                        + "因此这不是 UID 级的完全克隆。密钥未知的扇区无法读取，也就无法复制。",
                )
                LimitItem(
                    "无法复制的卡片",
                    "带加密认证或安全芯片的卡片（银行卡、部分门禁卡、公交卡）无法读取内容，本应用会明确报错而不是假装成功。",
                )
            }
        }

        Spacer(modifier = Modifier.height(NeuDimens.gapLarge))
    }
}

@Composable
private fun LimitItem(title: String, detail: String) {
    val palette = NeuTheme.palette
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = palette.onBackground, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = detail, color = palette.onBackgroundMuted, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(NeuDimens.gapSmall))
    }
}
