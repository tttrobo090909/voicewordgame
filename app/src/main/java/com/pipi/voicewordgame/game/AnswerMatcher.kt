package com.pipi.voicewordgame.game

/** 말한 문장에서 정답 단어를 찾아낸다(조사·어미·띄어쓰기·문장부호 무시). */
object AnswerMatcher {

    private val trailingSuffixes = listOf(
        "이에요", "예요", "입니다", "이에용", "이야", "야", "아", "이요", "요",
        "은", "는", "이", "가", "을", "를", "도", "에", "랑", "와", "과"
    )

    fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw.lowercase()) {
            if (ch.isLetterOrDigit()) sb.append(ch)
        }
        return sb.toString()
    }

    private fun stripSuffix(normalized: String): List<String> {
        val out = ArrayList<String>(4)
        out.add(normalized)
        for (suffix in trailingSuffixes) {
            if (normalized.length > suffix.length && normalized.endsWith(suffix)) {
                out.add(normalized.substring(0, normalized.length - suffix.length))
            }
        }
        return out
    }

    private fun targetsOf(word: Word, lang: String): List<String> {
        val primary = word.text(lang)
        val secondary = word.text(if (lang == "en") "ko" else "en")
        val syns = if (lang == "en") word.synEn + word.synKo else word.synKo + word.synEn
        return (listOf(primary, secondary) + syns)
            .filter { it.isNotBlank() }
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
    }

    /** 후보 문장들 중 정답이 되는 첫 문장을 돌려준다. 없으면 null. */
    fun firstMatch(candidates: List<String>, word: Word, lang: String): String? =
        firstMatchAny(candidates, listOf(word), lang)

    /**
     * 같은 그림을 쓰는 단어(예: 침대/이불)까지 정답으로 인정한다.
     * 그림이 하나뿐인 이모지라서 생기는 억울한 오답을 막기 위한 장치.
     */
    fun firstMatchAny(candidates: List<String>, words: List<Word>, lang: String): String? {
        if (candidates.isEmpty() || words.isEmpty()) return null
        val targets = words.flatMap { targetsOf(it, lang) }
        if (targets.isEmpty()) return null
        for (raw in candidates) {
            val norm = normalize(raw)
            if (norm.isEmpty()) continue
            val variants = stripSuffix(norm)
            for (variant in variants) {
                for (target in targets) {
                    if (variant == target) return raw
                    if (target.length >= 2 && variant.length <= target.length + 4 && variant.contains(target)) {
                        return raw
                    }
                }
            }
        }
        return null
    }
}
