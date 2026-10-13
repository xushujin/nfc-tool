package com.nfctool.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfctool.app.ui.theme.NeuDimens
import com.nfctool.app.ui.theme.NeuTheme

/** 圆形/圆角拟态图标按钮。 */
@Composable
fun NeuIconButton(
    kind: NeuIconKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val palette = NeuTheme.palette
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    NeuSurface(
        modifier = modifier.clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClickLabel = contentDescription,
            onClick = onClick,
        ),
        cornerRadius = NeuDimens.cornerSmall,
        elevation = if (enabled && !pressed) NeuElevation.RAISED else NeuElevation.INSET,
        contentPadding = PaddingValues(11.dp),
    ) {
        NeuIcon(
            kind = kind,
            modifier = Modifier.size(22.dp),
            tint = if (enabled) palette.onBackground else palette.onBackgroundMuted,
        )
    }
}

/** 统一的页面骨架：背景 + 顶栏 + 可滚动内容。 */
@Composable
fun NeuScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    scrollable: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = NeuTheme.palette
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = NeuDimens.screenPadding,
                    end = NeuDimens.screenPadding,
                    top = NeuDimens.gapMedium,
                    bottom = NeuDimens.gapSmall,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                NeuIconButton(
                    kind = NeuIconKind.Back,
                    onClick = onBack,
                    contentDescription = "返回",
                )
                Spacer(modifier = Modifier.width(NeuDimens.gapMedium))
            }
            Text(
                text = title,
                color = palette.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            actions()
        }

        val contentModifier = if (scrollable) {
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxSize()
        }
        Column(
            modifier = contentModifier.padding(horizontal = NeuDimens.screenPadding),
            content = content,
        )
    }
}

/** 分区标题。 */
@Composable
fun NeuSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = NeuTheme.palette.onBackgroundMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(
            start = NeuDimens.gapSmall,
            top = NeuDimens.gapLarge,
            bottom = NeuDimens.gapSmall,
        ),
    )
}

/** 提示条级别。 */
enum class NeuBannerLevel { INFO, SUCCESS, WARNING, ERROR }

/**
 * 提示条。
 *
 * 读卡/写卡的失败原因是用户最需要看到的信息，因此统一走这里展示，
 * 不弹 Toast、不做二次包装。
 */
@Composable
fun NeuBanner(
    text: String,
    level: NeuBannerLevel,
    modifier: Modifier = Modifier,
) {
    val palette = NeuTheme.palette
    val (icon, tint) = when (level) {
        NeuBannerLevel.INFO -> NeuIconKind.Info to palette.accent
        NeuBannerLevel.SUCCESS -> NeuIconKind.Check to palette.success
        NeuBannerLevel.WARNING -> NeuIconKind.Warning to palette.warning
        NeuBannerLevel.ERROR -> NeuIconKind.Warning to palette.danger
    }

    NeuSurface(
        modifier = modifier,
        cornerRadius = NeuDimens.cornerSmall,
        elevation = NeuElevation.INSET,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            NeuIcon(kind = icon, modifier = Modifier.size(18.dp), tint = tint)
            Spacer(modifier = Modifier.width(NeuDimens.gapSmall))
            Text(
                text = text,
                color = palette.onBackground,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 底部导航的一项。 */
data class NeuNavItem(val kind: NeuIconKind, val label: String)

/** 拟态底部导航。 */
@Composable
fun NeuBottomNav(
    items: List<NeuNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = NeuTheme.palette
    NeuSurface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = NeuDimens.gapMedium, vertical = NeuDimens.gapSmall),
        cornerRadius = NeuDimens.cornerLarge,
        elevation = NeuElevation.RAISED,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NeuIcon(
                        kind = item.kind,
                        modifier = Modifier.size(22.dp),
                        tint = if (selected) palette.accent else palette.onBackgroundMuted,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.label,
                        color = if (selected) palette.accent else palette.onBackgroundMuted,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/** 居中的空状态提示。 */
@Composable
fun NeuEmptyState(
    icon: NeuIconKind,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val palette = NeuTheme.palette
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NeuIcon(kind = icon, modifier = Modifier.size(48.dp), tint = palette.onBackgroundMuted)
            Spacer(modifier = Modifier.height(NeuDimens.gapMedium))
            Text(text = title, color = palette.onBackground, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = description, color = palette.onBackgroundMuted, fontSize = 13.sp)
        }
    }
}
