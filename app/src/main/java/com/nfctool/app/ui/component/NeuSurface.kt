package com.nfctool.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nfctool.app.ui.theme.NeuDimens
import com.nfctool.app.ui.theme.NeuTheme

/** 拟态表面的三种状态。 */
enum class NeuElevation {
    /** 凸起：外部双向阴影。 */
    RAISED,

    /** 凹陷：内填充加深 + 左上暗 / 右下亮的内描边。 */
    INSET,

    /** 齐平：只有底色，无阴影。 */
    FLAT,
}

/**
 * 拟态双向阴影。
 *
 * Compose 自带的 `Modifier.shadow` 只能画单侧阴影，而拟态风格要求
 * 左上亮面与右下暗面同时存在，因此这里自己绘制：
 * 由外向内叠加 [layers] 层逐渐收敛的圆角矩形，每层固定低透明度，
 * 叠加后形成近似高斯衰减的柔和阴影。
 *
 * 相比 `Paint.setShadowLayer`，这种画法不依赖软件图层，
 * 在硬件加速画布上表现一致，也不受 API 版本限制。
 */
fun Modifier.neuShadow(
    lightColor: Color,
    darkColor: Color,
    cornerRadius: Float,
    blur: Float,
    offset: Float,
    layers: Int = 12,
): Modifier = drawBehind {
    drawSoftShadow(lightColor, cornerRadius, blur, -offset, -offset, layers)
    drawSoftShadow(darkColor, cornerRadius, blur, offset, offset, layers)
}

/** 凹陷表面的内描边：左上暗、右下亮。 */
fun Modifier.neuInnerEdge(
    lightColor: Color,
    darkColor: Color,
    cornerRadius: Float,
    strokeWidth: Float,
): Modifier = drawBehind {
    val inset = strokeWidth / 2f
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(darkColor, lightColor),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        style = Stroke(width = strokeWidth),
    )
}

private fun DrawScope.drawSoftShadow(
    color: Color,
    cornerRadius: Float,
    blur: Float,
    offsetX: Float,
    offsetY: Float,
    layers: Int,
) {
    if (blur <= 0f || layers <= 0) return
    val layerAlpha = color.alpha / layers
    for (layer in layers downTo 1) {
        val grow = blur * (layer / layers.toFloat())
        drawRoundRect(
            color = color.copy(alpha = layerAlpha),
            topLeft = Offset(offsetX - grow, offsetY - grow),
            size = Size(size.width + 2f * grow, size.height + 2f * grow),
            cornerRadius = CornerRadius(cornerRadius + grow, cornerRadius + grow),
        )
    }
}

/**
 * 拟态容器。
 *
 * 内部会预留出阴影绘制所需的外边距，因此调用方不需要为阴影额外留 padding；
 * 相邻元素之间的间距仍然需要调用方自己给（见 [NeuDimens.gapMedium]）。
 */
@Composable
fun NeuSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = NeuDimens.cornerMedium,
    elevation: NeuElevation = NeuElevation.RAISED,
    contentPadding: PaddingValues = PaddingValues(NeuDimens.cardPadding),
    containerColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = NeuTheme.palette
    val density = LocalDensity.current
    val cornerPx = with(density) { cornerRadius.toPx() }
    val blurPx = with(density) { NeuDimens.shadowBlur.toPx() }
    val offsetPx = with(density) { NeuDimens.shadowOffset.toPx() }
    val strokePx = with(density) { NeuDimens.insetStroke.toPx() }

    // 阴影向外扩散 blur/2 并整体偏移 offset，这里预留 offset + blur/2 的外边距
    val shape = RoundedCornerShape(cornerRadius)

    val defaultFill = when (elevation) {
        NeuElevation.RAISED, NeuElevation.FLAT -> palette.surface
        NeuElevation.INSET -> palette.insetFill
    }
    val fill = containerColor ?: defaultFill

    val surfaceModifier = when (elevation) {
        NeuElevation.RAISED -> Modifier
            .neuShadow(
                lightColor = palette.lightShadow,
                darkColor = palette.darkShadow,
                cornerRadius = cornerPx,
                blur = blurPx,
                offset = offsetPx,
            )
            .background(color = fill, shape = shape)

        NeuElevation.INSET -> Modifier
            .background(color = fill, shape = shape)
            .neuInnerEdge(
                lightColor = palette.lightShadow,
                darkColor = palette.darkShadow,
                cornerRadius = cornerPx,
                strokeWidth = strokePx,
            )

        NeuElevation.FLAT -> Modifier.background(color = fill, shape = shape)
    }

    Box(
        modifier = modifier
            .padding(NeuShadowReserve)
            .then(surfaceModifier),
    ) {
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** 阴影预留的外边距，供需要精确对齐的布局使用。 */
val NeuShadowReserve: Dp = NeuDimens.shadowOffset + NeuDimens.shadowBlur / 2
