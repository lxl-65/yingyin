#!/usr/bin/env python3
"""Generate the Yingyin launcher icon set from one vector-like master.

Design B: deep ink background, orange film frame, cream play triangle, no text.
The Android adaptive foreground is kept in res/drawable/ic_launcher_foreground.xml;
this script generates the legacy PNGs, TV banner and web preview.
"""
from __future__ import annotations

import math
import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "android" / "app" / "src" / "main" / "res"
TOOLS = ROOT / "reports"
S = 1024
U = S / 108.0  # one adaptive-icon unit in master pixels

BG = (12, 13, 17, 255)
BG_TOP = (25, 27, 35, 255)
BG_BOTTOM = (7, 8, 11, 255)
ORANGE = (255, 107, 61, 255)
CREAM = (255, 243, 230, 255)

FRAME = (29.0, 27.0, 79.0, 81.0)  # left, top, right, bottom in 108-space
FRAME_RADIUS = 14.0
FRAME_WIDTH = 6.5
TRIANGLE = [(46.0, 40.0), (46.0, 68.0), (68.0, 54.0)]
HOLES = [(30.65, 38.2), (74.15, 38.2), (30.65, 51.8), (74.15, 51.8), (30.65, 65.4), (74.15, 65.4)]
HOLE_W, HOLE_H = 3.2, 4.6


def px(v: float) -> int:
    return int(round(v * U))


def gradient_background(size: int) -> Image.Image:
    """Subtle vertical ink gradient with a soft upper-left lift."""
    grad = Image.new("RGBA", (1, size), BG_BOTTOM)
    p = grad.load()
    for y in range(size):
        t = y / max(1, size - 1)
        # ease-in for a darker bottom
        t = t * t * (3 - 2 * t)
        p[0, y] = tuple(
            int(BG_TOP[i] * (1 - t) + BG_BOTTOM[i] * t) for i in range(4)
        )
    img = grad.resize((size, size), Image.Resampling.BICUBIC)
    # Soft radial lift, clipped to the canvas.
    glow = Image.new("L", (size, size), 0)
    gd = ImageDraw.Draw(glow)
    cx, cy, radius = size * 0.34, size * 0.25, size * 0.78
    gd.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=70)
    glow = glow.filter(ImageFilterGaussian(size * 0.12))
    lift = Image.new("RGBA", (size, size), (60, 34, 25, 255))
    img = Image.composite(lift, img, glow.point(lambda v: v // 3))
    return img


def ImageFilterGaussian(radius: float):
    from PIL import ImageFilter

    return ImageFilter.GaussianBlur(radius)


def rounded_mask(size: int, radius: float) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    return mask


def circle_mask(size: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse((0, 0, size - 1, size - 1), fill=255)
    return mask


def draw_art(size: int = S, with_background: bool = True, square_mask: bool = True) -> Image.Image:
    """Draw icon art at any resolution. If with_background, returns opaque tile."""
    u = size / 108.0

    def p(v: float) -> int:
        return int(round(v * u))

    if with_background:
        img = gradient_background(size)
        if square_mask:
            mask = rounded_mask(size, radius=24 * u)
            out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
            out.paste(img, (0, 0), mask)
            img = out
    else:
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    # Orange film frame, punched with transparent sprocket holes.
    frame_mask = Image.new("L", (size, size), 0)
    fd = ImageDraw.Draw(frame_mask)
    fd.rounded_rectangle(
        tuple(p(v) for v in FRAME),
        radius=FRAME_RADIUS * u,
        outline=255,
        width=max(1, int(round(FRAME_WIDTH * u))),
    )
    for hx, hy in HOLES:
        fd.rounded_rectangle(
            (p(hx), p(hy), p(hx + HOLE_W), p(hy + HOLE_H)),
            radius=max(1, int(round(1.15 * u))),
            fill=0,
        )
    frame_layer = Image.new("RGBA", (size, size), ORANGE)
    img.alpha_composite(Image.composite(frame_layer, Image.new("RGBA", (size, size), (0, 0, 0, 0)), frame_mask))

    # Cream play triangle.
    tri = Image.new("L", (size, size), 0)
    td = ImageDraw.Draw(tri)
    td.polygon([(p(x), p(y)) for x, y in TRIANGLE], fill=255)
    tri_layer = Image.new("RGBA", (size, size), CREAM)
    img.alpha_composite(Image.composite(tri_layer, Image.new("RGBA", (size, size), (0, 0, 0, 0)), tri))
    return img


def save_resized(img: Image.Image, path: Path, size: int):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.resize((size, size), Image.Resampling.LANCZOS).save(path, optimize=True)


def make_legacy():
    master = draw_art(S, with_background=True, square_mask=True)
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for d, size in densities.items():
        save_resized(master, RES / f"mipmap-{d}" / "ic_launcher.png", size)
    # Round legacy icon: same art clipped to a circle.
    bg = gradient_background(S)
    art = draw_art(S, with_background=False)
    round_img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    round_img.paste(bg, (0, 0), circle_mask(S))
    round_img.alpha_composite(art)
    for d, size in densities.items():
        save_resized(round_img, RES / f"mipmap-{d}" / "ic_launcher_round.png", size)
    return master


def make_web_and_preview(master: Image.Image):
    docs = ROOT / "docs"
    docs.mkdir(exist_ok=True)
    master.resize((512, 512), Image.Resampling.LANCZOS).save(docs / "icon.png", optimize=True)
    # Preview contact sheet for visual QA.
    TOOLS.mkdir(exist_ok=True)
    tile = 220
    sheet = Image.new("RGB", (tile * 4, tile + 46), (8, 9, 12))
    labels = ["Rounded tile", "Circle mask", "Adaptive safe", "48 px"]
    for i, label in enumerate(labels):
        cell = Image.new("RGBA", (tile, tile), (8, 9, 12, 255))
        if i == 0:
            icon = master.resize((tile - 28, tile - 28), Image.Resampling.LANCZOS)
        elif i == 1:
            icon = Image.new("RGBA", (tile - 28, tile - 28), (0, 0, 0, 0))
            bg = gradient_background(tile - 28)
            icon.paste(bg, (0, 0), circle_mask(tile - 28))
            icon.alpha_composite(draw_art(tile - 28, with_background=False))
        elif i == 2:
            icon = Image.new("RGBA", (tile - 28, tile - 28), (0, 0, 0, 0))
            icon.alpha_composite(draw_art(tile - 28, with_background=False))
            cell.alpha_composite(Image.new("RGBA", (tile - 28, tile - 28), (12, 13, 17, 255)), (14, 14))
        else:
            icon = master.resize((48, 48), Image.Resampling.LANCZOS).convert("RGBA")
        cell.alpha_composite(icon, ((tile - icon.width) // 2, (tile - icon.height) // 2))
        sheet.paste(cell.convert("RGB"), (i * tile, 0))
        d = ImageDraw.Draw(sheet)
        d.text((i * tile + tile // 2, tile + 14), label, fill=(180, 184, 194), anchor="ma")
    sheet.save(TOOLS / "icon_b_preview.png", optimize=True)


def make_tv_banner():
    w, h = 640, 360  # 2x of recommended 320x180, saved to xxhdpi below
    bg = gradient_background(max(w, h)).crop((0, 0, w, h))
    banner = Image.new("RGBA", (w, h), BG)
    banner.paste(bg, (0, 0))
    # left icon
    icon = draw_art(260, with_background=True, square_mask=True)
    banner.alpha_composite(icon, (42, 50))
    # text
    try:
        font_big = ImageFont.truetype(r"C:\Windows\Fonts\msyhbd.ttc", 58)
        font_small = ImageFont.truetype(r"C:\Windows\Fonts\msyh.ttc", 23)
    except OSError:
        font_big = ImageFont.load_default()
        font_small = ImageFont.load_default()
    d = ImageDraw.Draw(banner)
    d.text((335, 112), "影视仓", font=font_big, fill=CREAM, anchor="ls")
    d.text((337, 164), "影视聚合播放器", font=font_small, fill=(166, 170, 181), anchor="ls")
    d.rounded_rectangle((337, 207, 535, 211), radius=2, fill=ORANGE)
    # Save both densities for TV launchers.
    out = RES / "drawable-xxhdpi" / "tv_banner.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    banner.convert("RGB").resize((w, h), Image.Resampling.LANCZOS).save(out, optimize=True)
    out2 = RES / "drawable-xhdpi" / "tv_banner.png"
    out2.parent.mkdir(parents=True, exist_ok=True)
    banner.convert("RGB").resize((320, 180), Image.Resampling.LANCZOS).save(out2, optimize=True)


def main():
    master = make_legacy()
    make_web_and_preview(master)
    make_tv_banner()
    print("generated", RES, "and", ROOT / "docs" / "icon.png")
    print("preview", TOOLS / "icon_b_preview.png")


if __name__ == "__main__":
    main()
