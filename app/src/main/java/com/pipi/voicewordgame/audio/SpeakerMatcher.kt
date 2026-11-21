package com.pipi.voicewordgame.audio

import com.pipi.voicewordgame.game.Player
import com.pipi.voicewordgame.game.VoiceProfile
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/** 등록된 목소리와 비교해 누가 말했는지 고른다. */
object SpeakerMatcher {

    data class Match(val playerId: Long, val score: Float, val runnerUp: Float, val confident: Boolean)

    fun match(profile: VoiceProfile, players: List<Player>): Match? {
        val candidates = players.filter { it.profile != null }
        if (candidates.isEmpty()) return null

        val scored = candidates.map { p ->
            p.id to score(profile, p.profile!!)
        }.sortedByDescending { it.second }

        val best = scored.first()
        val second = if (scored.size > 1) scored[1].second else 0f
        val confident = best.second >= 0.62f && (scored.size == 1 || best.second - second >= 0.035f)
        return Match(best.first, best.second, second, confident)
    }

    private fun score(a: VoiceProfile, b: VoiceProfile): Float {
        var total = 0f
        var weight = 0f

        if (a.pitch > 0f && b.pitch > 0f) {
            val ratio = max(a.pitch / b.pitch, b.pitch / a.pitch)
            val sim = (1.0 - ln(ratio.toDouble()) / ln(1.9)).coerceIn(0.0, 1.0)
            total += (0.55 * sim).toFloat()
            weight += 0.55f
        }
        if (a.centroid > 0f && b.centroid > 0f) {
            val ratio = max(a.centroid / b.centroid, b.centroid / a.centroid)
            val sim = (1.0 - ln(ratio.toDouble()) / ln(1.8)).coerceIn(0.0, 1.0)
            total += (0.25 * sim).toFloat()
            weight += 0.25f
        }
        val bands = cosine(a.bands, b.bands)
        total += 0.20f * bands
        weight += 0.20f

        return if (weight <= 0f) 0f else (total / weight).coerceIn(0f, 1f)
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val n = minOf(a.size, b.size)
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in 0 until n) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na <= 1e-9 || nb <= 1e-9) return 0f
        return (dot / (sqrt(na) * sqrt(nb))).toFloat().coerceIn(0f, 1f)
    }

    fun same(a: VoiceProfile, b: VoiceProfile): Boolean = abs(score(a, b) - 1f) < 0.25f
}
