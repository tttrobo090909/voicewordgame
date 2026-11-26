package com.pipi.voicewordgame.game

import java.util.Random
import kotlin.math.min

/**
 * 한 판의 진행 상태: 문제 큐(한 바퀴 다 보기 전엔 같은 문제가 다시 나오지 않음),
 * 현재 문제, 맞힌 문제 수, 한 판에 낼 문제 수(v2.3).
 *
 * v2.3: 관리자 설정의 `한 판 문제 수`만큼만 출제한다. 고른 카테고리에 남은 단어가
 * 그보다 적으면 있는 만큼만 내고 끝난다(같은 문제를 다시 내지 않는다).
 */
class GameSession(private val random: Random = Random()) {

    private var pool: List<Word> = emptyList()
    private var deck: MutableList<Word> = mutableListOf()
    private var lastServedId: Int = -1

    /** 이번 판에 낼 문제 수 상한. 설정이 없으면 전체. */
    var limit: Int = Int.MAX_VALUE
        private set

    var current: Word? = null
        private set
    var correctCount: Int = 0
        private set
    var servedCount: Int = 0
        private set

    fun setPool(words: List<Word>, questions: Int = Int.MAX_VALUE) {
        pool = words
        limit = if (questions <= 0) words.size else min(questions, words.size)
        deck = mutableListOf()
        lastServedId = -1
        current = null
        correctCount = 0
        servedCount = 0
    }

    fun poolSize(): Int = pool.size

    /** 이번 판에 실제로 낼 수 있는 문제 수(설정값과 남은 단어 중 작은 쪽). */
    fun plannedCount(): Int = limit

    /** 다음 문제. 설정한 문제 수를 다 냈으면 null(게임 끝). */
    fun next(): Word? {
        if (pool.isEmpty()) return null
        if (servedCount >= limit) return null
        if (deck.isEmpty()) {
            deck = pool.toMutableList()
            deck.shuffle(random)
            if (deck.size > 1 && deck[0].id == lastServedId) {
                deck.add(deck.removeAt(0))
            }
        }
        val word = deck.removeAt(0)
        current = word
        lastServedId = word.id
        servedCount += 1
        return word
    }

    fun markCorrect() {
        correctCount += 1
    }

    /** 테스트·화면 캡처용: 지금 문제를 지정한 단어로 바꾼다(문제 수·정답 수는 그대로). */
    internal fun replaceCurrentForTest(word: Word) {
        current = word
    }
}
