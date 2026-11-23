# -*- coding: utf-8 -*-
"""v2.4 단어·그림 정리(한 번만 돌리는 편집 스크립트).

`downloads/말해봐요_그림교체_제안목록.md` 의 A(3D 그림 새로 생성 14개) · B(그림에 맞춰 단어 교체 13개)를
반영하고, 남아 있던 한글 중복 3쌍(배/눈/풀)까지 정리한 뒤
(배는 과일 그대로 두고 배 그림을 '보트', 눈 그림은 '눈송이', 풀 그림은 '로션'으로)

- `tools/word_data.py`  : 6개 카테고리 기준의 단어 원장(카테고리 번호를 words.json 과 같은 1~6으로 통일)
- `app/src/main/assets/words.json` : 위 원장에서 그대로 만든 앱 데이터(schema 2)

를 함께 다시 쓴다. 그림 파일 자체는 건드리지 않는다(3D 새 그림은 별도로 배치).
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
sys.path.insert(0, HERE)

from word_data import WORDS, CATEGORIES as OLD_CATEGORIES  # 기존 원장(id·codepoint 보존용)  # noqa: E402

# 이미 6개 카테고리로 정리된 원장에 다시 돌려도 카테고리가 망가지지 않게 한다.
ALREADY_SIX = len(OLD_CATEGORIES) == 6

CATEGORIES = [
    (1, "동물", "Animals"),
    (2, "음식·과일", "Food & Fruit"),
    (3, "탈것", "Vehicles"),
    (4, "사물·집", "Things & Home"),
    (5, "자연", "Nature"),
    (6, "사람·몸", "People & Body"),
]

# 그림을 새로 만드는 단어(로컬 3D 일러스트). Noto 코드 대신 "custom" 으로 적는다.
CUSTOM_ART = {120, 121, 124, 138, 156, 181, 183, 188, 189, 190, 191, 220, 230, 231}

# 단어를 그림에 맞춰 바꾸는 항목(제안목록 B) + 중복 정리 3건 + 안경 유사어 정리
CHANGES = {
    # --- B. 그림에 맞춰 단어 교체(13)
    44: dict(ko="코알라", en="Koala", code="1f428", syn_ko=["코알라"], syn_en=["koala"]),
    56: dict(ko="아보카도", en="Avocado", code="1f951", syn_ko=["아보카도"], syn_en=["avocado"]),
    83: dict(ko="카레", en="Curry", code="1f35b", syn_ko=["카레", "카레라이스"], syn_en=["curry", "curry rice"]),
    139: dict(ko="화산", en="Volcano", code="1f30b", syn_ko=["화산"], syn_en=["volcano"]),
    227: dict(ko="성", en="Castle", code="1f3f0", syn_ko=["성", "성곽"], syn_en=["castle", "fortress"]),
    229: dict(ko="약", en="Medicine", code="1f48a", syn_ko=["약", "알약"], syn_en=["medicine", "pill"]),
    233: dict(ko="회전목마", en="Carousel", code="1f3a0", syn_ko=["회전목마", "목마"],
              syn_en=["carousel", "merry-go-round"]),
    234: dict(ko="서커스", en="Circus", code="1f3aa", syn_ko=["서커스", "곡예", "써커스"], syn_en=["circus"]),
    236: dict(ko="돋보기", en="Magnifying glass", code="1f50d", syn_ko=["돋보기", "확대경"],
              syn_en=["magnifying glass", "magnifier"]),
    237: dict(ko="선물", en="Gift", code="1f381", syn_ko=["선물"], syn_en=["gift", "present"]),
    238: dict(ko="도시락", en="Lunchbox", code="1f371", syn_ko=["도시락", "도시락통", "런치박스"],
              syn_en=["lunchbox", "lunch box", "bento"]),
    239: dict(ko="커피", en="Coffee", code="2615", syn_ko=["커피", "커피잔"], syn_en=["coffee"]),
    240: dict(ko="크루아상", en="Croissant", code="1f950", syn_ko=["크루아상"], syn_en=["croissant"]),
    # --- 기존에 남아 있던 한글 단어 중복 3쌍 정리
    110: dict(ko="보트", en="Boat", code="1f6a2", syn_ko=["보트", "배", "여객선"], syn_en=["boat", "ship"]),
    131: dict(ko="눈송이", en="Snowflake", code="2744", syn_ko=["눈송이", "눈", "함박눈"],
              syn_en=["snowflake", "snow"]),
    # 풀(그림 1f33f) 은 그대로 두고, 풀처럼 보이던 로션 병(1f9f4)을 그림에 맞는 단어로 바꿔 중복을 없앤다.
    144: dict(ko="풀", en="Grass", code="1f33f", syn_ko=["풀", "잔디", "풀잎"], syn_en=["grass", "lawn"]),
    206: dict(ko="로션", en="Lotion", code="1f9f4", syn_ko=["로션", "핸드크림"],
              syn_en=["lotion", "moisturizer"]),
    # 돋보기가 새 단어가 되어 안경의 유사어에서는 뺀다(정답이 겹치지 않게).
    152: dict(ko="안경", en="Glasses", code="1f453", syn_ko=["안경"], syn_en=["glasses", "spectacles"]),
}

# 기존 word_data.py 의 10개 카테고리 -> v2.0 이후 6개 카테고리(remap_categories.py 와 같은 규칙)
BY_OLD = {1: 1, 2: 1, 3: 2, 4: 2, 5: 3, 6: 5, 7: 6, 8: 4, 9: 4, 10: 4}

# remap_categories.py 가 낱말 하나만 옮긴 예외(그대로 유지한다).
OVERRIDE = {
    "공룡": 1, "우주인": 6, "의사": 6, "간호사": 6, "경찰관": 6,
    "소방관": 6, "선생님": 6, "요리사": 6, "공주": 6,
    "가방": 4, "우산": 4,
}


def q(text):
    return '"%s"' % text.replace('"', '\\"')


def list_literal(items):
    return "[" + ", ".join(q(i) for i in items) + "]"


def build_words():
    rows = []
    for w in WORDS:
        wid, ko, en, old_cat, code, syn_ko, syn_en = w[0], w[1], w[2], w[3], w[4], w[5], w[6]
        category = old_cat if ALREADY_SIX else OVERRIDE.get(ko, BY_OLD[old_cat])
        row = dict(id=wid, ko=ko, en=en, category=category, code=code,
                   syn_ko=list(syn_ko), syn_en=list(syn_en))
        if wid in CUSTOM_ART:
            row["code"] = "custom"
        if wid in CHANGES:
            row.update(CHANGES[wid])
        if wid in CUSTOM_ART:
            row["code"] = "custom"
        rows.append(row)
    return rows


def write_word_data(rows):
    lines = []
    lines.append("# -*- coding: utf-8 -*-")
    lines.append('"""말해봐요! 250단어 데이터 (카테고리 6개 · 앱 assets/words.json 과 1:1).')
    lines.append("")
    lines.append("각 항목: (id, ko, en, category_id(1~6), 그림 코드, syn_ko, syn_en)")
    lines.append('그림 코드는 Noto Emoji 파일명 규칙(emoji_u{code}.png, ZWJ는 \'_\').')
    lines.append('CUSTOM_ART 에 든 단어는 Noto 대신 앱에 직접 넣은 3D 일러스트라 코드가 "custom" 이다.')
    lines.append('"""')
    lines.append("")
    lines.append("CATEGORIES = [")
    for cid, ko, en in CATEGORIES:
        lines.append("    (%d, %s, %s)," % (cid, q(ko), q(en)))
    lines.append("]")
    lines.append("")
    lines.append("# 화면에 쓸 3D 그림을 직접 만든 단어 id(Noto Emoji 아님).")
    lines.append("CUSTOM_ART = {%s}" % ", ".join(str(i) for i in sorted(CUSTOM_ART)))
    lines.append("")
    lines.append("# id, ko, en, cat(1~6), code, synKo, synEn")
    lines.append("WORDS = [")
    current = None
    names = {c[0]: c[1] for c in CATEGORIES}
    for row in rows:
        if row["category"] != current:
            current = row["category"]
            lines.append("")
            lines.append("    # %d. %s" % (current, names[current]))
        lines.append("    (%d, %s, %s, %d, %s, %s, %s)," % (
            row["id"], q(row["ko"]), q(row["en"]), row["category"], q(row["code"]),
            list_literal(row["syn_ko"]), list_literal(row["syn_en"])))
    lines.append("]")
    lines.append("")
    with open(os.path.join(HERE, "word_data.py"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def write_words_json(rows):
    words = []
    for row in rows:
        words.append({
            "id": row["id"],
            "ko": row["ko"],
            "en": row["en"],
            "category": row["category"],
            "image": "words/%03d.webp" % row["id"],
            "synKo": [s for s in row["syn_ko"] if s != row["ko"]],
            "synEn": [s for s in row["syn_en"] if s.strip().lower() != row["en"].strip().lower()],
            "hint": "",
        })
    payload = {
        "schema": 2,
        "appVersion": "2.4",
        "wordCount": len(words),
        "categories": [{"id": c[0], "ko": c[1], "en": c[2]} for c in CATEGORIES],
        "words": words,
    }
    with open(os.path.join(ASSETS, "words.json"), "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=1)


def report(rows):
    from collections import Counter
    print("단어:", len(rows))
    for key in ("ko", "en"):
        dup = {k: v for k, v in Counter(r[key] for r in rows).items() if v > 1}
        print("%s 중복: %s" % (key, dup or "0건"))
    dup_codes = {k: v for k, v in Counter(r["code"] for r in rows).items() if v > 1}
    print("그림 코드 중복:", dup_codes or "0건")
    counts = Counter(r["category"] for r in rows)
    print("카테고리별:", dict(sorted(counts.items())))


if __name__ == "__main__":
    rows = build_words()
    write_word_data(rows)
    write_words_json(rows)
    report(rows)
