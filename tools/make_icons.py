# -*- coding: utf-8 -*-
"""런처 아이콘 생성: 파스텔 원 + 마이크 이모지."""
import os
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(os.path.dirname(HERE), "app", "src", "main", "res")
SRC = os.path.join(HERE, "cache", "1f3a4.png")
BG = (255, 239, 206, 255)      # icon_bg
RING = (238, 139, 118, 255)    # coral_deep

emoji = Image.open(SRC).convert("RGBA")


def circle_icon(size, emoji_scale=0.72):
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(canvas)
    pad = max(1, int(size * 0.02))
    d.ellipse([pad, pad, size - 1 - pad, size - 1 - pad], fill=BG, outline=RING,
              width=max(1, int(size * 0.02)))
    inner = int(size * emoji_scale)
    im = emoji.resize((inner, inner), Image.LANCZOS)
    canvas.alpha_composite(im, ((size - inner) // 2, (size - inner) // 2))
    return canvas


def round_icon(size, emoji_scale=0.72):
    icon = circle_icon(size, emoji_scale)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(icon, (0, 0), mask)
    return out


def foreground(size):
    # 어댑티브 아이콘 전경: 안전 영역(66%) 안에 이모지
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    inner = int(size * 0.52)
    im = emoji.resize((inner, inner), Image.LANCZOS)
    canvas.alpha_composite(im, ((size - inner) // 2, (size - inner) // 2))
    return canvas


DENS = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

for name, mult in DENS.items():
    folder = os.path.join(RES, "mipmap-" + name)
    os.makedirs(folder, exist_ok=True)
    size = int(48 * mult)
    circle_icon(size).save(os.path.join(folder, "ic_launcher.png"))
    round_icon(size).save(os.path.join(folder, "ic_launcher_round.png"))
    foreground(int(108 * mult)).save(os.path.join(folder, "ic_launcher_foreground.png"))

print("icons done:", sorted(os.listdir(os.path.join(RES, "mipmap-xxhdpi"))))
