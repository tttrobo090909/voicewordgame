package com.pipi.voicewordgame

import com.pipi.voicewordgame.audio.SpeakerMatcher
import com.pipi.voicewordgame.audio.VoiceFeatureExtractor
import com.pipi.voicewordgame.game.Player
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** 합성한 모음 비슷한 소리로 음높이 추정과 화자 매칭이 동작하는지 확인한다. */
class VoiceFeatureTest {

    private fun tone(f0: Double, seconds: Double = 2.0, amp: Double = 0.22, seed: Long = 7L): ShortArray {
        val sampleRate = 16000
        val n = (sampleRate * seconds).toInt()
        val out = ShortArray(n)
        val rnd = java.util.Random(seed)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            var v = 0.0
            for (h in 1..8) {
                v += sin(2 * PI * f0 * h * t) / h
            }
            v = v * amp + (rnd.nextDouble() - 0.5) * 0.0015
            val s = (v * 32767).toInt().coerceIn(-32768, 32767)
            out[i] = s.toShort()
        }
        return out
    }

    @Test
    fun estimatesPitchWithinTolerance() {
        val profile = VoiceFeatureExtractor.analyze(tone(180.0))
        assertNotNull(profile)
        val pitch = profile!!.pitch
        assertTrue("pitch=$pitch", abs(pitch - 180.0) / 180.0 < 0.06)
        assertTrue(profile.bands.size == 13)
        assertTrue(profile.centroid > 50f)
    }

    @Test
    fun estimatesHighPitch() {
        val profile = VoiceFeatureExtractor.analyze(tone(320.0))
        assertNotNull(profile)
        val pitch = profile!!.pitch
        assertTrue("pitch=$pitch", abs(pitch - 320.0) / 320.0 < 0.08)
    }

    @Test
    fun sameVoiceMatchesConfidently() {
        val a = VoiceFeatureExtractor.analyze(tone(200.0, seed = 11))!!
        val b = VoiceFeatureExtractor.analyze(tone(200.0, seed = 99))!!
        val child = Player(id = 1, name = "딸", profile = a)
        val mom = Player(id = 2, name = "엄마", profile = VoiceFeatureExtractor.analyze(tone(330.0))!!)
        val result = SpeakerMatcher.match(b, listOf(child, mom))
        assertNotNull(result)
        assertTrue("score=${result!!.score}", result.playerId == 1L)
        assertTrue(result.confident)
    }

    @Test
    fun differentVoiceIsNotConfident() {
        val low = VoiceFeatureExtractor.analyze(tone(140.0))!!
        val high = VoiceFeatureExtractor.analyze(tone(360.0))!!
        val result = SpeakerMatcher.match(high, listOf(Player(id = 1, name = "아빠", profile = low)))
        assertTrue(result != null && !result.confident)
    }

    @Test
    fun silenceProducesNoProfile() {
        val silence = ShortArray(16000) { 3 }
        assertTrue(VoiceFeatureExtractor.analyze(silence) == null)
    }
}
