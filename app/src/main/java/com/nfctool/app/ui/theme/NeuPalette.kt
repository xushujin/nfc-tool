package com.nfctool.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** 配色的明暗取向。 */
enum class NeuTone { LIGHT, DARK }

/**
 * 一套拟态配色。
 *
 * 亮面 / 暗面阴影**不是手写常量**，而是由底色按固定比例推导：
 * 亮面 = 底色向白偏移，暗面 = 底色向黑偏移。
 * 这样新增一套配色只需要给底色与强调色，明暗关系与"凸起/凹陷"的视觉一致性自动保持。
 */
class NeuPalette(
    val id: String,
    val displayName: String,
    val tone: NeuTone,
    val background: Color,
    val accent: Color,
) {
    val isDark: Boolean get() = tone == NeuTone.DARK

    /** 卡面底色：与背景拉开一点点层级，但保持"同一材质"的观感。 */
    val surface: Color = lerp(background, Color.White, if (isDark) 0.045f else 0.55f)

    /** 状态卡等需要轻微强调的表面底色。 */
    val highlight: Color = if (isDark) {
        lerp(background, accent, 0.10f)
    } else {
        lerp(background, accent, 0.05f)
    }

    /** 凹陷区域的填充：比背景略深。 */
    val insetFill: Color = lerp(background, Color.Black, if (isDark) 0.14f else 0.035f)

    /** 左上方向的亮面阴影。 */
    val lightShadow: Color = lerp(background, Color.White, if (isDark) 0.10f else 0.95f)

    /** 右下方向的暗面阴影。 */
    val darkShadow: Color = lerp(background, Color.Black, if (isDark) 0.55f else 0.14f)

    /** 主文字色。 */
    val onBackground: Color = lerp(background, if (isDark) Color.White else Color.Black, 0.88f)

    /** 次级文字色。 */
    val onBackgroundMuted: Color = lerp(background, if (isDark) Color.White else Color.Black, 0.52f)

    /** 强调色上的文字色：按强调色亮度自动取黑或白。 */
    val onAccent: Color = if (accent.luminance() > 0.55f) Color(0xFF1B1B1B) else Color.White

    val success: Color = if (isDark) Color(0xFF5BD6A0) else Color(0xFF1E8E5A)
    val warning: Color = if (isDark) Color(0xFFE9B44C) else Color(0xFFB4741A)
    val danger: Color = if (isDark) Color(0xFFFF7A7A) else Color(0xFFC0392B)

    override fun toString(): String = "NeuPalette($id, $displayName, $tone)"
}

/** 内置配色主题集合。 */
object NeuPalettes {

    /** 极光：默认主题，冷调浅色。 */
    val AURORA = NeuPalette(
        id = "aurora",
        displayName = "极光",
        tone = NeuTone.LIGHT,
        background = Color(0xFFEEF0F5),
        accent = Color(0xFF4C5FE0),
    )

    /** 沙丘：暖调浅色。 */
    val SAND = NeuPalette(
        id = "sand",
        displayName = "沙丘",
        tone = NeuTone.LIGHT,
        background = Color(0xFFF4EFE7),
        accent = Color(0xFFC97A2B),
    )

    /** 薄荷：青绿浅色。 */
    val MINT = NeuPalette(
        id = "mint",
        displayName = "薄荷",
        tone = NeuTone.LIGHT,
        background = Color(0xFFE9F2EE),
        accent = Color(0xFF1F8F6B),
    )

    /** 石墨：中性深色。 */
    val GRAPHITE = NeuPalette(
        id = "graphite",
        displayName = "石墨",
        tone = NeuTone.DARK,
        background = Color(0xFF2A2E35),
        accent = Color(0xFF7FA6FF),
    )

    /** 午夜：靛蓝深色。 */
    val MIDNIGHT = NeuPalette(
        id = "midnight",
        displayName = "午夜",
        tone = NeuTone.DARK,
        background = Color(0xFF1F2433),
        accent = Color(0xFF9A8CFF),
    )

    val ALL: List<NeuPalette> = listOf(AURORA, SAND, MINT, GRAPHITE, MIDNIGHT)

    val DEFAULT: NeuPalette = AURORA

    /** 按 id 取配色；id 未知时回退到默认配色（不抛异常，避免设置项损坏导致应用起不来）。 */
    fun byId(id: String): NeuPalette = ALL.firstOrNull { it.id == id } ?: DEFAULT

    fun contains(id: String): Boolean = ALL.any { it.id == id }
}
