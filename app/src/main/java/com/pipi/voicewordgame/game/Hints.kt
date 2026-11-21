package com.pipi.voicewordgame.game

/**
 * 힌트 문구.
 *
 * - 관리자가 힌트를 직접 적었으면 그것을 쓴다.
 * - 힌트가 없는 **1글자 한글** 단어는 글자가 그대로 드러나지 않도록 **초성**만 보여 준다.
 *   예) 손 → ㅅ, 코 → ㅋ, 공 → ㄱ  (그림을 보고 떠올리게 하려는 것이라 글자 스포일러를 막는다)
 * - 그 밖(2글자 이상 한글, 영문)은 기존처럼 첫 글자만 보여 주고 나머지는 ○ 로 가린다.
 *   예) 인형 → 인○, Bee → B○○
 */
object Hints {

    private const val HANGUL_SYLLABLE_FIRST = 0xAC00
    private const val HANGUL_SYLLABLE_LAST = 0xD7A3

    /** 유니코드 한글 음절의 초성 19자(ㄱ~ㅎ). */
    private val CHOSEONG = listOf(
        "ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ", "ㅂ", "ㅃ", "ㅅ",
        "ㅆ", "ㅇ", "ㅈ", "ㅉ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    )

    fun defaultHint(word: Word, lang: String): String {
        val text = word.text(lang).trim()
        if (text.isEmpty()) return ""
        if (text.length == 1 && isHangulSyllable(text[0])) return choseong(text[0])
        return text.substring(0, 1) + "○".repeat((text.length - 1).coerceAtLeast(0))
    }

    fun isCustom(word: Word): Boolean = word.hint.isNotBlank()

    fun hint(word: Word, lang: String): String =
        if (isCustom(word)) word.hint.trim() else defaultHint(word, lang)

    /** 그림 위 배지에 크게 띄우는 문구. */
    fun badge(word: Word, lang: String): String {
        val length = word.text(lang).trim().length
        return "힌트: ${hint(word, lang)} (${length}글자)"
    }

    /** 한글 음절 한 글자의 초성. 예) '손' → "ㅅ". 음절이 아니면 글자 그대로 돌려준다. */
    fun choseong(char: Char): String {
        if (!isHangulSyllable(char)) return char.toString()
        val index = (char.code - HANGUL_SYLLABLE_FIRST) / (21 * 28)
        return CHOSEONG.getOrElse(index) { char.toString() }
    }

    private fun isHangulSyllable(char: Char): Boolean =
        char.code in HANGUL_SYLLABLE_FIRST..HANGUL_SYLLABLE_LAST
}
