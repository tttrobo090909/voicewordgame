package com.pipi.voicewordgame.admin

import java.util.Random

/**
 * 제목을 1.2초 안에 3번 연속 탭했는지 세는 순수 로직.
 * 조건을 만족하면 true 를 돌려주고 카운터를 비운다.
 */
class TapCounter(
    private val windowMs: Long = 1200L,
    private val required: Int = 3
) {
    private val taps = ArrayDeque<Long>()

    fun tap(now: Long): Boolean {
        taps.addLast(now)
        while (taps.isNotEmpty() && now - taps.first() > windowMs) {
            taps.removeFirst()
        }
        if (taps.size >= required) {
            taps.clear()
            return true
        }
        return false
    }

    fun reset() = taps.clear()
}

/** 관리자 잠금 해제용 랜덤 구구단 문제(2~9단). */
object Gugu {

    data class Question(val a: Int, val b: Int) {
        val answer: Int get() = a * b
        val text: String get() = "${a} × ${b} = ?"
    }

    fun next(random: Random = Random()): Question =
        Question(a = 2 + random.nextInt(8), b = 1 + random.nextInt(9))

    fun isCorrect(question: Question, input: String): Boolean {
        val value = input.trim().toIntOrNull() ?: return false
        return value == question.answer
    }
}
