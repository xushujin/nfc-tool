package com.nfctool.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LocalNeuPalette = staticCompositionLocalOf { NeuPalettes.DEFAULT }

/** 拟态圆角与间距的统一取值，避免各处硬编码。 */
object NeuDimens {
    val cornerSmall: Dp = 12.dp
    val cornerMedium: Dp = 18.dp
    val cornerLarge: Dp = 26.dp

    val screenPadding: Dp = 20.dp
    val cardPadding: Dp = 18.dp
    val gapSmall: Dp = 8.dp
    val gapMedium: Dp = 14.dp
    val gapLarge: Dp = 22.dp

    /** 阴影扩散半径，越大越"浮"。 */
    val shadowBlur: Dp = 14.dp

    /** 阴影偏移量，决定光源方向（左上打光）。 */
    val shadowOffset: Dp = 6.dp

    /** 凹陷表面的内描边宽度。 */
    val insetStroke: Dp = 1.5.dp
}

/** 拟态主题入口。主题切换通过 [themeId] 传入，由单一设置源驱动，全局生效。 */
@Composable
fun NfcToolTheme(themeId: String, content: @Composable () -> Unit) {
    val palette = NeuPalettes.byId(themeId)
    CompositionLocalProvider(LocalNeuPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toMaterialColorScheme(),
            content = content,
        )
    }
}

/** 取当前配色。组件一律从这里取色，不直接引用具体主题常量。 */
object NeuTheme {
    val palette: NeuPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalNeuPalette.current

    val shapeSmall: RoundedCornerShape
        @Composable
        @ReadOnlyComposable
        get() = RoundedCornerShape(NeuDimens.cornerSmall)

    val shapeMedium: RoundedCornerShape
        @Composable
        @ReadOnlyComposable
        get() = RoundedCornerShape(NeuDimens.cornerMedium)

    val shapeLarge: RoundedCornerShape
        @Composable
        @ReadOnlyComposable
        get() = RoundedCornerShape(NeuDimens.cornerLarge)
}

/**
 * 把拟态配色映射到 Material3 的 ColorScheme。
 *
 * 只用它来给 Material 基础组件（Text / TextField / Switch）兜底取色，
 * 拟态容器的外观由本工程自己的组件实现，不依赖 Material 的阴影与 elevation。
 */
private fun NeuPalette.toMaterialColorScheme() = if (isDark) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onBackground,
        surfaceVariant = insetFill,
        onSurfaceVariant = onBackgroundMuted,
        error = danger,
        onError = Color.White,
        outline = onBackgroundMuted,
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onBackground,
        surfaceVariant = insetFill,
        onSurfaceVariant = onBackgroundMuted,
        error = danger,
        onError = Color.White,
        outline = onBackgroundMuted,
    )
}
