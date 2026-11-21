package com.pipi.voicewordgame.game

import java.util.Random

class Player(
    val id: Long,
    var name: String,
    var avatarId: Int = 0,
    var profile: VoiceProfile? = null,
    var score: Int = 0
) {
    fun avatar(): AvatarInfo? = WordRepository.avatars.firstOrNull { it.id == avatarId }
        ?: WordRepository.avatars.firstOrNull()

    fun initial(): String = name.trim().take(1)
}

/**
 * 한 게임 세션: 문제 큐, 점수, 마지막 득점(점수 옮기기용), 대기 중인 득점.
 */
class GameSession(val players: MutableList<Player>) {

    private val random = Random(System.currentTimeMillis())
    private var pool: List<Word> = emptyList()
    private var deck: MutableList<Word> = mutableListOf()
    private var lastServedId: Int = -1

    var current: Word? = null
        private set
    var servedCount: Int = 0
        private set

    var lastAwardPlayerId: Long? = null
        private set
    var lastAwardWordId: Int? = null
        private set
    var pendingWordId: Int? = null
        private set

    fun setPool(words: List<Word>) {
        pool = words
        deck = mutableListOf()
        lastServedId = -1
    }

    fun hasPool(): Boolean = pool.isNotEmpty()

    fun next(): Word? {
        if (pool.isEmpty()) return null
        if (deck.isEmpty()) {
            deck = pool.toMutableList()
            deck.shuffle(random)
            if (deck.size > 1 && deck[0].id == lastServedId) {
                val moved = deck.removeAt(0)
                deck.add(moved)
            }
        }
        val word = deck.removeAt(0)
        current = word
        lastServedId = word.id
        servedCount += 1
        pendingWordId = null
        return word
    }

    fun scoreOf(playerId: Long): Int = players.firstOrNull { it.id == playerId }?.score ?: 0

    fun award(playerId: Long, wordId: Int?) {
        val p = players.firstOrNull { it.id == playerId } ?: return
        p.score += 1
        lastAwardPlayerId = p.id
        lastAwardWordId = wordId
        pendingWordId = null
    }

    fun markPending(wordId: Int?) {
        pendingWordId = wordId
    }

    fun clearPending() {
        pendingWordId = null
    }

    /** 방금 준 점수를 다른 사람에게 옮긴다. */
    fun moveLastAwardTo(playerId: Long): Boolean {
        val fromId = lastAwardPlayerId ?: return false
        if (fromId == playerId) return false
        val from = players.firstOrNull { it.id == fromId } ?: return false
        val to = players.firstOrNull { it.id == playerId } ?: return false
        from.score = (from.score - 1).coerceAtLeast(0)
        to.score += 1
        lastAwardPlayerId = to.id
        return true
    }

    fun resetScores() {
        players.forEach { it.score = 0 }
        lastAwardPlayerId = null
        lastAwardWordId = null
        pendingWordId = null
        servedCount = 0
    }

    fun ranking(): List<Player> = players.sortedByDescending { it.score }
}
