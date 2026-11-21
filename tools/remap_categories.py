# -*- coding: utf-8 -*-
"""v2.0 카테고리 재편: 기존 10개 카테고리 250단어를 6개 카테고리로 빠짐없이 매핑한다.

- 기존 묶음을 기본값으로 옮기고, 어색한 낱말만 개별로 옮긴다(OVERRIDE).
- 결과: assets/words.json (schema 2, 카테고리 6개, hint 필드 포함)
- 검증: 250개 전부 매핑됐는지, 모든 그림 파일이 실제로 있는지 확인한다.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
SRC = os.path.join(ASSETS, "words.json")

# (id, ko, en)
CATEGORIES = [
    (1, "동물", "Animals"),
    (2, "음식·과일", "Food & Fruit"),
    (3, "탈것", "Vehicles"),
    (4, "사물·집", "Things & Home"),
    (5, "자연", "Nature"),
    (6, "사람·몸", "People & Body"),
]

# 기존 카테고리 -> 새 카테고리
BY_OLD = {
    1: 1,   # 동물
    2: 1,   # 바다·곤충
    3: 2,   # 과일·채소
    4: 2,   # 음식·간식
    5: 3,   # 탈것·교통
    6: 5,   # 자연·날씨·우주
    7: 6,   # 옷·신체
    8: 4,   # 집·생활용품
    9: 4,   # 학교·놀이·악기
    10: 4,  # 장소·직업·공룡
}

# 낱말 하나만 옮기는 예외
OVERRIDE = {
    "공룡": 1,      # 장소·직업 묶음에 있던 공룡 -> 동물
    "우주인": 6,    # 자연 묶음에 있던 사람 -> 사람·몸
    "의사": 6,
    "간호사": 6,
    "경찰관": 6,
    "소방관": 6,
    "선생님": 6,
    "요리사": 6,
    "공주": 6,
    "가방": 4,      # 몸에 걸치는 물건이지만 사물로
    "우산": 4,
}


def main():
    data = json.load(open(SRC, encoding="utf-8"))
    words = data["words"]

    out = []
    for w in words:
        cat = OVERRIDE.get(w["ko"], BY_OLD[w["category"]])
        if not (1 <= cat <= 6):
            sys.exit("카테고리 매핑 실패: %s" % w["ko"])
        item = {
            "id": w["id"],
            "ko": w["ko"],
            "en": w["en"],
            "category": cat,
            "image": w["image"],
            "synKo": w.get("synKo", []),
            "synEn": w.get("synEn", []),
            "hint": "",          # 커스텀 힌트(비어 있으면 첫 글자 + ○ 로 자동 생성)
        }
        out.append(item)

    # 검증
    if len(out) != 250:
        sys.exit("단어 수가 250이 아님: %d" % len(out))
    if len({w["id"] for w in out}) != 250:
        sys.exit("중복 id")
    counts = {}
    for w in out:
        counts[w["category"]] = counts.get(w["category"], 0) + 1
        path = os.path.join(ASSETS, w["image"])
        if not os.path.exists(path):
            sys.exit("그림 없음: %s (%s)" % (w["image"], w["ko"]))
    for cid, ko, _ in CATEGORIES:
        if counts.get(cid, 0) == 0:
            sys.exit("빈 카테고리: %s" % ko)

    payload = {
        "schema": 2,
        "appVersion": "2.0",
        "wordCount": len(out),
        "categories": [{"id": i, "ko": ko, "en": en} for i, ko, en in CATEGORIES],
        "words": out,
    }
    with open(SRC, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=1)

    print("저장:", SRC)
    for cid, ko, _ in CATEGORIES:
        print("  %d %-8s %d개" % (cid, ko, counts[cid]))
    print("  합계", sum(counts.values()))


if __name__ == "__main__":
    main()
