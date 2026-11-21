package com.pipi.voicewordgame.game

/**
 * 힌트 문구.
 * 관리자가 힌트를 직접 적었으면 그것을 쓰고, 아니면 첫 글자만 보여 주고 나머지는 ○ 로 가린다.
 * 예) "인형" → "인○" (2글자)
 */
object Hints {

    fun defaultHint(word: Word, lang: String): String {
        val text = word.text(lang).trim()
        if (text.isEmpty()) return ""
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
}
