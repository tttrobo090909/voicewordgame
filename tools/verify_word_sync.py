#!/usr/bin/env python3
"""말해봐요!(voicewordgame)의 단어·그림이 들어봐요!(listengame)와 100% 같은지 확인한다.

v2.9부터 두 앱은 같은 420단어·420장 그림을 쓴다.
`words.json`의 `categories`·`words`·`wordCount`가 완전히 같아야 하고,
`assets/words/*.webp` 파일 이름과 내용(SHA-256)이 모두 같아야 한다.
`appVersion`은 앱마다 다르므로(말해봐요 2.9 / 들어봐요 1.1) 비교에서 뺀다.

사용:
    python3 tools/verify_word_sync.py            # 형제 폴더 ../listengame 과 비교
    python3 tools/verify_word_sync.py /path/to/listengame
"""
import hashlib
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_OTHER = os.path.normpath(os.path.join(HERE, "..", "..", "listengame"))
ASSETS = os.path.join("app", "src", "main", "assets")


def load_words(project):
    with open(os.path.join(project, ASSETS, "words.json"), encoding="utf-8") as f:
        return json.load(f)


def image_hashes(project):
    folder = os.path.join(project, ASSETS, "words")
    out = {}
    for name in sorted(os.listdir(folder)):
        if not name.endswith(".webp"):
            continue
        with open(os.path.join(folder, name), "rb") as f:
            out[name] = hashlib.sha256(f.read()).hexdigest()
    return out


def main():
    other = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_OTHER
    mine = os.path.normpath(os.path.join(HERE, ".."))

    a, b = load_words(other), load_words(mine)
    problems = []

    if a["categories"] != b["categories"]:
        problems.append("categories 가 다릅니다")
    if a["words"] != b["words"]:
        problems.append("words 배열이 다릅니다")
    if a.get("wordCount") != b.get("wordCount"):
        problems.append("wordCount 가 다릅니다 (%s vs %s)" % (a.get("wordCount"), b.get("wordCount")))
    if len(b["words"]) != b.get("wordCount"):
        problems.append("wordCount 와 실제 단어 수가 다릅니다")

    ha, hb = image_hashes(other), image_hashes(mine)
    if set(ha) != set(hb):
        only_a = sorted(set(ha) - set(hb))
        only_b = sorted(set(hb) - set(ha))
        problems.append("그림 파일 목록이 다릅니다: %s에만 %s, %s에만 %s"
                        % (os.path.basename(other), only_a, os.path.basename(mine), only_b))
    diff = [k for k in ha if k in hb and ha[k] != hb[k]]
    if diff:
        problems.append("그림 내용이 다른 파일: %s" % diff)

    print("들어봐요!   : %d단어 · %d장 (appVersion %s)" % (len(a["words"]), len(ha), a.get("appVersion")))
    print("말해봐요!   : %d단어 · %d장 (appVersion %s)" % (len(b["words"]), len(hb), b.get("appVersion")))
    if problems:
        for p in problems:
            print("불일치 -", p)
        return 1
    print("일치 - categories·words·wordCount 와 그림 %d장 해시가 100%% 같습니다." % len(ha))
    return 0


if __name__ == "__main__":
    sys.exit(main())
