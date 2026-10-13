package com.nfctool.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfctool.app.ui.theme.NeuDimens
import com.nfctool.app.ui.theme.NeuTheme

/**
 * 拟态不确定进度条。
 *
 * 不确定（indeterminate）而非确定，是因为「写 NFC」本身无法量化进度：
 * 耗时几乎全花在等用户贴卡上，写进去只是几十毫秒。给出百分比会是假信息。
 */
@Composable
fun NeuLinearProgress(
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val palette = NeuTheme.palette
    val transition = rememberInfiniteTransition(label = "neu-linear-progress")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "neu-linear-progress-offset",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = palette.insetFill, cornerRadius = radius)

        val barWidth = size.width * 0.28f
        val travel = size.width + barWidth
        val startX = travel * offset - barWidth
        drawRoundRect(
            color = palette.accent,
            topLeft = Offset(startX, 0f),
            size = Size(barWidth, size.height),
            cornerRadius = radius,
        )
    }
}

/**
 * 「正在等标签」的呼吸波纹指示器。
 *
 * 三层同心弧由外向内扩散、透明度递减，向用户传达"设备在听，需要贴卡"。
 * 纯画布实现，不依赖任何动画库。
 */
@Composable
fun NeuScanIndicator(
    modifier: Modifier = Modifier,
    rings: Int = 3,
) {
    val palette = NeuTheme.palette
    val transition = rememberInfiniteTransition(label = "neu-scan")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "neu-scan-phase",
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f
        val coreRadius = maxRadius * 0.22f
        val stroke = maxRadius * 0.09f

        // 中心圆点：稳定，作为视觉锚点
        drawCircle(color = palette.accent, radius = coreRadius, center = center)

        for (ring in 0 until rings) {
            // 每层错开一个相位，形成由内向外扩散的观感
            val local = (phase + ring.toFloat() / rings) % 1f
            val radius = coreRadius + (maxRadius - coreRadius) * local
            if (radius <= coreRadius) continue
            val alpha = (1f - local) * 0.65f
            drawCircle(
                color = palette.accent.copy(alpha = alpha),
                radius = radius,
                center = center,
                style = Stroke(width = stroke),
            )
        }
    }
}

/**
 * 「等待标签 / 正在写入」状态卡。
 *
 * 把三段状态集中在一处展示，替换原先只在角落里挂一条文字横幅的做法：
 *  1. 等待贴卡：波纹动画 + 明确指令 + 取消按钮
 *  2. 正在写入：进度条
 *  3. 结果：由调用方的全局提示条展示
 */
@Composable
fun NeuPendingCard(
    prompt: String,
    busy: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = NeuTheme.palette
    NeuSurface(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        containerColor = if (busy) palette.highlight else palette.highlight,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (busy) {
                NeuScanIndicator(modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(NeuDimens.gapMedium))
                Text(
                    text = "正在写入…",
                    color = palette.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(NeuDimens.gapSmall))
                NeuLinearProgress(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(NeuDimens.gapSmall))
                Text(
                    text = "拿稳卡片，直到出现成功或失败提示",
                    color = palette.onBackgroundMuted,
                    fontSize = 12.sp,
                )
            } else {
                NeuScanIndicator(modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(NeuDimens.gapMedium))
                Text(
                    text = prompt,
                    color = palette.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(NeuDimens.gapSmall))
                Text(
                    text = "把卡片贴到手机背面的 NFC 感应区",
                    color = palette.onBackgroundMuted,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(NeuDimens.gapLarge))
                NeuButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    NeuButtonContent(icon = NeuIconKind.Close, text = "取消写入")
                }
            }
        }
    }
}

/**
 * 横向可滚动的选择条。
 *
 * 写入类型从 4 个涨到 7 个，等分的分段控制器在小屏会挤成一团，
 * 改成可横向滚动的胶囊标签。
 */
@Composable
fun NeuChipRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = NeuTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(NeuDimens.gapSmall),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .neuClickable { onSelect(index) }
                    .background(
                        color = if (selected) palette.accent else palette.insetFill,
                        shape = RoundedCornerShape(50),
                    )
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Text(
                    text = option,
                    color = if (selected) palette.onAccent else palette.onBackgroundMuted,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

/** 顶部胶囊形小图标按钮（用于表单内的辅助操作）。 */
@Composable
fun NeuPillButton(
    icon: NeuIconKind,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .neuClickable(onClick = onClick)
            .background(color = NeuTheme.palette.accent.copy(alpha = 0.14f), shape = RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeuIcon(kind = icon, modifier = Modifier.size(16.dp), tint = NeuTheme.palette.accent)
        Spacer(modifier = Modifier.width(NeuDimens.gapSmall))
        Text(text = text, color = NeuTheme.palette.accent, fontSize = 13.sp)
    }
}

/** 供 [NeuPendingCard] 使用的强调底色，见 [com.nfctool.app.ui.theme.NeuPalette.highlight]。 */
