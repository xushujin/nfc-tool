#!/usr/bin/env python3
"""生成 NFC工具 的启动图标（程序化生成，便于复核）。

产出：
  mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png          传统方形图标
  mipmap-{...}/ic_launcher_round.png                                传统圆形图标
  mipmap-{...}/ic_launcher_foreground.png                           自适应图标前景（透明底）

图形：三圈同心圆弧 + 中心圆点，构成 NFC 波纹符号；底色为拟态风格的浅灰蓝。
"""
from __future__ import annotations

import math
import os
from PIL import Image, ImageDraw

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(REPO_ROOT, "app", "src", "main", "res")

# 拟态浅色主题基色（与 values/colors.xml 的 neu_window_background 一致）
BG_TOP = (247, 248, 252)
BG_BOTTOM = (222, 227, 238)
GLYPH = (70, 80, 107)

SS = 8  # 超采样倍数，抗锯齿

DENSITIES = {
    "mdpi": (48, 108),
    "hdpi": (72, 162),
    "xhdpi": (96, 216),
    "xxhdpi": (144, 324),
    "xxxhdpi": (192, 432),
}


def lerp(a: int, b: int, t: float) -> int:
    return round(a + (b - a) * t)


def rounded_bg(size: int, circular: bool) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    grad = Image.new("RGBA", (size, size))
    d = ImageDraw.Draw(grad)
    for y in range(size):
        t = y / max(1, size - 1)
        d.line([(0, y), (size, y)], fill=(lerp(BG_TOP[0], BG_BOTTOM[0], t),
                                          lerp(BG_TOP[1], BG_BOTTOM[1], t),
                                          lerp(BG_TOP[2], BG_BOTTOM[2], t), 255))
    if circular:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    else:
        mask = Image.new("L", (size, size), 0)
        r = int(size * 0.22)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=255)
    img.paste(grad, (0, 0), mask)
    return img


def draw_glyph(img: Image.Image, box: int, size: int) -> None:
    """在 size×size 画布上绘制 glyph，glyph 外接框边长约 box，居中。"""
    d = ImageDraw.Draw(img)
    cx = size / 2.0
    cy = size / 2.0
    # 圆弧半径按外接框比例取
    radii = [0.20, 0.33, 0.46]
    stroke = box * 0.085
    # 弧张角：右侧开口
    start_deg, end_deg = -62.0, 62.0
    for r_ratio in radii:
        r = box * r_ratio
        steps = max(48, int(r * 1.6))
        for i in range(steps + 1):
            t = i / steps
            ang = math.radians(start_deg + (end_deg - start_deg) * t)
            x = cx + r * math.cos(ang)
            y = cy + r * math.sin(ang)
            d.ellipse([x - stroke / 2, y - stroke / 2, x + stroke / 2, y + stroke / 2],
                      fill=GLYPH + (255,))
    # 中心圆点
    dot = box * 0.085
    d.ellipse([cx - dot, cy - dot, cx + dot, cy + dot], fill=GLYPH + (255,))


def make_icon(size: int, circular: bool) -> Image.Image:
    big = size * SS
    img = rounded_bg(big, circular)
    # 图标内 glyph 占 62% 画布（与自适应图标 66% 安全区一致）
    draw_glyph(img, box=int(big * 0.62), size=big)
    return img.resize((size, size), Image.LANCZOS)


def make_foreground(size: int) -> Image.Image:
    """自适应图标前景：108dp 画布，glyph 落在中心 66% 安全区内，背景透明。"""
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw_glyph(img, box=int(big * 0.52), size=big)
    return img.resize((size, size), Image.LANCZOS)


def main() -> None:
    for density, (legacy, adaptive) in DENSITIES.items():
        out = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(out, exist_ok=True)
        make_icon(legacy, circular=False).save(os.path.join(out, "ic_launcher.png"))
        make_icon(legacy, circular=True).save(os.path.join(out, "ic_launcher_round.png"))
        make_foreground(adaptive).save(os.path.join(out, "ic_launcher_foreground.png"))
        print(f"{density:8s} legacy={legacy:4d} adaptive={adaptive:4d} -> {out}")
    # 供人工复核的放大样张
    preview = os.path.join(REPO_ROOT, "icon-preview.png")
    make_icon(384, circular=False).save(preview)
    print("preview ->", preview)


if __name__ == "__main__":
    main()
