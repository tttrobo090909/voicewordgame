# -*- coding: utf-8 -*-
"""v2.0 아이콘: 3D 노란 병아리 앱 아이콘 + 첫 화면 배너용 병아리 마스코트.

원본(tools/src/chick_icon.png, chick_mascot.png)에서
- mipmap-* : ic_launcher / ic_launcher_round / ic_launcher_foreground(어댑티브 전경)
- drawable-nodpi/chick_mascot.webp : 배너에 쓰는 투명 배경 병아리
를 만든다. 마스코트는 테두리에서 이어진 흰 배경만 지워서(flood fill)
눈·배의 흰색 하이라이트는 그대로 둔다.
"""
import os
from collections import deque

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "app", "src", "main", "res")
SRC_ICON = os.path.join(HERE, "src", "chick_icon.png")
SRC_MASCOT = os.path.join(HERE, "src", "chick_mascot.png")

DENS = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def cutout(img: Image.Image, tolerance: int = 26) -> Image.Image:
    """가장자리에서 이어진 흰 배경을 투명하게 만든다(내부 흰색은 유지)."""
    img = img.convert("RGBA")
    w, h = img.size
    px = img.load()
    visited = bytearray(w * h)
    q = deque()

    def near_white(x, y):
        r, g, b, a = px[x, y]
        return r >= 255 - tolerance and g >= 255 - tolerance and b >= 255 - tolerance and a > 0

    for x in range(w):
        for y in (0, h - 1):
            if near_white(x, y) and not visited[y * w + x]:
                visited[y * w + x] = 1
                q.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            if near_white(x, y) and not visited[y * w + x]:
                visited[y * w + x] = 1
                q.append((x, y))

    while q:
        x, y = q.popleft()
        px[x, y] = (255, 255, 255, 0)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and not visited[ny * w + nx] and near_white(nx, ny):
                visited[ny * w + nx] = 1
                q.append((nx, ny))

    # 가장자리 한 겹을 살짝 부드럽게(흰 테두리 남지 않게)
    alpha = img.getchannel("A").filter(ImageFilter.GaussianBlur(1.2))
    img.putalpha(alpha)
    return img


def trim(img: Image.Image, pad_ratio: float = 0.02) -> Image.Image:
    box = img.getbbox()
    if box is None:
        return img
    img = img.crop(box)
    pad = int(max(img.size) * pad_ratio)
    canvas = Image.new("RGBA", (img.width + pad * 2, img.height + pad * 2), (0, 0, 0, 0))
    canvas.alpha_composite(img, (pad, pad))
    return canvas


def square_icon(img: Image.Image, size: int) -> Image.Image:
    """둥근 사각형 아이콘(구형 런처용)."""
    return img.convert("RGBA").resize((size, size), Image.LANCZOS)


def round_icon(img: Image.Image, size: int) -> Image.Image:
    src = img.convert("RGBA").resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(src, (0, 0), mask)
    return out


def foreground(chick: Image.Image, size: int) -> Image.Image:
    """어댑티브 아이콘 전경(안전 영역 66% 안에 병아리)."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    inner_h = int(size * 0.56)
    scale = inner_h / chick.height
    im = chick.resize((max(1, int(chick.width * scale)), inner_h), Image.LANCZOS)
    canvas.alpha_composite(im, ((size - im.width) // 2, (size - im.height) // 2))
    return canvas


def main():
    icon = Image.open(SRC_ICON)
    chick = trim(cutout(Image.open(SRC_MASCOT)))

    for name, mult in DENS.items():
        folder = os.path.join(RES, "mipmap-" + name)
        os.makedirs(folder, exist_ok=True)
        size = int(48 * mult)
        square_icon(icon, size).save(os.path.join(folder, "ic_launcher.png"))
        round_icon(icon, size).save(os.path.join(folder, "ic_launcher_round.png"))
        foreground(chick, int(108 * mult)).save(os.path.join(folder, "ic_launcher_foreground.png"))

    drawable = os.path.join(RES, "drawable-nodpi")
    os.makedirs(drawable, exist_ok=True)
    mascot_h = 360
    mascot = chick.resize((max(1, int(chick.width * mascot_h / chick.height)), mascot_h), Image.LANCZOS)
    mascot.save(os.path.join(drawable, "chick_mascot.webp"), "WEBP", quality=92, method=6)

    # 관리자 화면 미리보기용(첫 화면 배너와 같은 그림)
    print("icons ok · mascot", mascot.size)


if __name__ == "__main__":
    main()
