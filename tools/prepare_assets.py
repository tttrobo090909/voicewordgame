# -*- coding: utf-8 -*-
"""말해봐요! 에셋 준비: Noto Emoji 3D 512px PNG -> WebP 512 변환 + words.json 생성 + 검증."""
import json
import os
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from word_data import WORDS, CATEGORIES, AVATARS

BASE = "https://raw.githubusercontent.com/googlefonts/noto-emoji/main/3D/png/512/emoji_u{}.png"
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
CACHE = os.path.join(HERE, "cache")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
WORDS_DIR = os.path.join(ASSETS, "words")
AVATAR_DIR = os.path.join(ASSETS, "avatars")
CAT_DIR = os.path.join(ASSETS, "categories")

for d in (CACHE, WORDS_DIR, AVATAR_DIR, CAT_DIR):
    os.makedirs(d, exist_ok=True)


def fetch(code, out_path):
    url = BASE.format(code)
    req = urllib.request.Request(url, headers={"User-Agent": "pipi-voicewordgame/1.0"})
    with urllib.request.urlopen(req, timeout=30) as r:
        data = r.read()
    with open(out_path, "wb") as f:
        f.write(data)
    return out_path


def cached_fetch(code):
    path = os.path.join(CACHE, code + ".png")
    if os.path.exists(path) and os.path.getsize(path) > 500:
        return path
    for attempt in range(3):
        try:
            return fetch(code, path)
        except Exception as e:  # noqa: BLE001
            if attempt == 2:
                print("FAIL", code, e)
                return None
            time.sleep(1.2 * (attempt + 1))
    return None


def to_webp(src, dst, size, quality=90):
    if os.path.exists(dst) and os.path.getsize(dst) > 500:
        return os.path.getsize(dst)
    im = Image.open(src).convert("RGBA")
    if im.size != (size, size):
        im = im.resize((size, size), Image.LANCZOS)
    im.save(dst, "WEBP", quality=quality, method=4)
    return os.path.getsize(dst)


def resolve(code, alts):
    cands = [code] + list(alts or [])
    for c in cands:
        p = cached_fetch(c)
        if p:
            return c, p
    return None, None


def main():
    jobs = []
    for w in WORDS:
        wid, ko, en, cat, code = w[0], w[1], w[2], w[3], w[4]
        alts = w[7] if len(w) > 7 else []
        jobs.append((wid, ko, code, alts))
    extra = [(f"cat{c[0]}", c[1], c[3], []) for c in CATEGORIES]
    extra += [(f"av{i}", a[0], a[1], []) for i, a in enumerate(AVATARS)]
    extra += [("icon", "앱아이콘", "1f3a4", ["1f4ac", "1f5e3"])]

    results = {}

    def work(job):
        key, label, code, alts = job
        used, path = resolve(code, alts)
        return key, label, code, used, path

    with ThreadPoolExecutor(max_workers=12) as ex:
        for key, label, code, used, path in ex.map(work, jobs + extra):
            results[key] = (label, code, used, path)

    missing = [k for k, v in results.items() if v[3] is None]
    print("요청:", len(results), "실패:", len(missing), missing)

    # words
    words_json = []
    conv_jobs = []
    for w in WORDS:
        label, orig, used, path = results[w[0]]
        if path:
            conv_jobs.append((path, os.path.join(WORDS_DIR, "%03d.webp" % w[0]), 512, 90))
    for c in CATEGORIES:
        label, orig, used, path = results[f"cat{c[0]}"]
        if path:
            conv_jobs.append((path, os.path.join(CAT_DIR, "%d.webp" % c[0]), 256, 92))
    for i, a in enumerate(AVATARS):
        label, orig, used, path = results[f"av{i}"]
        if path:
            conv_jobs.append((path, os.path.join(AVATAR_DIR, "%02d.webp" % i), 256, 92))
    with ThreadPoolExecutor(max_workers=6) as ex:
        list(ex.map(lambda j: to_webp(*j), conv_jobs))

    total_bytes = 0
    for w in WORDS:
        wid, ko, en, cat, code = w[0], w[1], w[2], w[3], w[4]
        syn_ko, syn_en = w[5], w[6]
        label, orig, used, path = results[wid]
        if not path:
            continue
        dst = os.path.join(WORDS_DIR, "%03d.webp" % wid)
        sz = os.path.getsize(dst)
        total_bytes += sz
        words_json.append({
            "id": wid,
            "ko": ko,
            "en": en,
            "category": cat,
            "image": "words/%03d.webp" % wid,
            "synKo": [s for s in syn_ko if s != ko],
            "synEn": [s for s in syn_en if s.strip().lower() != en.strip().lower()],
            "art": "approx" if (used != code or (w[7] if len(w) > 7 else [])) else "exact",
        })

    cats = []
    for c in CATEGORIES:
        label, orig, used, path = results[f"cat{c[0]}"]
        cats.append({"id": c[0], "ko": c[1], "en": c[2], "image": "categories/%d.webp" % c[0]})

    avatars = []
    for i, a in enumerate(AVATARS):
        label, orig, used, path = results[f"av{i}"]
        avatars.append({"id": i, "name": a[0], "image": "avatars/%02d.webp" % i})

    payload = {
        "schema": 1,
        "appVersion": "1.0",
        "wordCount": len(words_json),
        "categories": cats,
        "avatars": avatars,
        "words": words_json,
    }
    with open(os.path.join(ASSETS, "words.json"), "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=1)

    # icon
    label, orig, used, path = results["icon"]
    icon_src = path

    print("단어:", len(words_json), "이미지 평균 KB: %.1f" % (total_bytes / max(1, len(words_json)) / 1024.0))
    approx = [w["ko"] for w in words_json if w["art"] == "approx"]
    print("근사/대체 그림(%d): %s" % (len(approx), ", ".join(approx)))
    # 중복 그림 검사
    from collections import Counter
    cnt = Counter(w["image"].replace("words/", "") for w in words_json)
    by_code = {}
    for w in words_json:
        pass
    codes = {}
    for w in WORDS:
        label, orig, used, path = results[w[0]]
        codes.setdefault(used, []).append(w[1])
    dup = {k: v for k, v in codes.items() if len(v) > 1}
    print("같은 그림 공유:", {k: v for k, v in dup.items()})
    print("ICON_SRC=" + (icon_src or "NONE"))


if __name__ == "__main__":
    main()
