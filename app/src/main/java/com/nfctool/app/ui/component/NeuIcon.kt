package com.nfctool.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nfctool.app.ui.theme.NeuTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * 图标集合。
 *
 * 不引图标库（含 Material Icons 扩展包），全部用画布几何图元自绘：
 * 一是避免多引一个可能被废弃的依赖，二是拟态风格本身偏厚重，
 * 自绘的粗线条图标与凸起/凹陷的观感更一致。
 *
 * 所有图标都在 [0,1] 归一化坐标里绘制，按画布短边等比缩放。
 */
enum class NeuIconKind {
    Read,
    Copy,
    Wifi,
    Media,
    History,
    Settings,
    Info,
    Check,
    Close,
    Back,
    Delete,
    Warning,
    Lock,
    Tag,
}

@Composable
fun NeuIcon(
    kind: NeuIconKind,
    modifier: Modifier = Modifier,
    tint: Color = NeuTheme.palette.onBackground,
) {
    Canvas(modifier = modifier) {
        drawNeuIcon(kind, tint)
    }
}

private fun DrawScope.drawNeuIcon(kind: NeuIconKind, tint: Color) {
    val unit = size.minDimension
    val stroke = unit * 0.085f
    val center = Offset(size.width / 2f, size.height / 2f)
    val left = center.x - unit / 2f
    val top = center.y - unit / 2f

    fun p(x: Float, y: Float) = Offset(left + unit * x, top + unit * y)

    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
        color = tint,
        start = p(x1, y1),
        end = p(x2, y2),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )

    fun circle(x: Float, y: Float, radius: Float, filled: Boolean = true) {
        val r = unit * radius
        if (filled) {
            drawCircle(color = tint, radius = r, center = p(x, y))
        } else {
            drawCircle(color = tint, radius = r, center = p(x, y), style = Stroke(width = stroke))
        }
    }

    fun roundedRect(x: Float, y: Float, w: Float, h: Float, radius: Float, filled: Boolean = false) {
        val topLeft = p(x, y)
        val rectSize = Size(unit * w, unit * h)
        if (filled) {
            drawRoundRect(color = tint, topLeft = topLeft, size = rectSize, cornerRadius = CornerRadius(unit * radius))
        } else {
            drawRoundRect(
                color = tint,
                topLeft = topLeft,
                size = rectSize,
                cornerRadius = CornerRadius(unit * radius),
                style = Stroke(width = stroke),
            )
        }
    }

    fun arc(cx: Float, cy: Float, radius: Float, startDeg: Float, sweepDeg: Float) {
        val r = unit * radius
        val c = p(cx, cy)
        val rect = Rect(
            offset = Offset(c.x - r, c.y - r),
            size = Size(r * 2f, r * 2f),
        )
        drawArc(
            color = tint,
            startAngle = startDeg,
            sweepAngle = sweepDeg,
            useCenter = false,
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }

    when (kind) {
        // 读卡：卡片轮廓 + 右下角无线波纹
        NeuIconKind.Read -> {
            roundedRect(0.12f, 0.26f, 0.62f, 0.48f, 0.09f)
            line(0.24f, 0.4f, 0.52f, 0.4f)
            line(0.24f, 0.56f, 0.44f, 0.56f)
            arc(0.78f, 0.66f, 0.13f, -70f, 140f)
            arc(0.78f, 0.66f, 0.22f, -70f, 140f)
        }

        // 复制：两张叠放的卡片
        NeuIconKind.Copy -> {
            roundedRect(0.12f, 0.12f, 0.54f, 0.54f, 0.09f)
            roundedRect(0.34f, 0.34f, 0.54f, 0.54f, 0.09f)
        }

        // WiFi：三段波纹 + 圆点
        NeuIconKind.Wifi -> {
            arc(0.5f, 0.74f, 0.12f, 200f, 140f)
            arc(0.5f, 0.74f, 0.24f, 200f, 140f)
            arc(0.5f, 0.74f, 0.36f, 200f, 140f)
            circle(0.5f, 0.76f, 0.045f)
        }

        // 媒体：播放三角 + 圆角方框
        NeuIconKind.Media -> {
            roundedRect(0.1f, 0.22f, 0.8f, 0.56f, 0.1f)
            val triangle = Path().apply {
                moveTo(left + unit * 0.42f, top + unit * 0.38f)
                lineTo(left + unit * 0.42f, top + unit * 0.62f)
                lineTo(left + unit * 0.62f, top + unit * 0.5f)
                close()
            }
            drawPath(path = triangle, color = tint)
        }

        // 历史：表盘 + 指针
        NeuIconKind.History -> {
            circle(0.5f, 0.5f, 0.36f, filled = false)
            line(0.5f, 0.5f, 0.5f, 0.28f)
            line(0.5f, 0.5f, 0.68f, 0.58f)
        }

        // 设置：外圈齿轮（八向齿）+ 内圆
        NeuIconKind.Settings -> {
            circle(0.5f, 0.5f, 0.32f, filled = false)
            circle(0.5f, 0.5f, 0.1f)
            val inner = unit * 0.3f
            val outer = unit * 0.46f
            for (index in 0 until 8) {
                val angle = (index * 45f) * (Math.PI.toFloat() / 180f)
                val x1 = center.x + inner * cos(angle)
                val y1 = center.y + inner * sin(angle)
                val x2 = center.x + outer * cos(angle)
                val y2 = center.y + outer * sin(angle)
                drawLine(color = tint, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }

        // 关于：圆圈 + i
        NeuIconKind.Info -> {
            circle(0.5f, 0.5f, 0.38f, filled = false)
            circle(0.5f, 0.32f, 0.045f)
            line(0.5f, 0.45f, 0.5f, 0.7f)
        }

        NeuIconKind.Check -> {
            line(0.22f, 0.52f, 0.42f, 0.72f)
            line(0.42f, 0.72f, 0.78f, 0.3f)
        }

        NeuIconKind.Close -> {
            line(0.26f, 0.26f, 0.74f, 0.74f)
            line(0.74f, 0.26f, 0.26f, 0.74f)
        }

        NeuIconKind.Back -> {
            line(0.58f, 0.24f, 0.34f, 0.5f)
            line(0.34f, 0.5f, 0.58f, 0.76f)
        }

        NeuIconKind.Delete -> {
            line(0.2f, 0.3f, 0.8f, 0.3f)
            line(0.38f, 0.3f, 0.42f, 0.2f)
            line(0.62f, 0.3f, 0.58f, 0.2f)
            line(0.42f, 0.2f, 0.58f, 0.2f)
            roundedRect(0.28f, 0.3f, 0.44f, 0.5f, 0.06f)
            line(0.44f, 0.42f, 0.44f, 0.68f)
            line(0.56f, 0.42f, 0.56f, 0.68f)
        }

        NeuIconKind.Warning -> {
            val triangle = Path().apply {
                moveTo(left + unit * 0.5f, top + unit * 0.14f)
                lineTo(left + unit * 0.9f, top + unit * 0.84f)
                lineTo(left + unit * 0.1f, top + unit * 0.84f)
                close()
            }
            drawPath(path = triangle, color = tint, style = Stroke(width = stroke, join = StrokeJoin.Round))
            line(0.5f, 0.42f, 0.5f, 0.62f)
            circle(0.5f, 0.72f, 0.035f)
        }

        NeuIconKind.Lock -> {
            roundedRect(0.24f, 0.46f, 0.52f, 0.42f, 0.08f)
            arc(0.5f, 0.46f, 0.18f, 180f, 180f)
        }

        NeuIconKind.Tag -> {
            val tagPath = Path().apply {
                moveTo(left + unit * 0.14f, top + unit * 0.14f)
                lineTo(left + unit * 0.54f, top + unit * 0.14f)
                lineTo(left + unit * 0.86f, top + unit * 0.46f)
                lineTo(left + unit * 0.46f, top + unit * 0.86f)
                lineTo(left + unit * 0.14f, top + unit * 0.54f)
                close()
            }
            drawPath(path = tagPath, color = tint, style = Stroke(width = stroke, join = StrokeJoin.Round))
            circle(0.36f, 0.36f, 0.05f)
        }
    }
}
