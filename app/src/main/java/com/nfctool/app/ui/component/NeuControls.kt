package com.nfctool.app.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfctool.app.ui.theme.NeuDimens
import com.nfctool.app.ui.theme.NeuTheme

/**
 * 拟态按钮。
 *
 * 按下时从「凸起」翻转为「凹陷」，用形状本身承担按压反馈，
 * 因此不再叠加 Material 的涟漪效果（indication 传 null）。
 */
@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    cornerRadius: Dp = NeuDimens.cornerMedium,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 15.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val palette = NeuTheme.palette
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val raised = enabled && !pressed

    NeuSurface(
        modifier = modifier.clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        ),
        cornerRadius = cornerRadius,
        elevation = if (raised) NeuElevation.RAISED else NeuElevation.INSET,
        contentPadding = contentPadding,
        containerColor = if (primary) palette.accent else null,
    ) {
        val contentColor = when {
            !enabled -> palette.onBackgroundMuted
            primary -> palette.onAccent
            else -> palette.onBackground
        }
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content,
            )
        }
    }
}

/** 图标 + 文字的按钮内容，统一间距。 */
@Composable
fun NeuButtonContent(
    icon: NeuIconKind,
    text: String,
    iconSize: Dp = 20.dp,
) {
    val color = LocalContentColor.current
    NeuIcon(kind = icon, modifier = Modifier.size(iconSize), tint = color)
    Spacer(modifier = Modifier.width(NeuDimens.gapSmall))
    Text(text = text, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium)
}

/** 拟态输入框：凹陷容器 + 可选标签 / 占位符 / 辅助说明。 */
@Composable
fun NeuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = NeuTheme.palette
    val textColor = palette.onBackground

    NeuSurface(
        modifier = modifier,
        cornerRadius = NeuDimens.cornerSmall,
        elevation = NeuElevation.INSET,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (label != null) {
                Text(
                    text = label,
                    color = palette.onBackgroundMuted,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    enabled = true,
                    singleLine = singleLine,
                    minLines = minLines,
                    textStyle = LocalTextStyle.current.merge(
                        TextStyle(color = textColor, fontSize = 16.sp),
                    ),
                    cursorBrush = SolidColor(palette.accent),
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty() && placeholder != null) {
                                Text(text = placeholder, color = palette.onBackgroundMuted, fontSize = 16.sp)
                            }
                            inner()
                        }
                    },
                )
                if (trailing != null) {
                    Spacer(modifier = Modifier.width(NeuDimens.gapSmall))
                    trailing()
                }
            }
            if (supportingText != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = supportingText,
                    color = if (isError) palette.danger else palette.onBackgroundMuted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/** 拟态开关：凹陷轨道 + 凸起滑块。 */
@Composable
fun NeuSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = NeuTheme.palette
    val density = LocalDensity.current
    val trackWidth = 56.dp
    val trackHeight = 32.dp
    val knobSize = 24.dp
    val knobTravel = trackWidth - knobSize - 8.dp
    val knobOffset by animateDpAsState(
        targetValue = if (checked) knobTravel else 0.dp,
        label = "neu-switch-knob",
    )

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .clickable { onCheckedChange(!checked) }
            .drawBehind {
                val strokePx = with(density) { NeuDimens.insetStroke.toPx() }
                val corner = size.height / 2f
                drawRoundRect(
                    color = if (checked) palette.accent.copy(alpha = 0.22f) else palette.insetFill,
                    cornerRadius = CornerRadius(corner, corner),
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(palette.darkShadow, palette.lightShadow),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                    topLeft = Offset(strokePx / 2f, strokePx / 2f),
                    size = Size(size.width - strokePx, size.height - strokePx),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = strokePx),
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                // 偏移值由动画状态驱动，必须用 lambda 重载，否则每帧都会重新测量布局
                .offset { IntOffset((4.dp + knobOffset).roundToPx(), 0) }
                .size(knobSize)
                .neuShadow(
                    lightColor = palette.lightShadow,
                    darkColor = palette.darkShadow,
                    cornerRadius = with(density) { (knobSize / 2).toPx() },
                    blur = with(density) { 6.dp.toPx() },
                    offset = with(density) { 2.dp.toPx() },
                    layers = 6,
                )
                .background(color = if (checked) palette.accent else palette.surface, shape = CircleShape),
        )
    }
}

/** 只读的键值行，用于设置页与关于页。 */
@Composable
fun NeuKeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val palette = NeuTheme.palette
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = palette.onBackgroundMuted,
            fontSize = 14.sp,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            color = palette.onBackground,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 拟态分段选择器，用于主题挑选等少量选项场景。 */
@Composable
fun NeuSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = NeuTheme.palette
    NeuSurface(
        modifier = modifier,
        cornerRadius = NeuDimens.cornerSmall,
        elevation = NeuElevation.INSET,
        contentPadding = PaddingValues(5.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .background(
                            color = if (selected) palette.accent else Color.Transparent,
                            shape = RoundedCornerShape(NeuDimens.cornerSmall - 4.dp),
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
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
}
