package com.pipi.voicewordgame.game

import java.util.Random

/**
 * 한 판의 진행 상태: 문제 큐(한 바퀴 다 보기 전엔 같은 문제가 다시 나오지 않음),
 * 현재 문제, 맞힌 문제 수.
 */
class GameSession(private val random: Random = Random()) {

    private var pool: List<Word> = emptyList()
    private var deck: MutableList<Word> = mutableListOf()
    private var lastServedId: Int = -1

    var current: Word? = null
        private set
    var correctCount: Int = 0
        private set
    var servedCount: Int = 0
        private set

    fun setPool(words: List<Word>) {
        pool = words
        deck = mutableListOf()
        lastServedId = -1
        current = null
        correctCount = 0
        servedCount = 0
    }

    fun poolSize(): Int = pool.size

    fun next(): Word? {
        if (pool.isEmpty()) return null
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
}
