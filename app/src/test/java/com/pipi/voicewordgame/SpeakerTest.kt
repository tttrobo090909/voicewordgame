package com.pipi.voicewordgame

import android.content.Context
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.test.core.app.ApplicationProvider
import com.pipi.voicewordgame.tts.Speaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowTextToSpeech
import java.io.File
import java.time.Duration
import java.util.Locale

/**
 * v2.10 TTS (`들어봐요!` v1.2 의 개선 1번):
 * - 한국어는 구글 TTS → 기기 기본, 영어는 삼성 TTS → 구글 TTS → 기기 기본.
 * - 목소리는 한국어 `ko-KR-language`, 영어 `en-AU-language` → `en-US-language` 순.
 * - 두 번 모두 속도 0.90 · 음높이 1.00, 1번째 완료 → 350ms → 2번째, 안전 타이머 4초.
 *
 * Robolectric 의 TTS 그림자는 초기화 결과를 스스로 알려 주지 않으므로,
 * [wakeEngine] 으로 마지막에 만든 엔진에 성공/실패를 직접 알려 준다(실기기의 엔진 초기화 콜백과 같다).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeakerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 실기기처럼 한국어·영어(호주/미국) 음성이 깔려 있는 것으로 둔다.
        ShadowTextToSpeech.addLanguageAvailability(Locale.forLanguageTag("ko-KR"))
        ShadowTextToSpeech.addLanguageAvailability(Locale.forLanguageTag("en-AU"))
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
    }

    // -------------------------------------------------- 엔진 고르기

    @Test
    fun koreanUsesGoogleAndEnglishUsesSamsungFirst() {
        assertEquals(
            "한국어는 구글 TTS → 기기 기본",
            listOf(Speaker.GOOGLE_TTS_ENGINE, null),
            Speaker.engineCandidates("ko-KR")
        )
        assertEquals(
            "영어는 삼성 TTS → 구글 TTS → 기기 기본(en-US)",
            listOf(Speaker.SAMSUNG_TTS_ENGINE, Speaker.GOOGLE_TTS_ENGINE, null),
            Speaker.engineCandidates("en-US")
        )
        assertEquals(
            "영어는 삼성 TTS → 구글 TTS → 기기 기본(en-AU)",
            listOf(Speaker.SAMSUNG_TTS_ENGINE, Speaker.GOOGLE_TTS_ENGINE, null),
            Speaker.engineCandidates("en-AU")
        )
    }

    @Test
    fun koreanEngineOpensGoogleTtsFirst() {
        val speaker = Speaker(context)
        var ready: Boolean? = null
        speaker.init("ko-KR") { ready = it }

        assertEquals("구글 TTS 로 연다", Speaker.GOOGLE_TTS_ENGINE, speaker.enginePackageForTest("ko-KR"))
        assertNull("영어 엔진은 따로 연다", speaker.enginePackageForTest("en-US"))
        assertNull("아직 초기화 전", ready)

        wakeEngine(TextToSpeech.SUCCESS)
        assertEquals(true, ready)
        assertTrue(speaker.isReadyForTest("ko-KR"))
        assertFalse(speaker.isReadyForTest("en-US"))
    }

    @Test
    fun koreanFallsBackToTheSystemEngineWhenGoogleIsMissing() {
        val speaker = Speaker(context)
        var ready: Boolean? = null
        speaker.init("ko-KR") { ready = it }
        assertEquals(Speaker.GOOGLE_TTS_ENGINE, speaker.enginePackageForTest("ko-KR"))

        wakeEngine(TextToSpeech.ERROR) // 구글 TTS 없음 → 기기 기본 엔진으로 다시 시도
        assertNull("기기 기본 엔진", speaker.enginePackageForTest("ko-KR"))
        assertNull("아직 준비 전", ready)

        wakeEngine(TextToSpeech.SUCCESS)
        assertEquals(true, ready)
    }

    @Test
    fun englishTriesSamsungThenGoogleThenTheSystemEngine() {
        val speaker = Speaker(context)
        var ready: Boolean? = null
        speaker.init("en-US") { ready = it }
        assertEquals("삼성 TTS 로 먼저", Speaker.SAMSUNG_TTS_ENGINE, speaker.enginePackageForTest("en-US"))

        wakeEngine(TextToSpeech.ERROR)
        assertEquals("없으면 구글 TTS", Speaker.GOOGLE_TTS_ENGINE, speaker.enginePackageForTest("en-US"))

        wakeEngine(TextToSpeech.ERROR)
        assertNull("그다음 기기 기본 엔진", speaker.enginePackageForTest("en-US"))
        assertNull(ready)

        wakeEngine(TextToSpeech.SUCCESS)
        assertEquals(true, ready)
        assertTrue(speaker.isReadyForTest("en-US"))
    }

    // -------------------------------------------------- 목소리 고르기

    @Test
    fun koreanPrefersTheOfflineKoKrLanguageVoice() {
        // 구글 TTS 처럼: 온라인 목소리와 오프라인 `ko-KR-language` 가 함께 있다.
        ShadowTextToSpeech.addVoice(voice("ko-kr-x-iss", KOREAN, network = true))
        ShadowTextToSpeech.addVoice(voice("ko-KR-language", KOREAN, network = false))

        val speaker = readySpeaker("ko-KR")
        var finished = false
        speaker.speakTwice("사과", "ko-KR") { finished = true }
        idleFor(700)

        assertEquals("ko-KR-language 목소리", "ko-KR-language", speaker.voiceNameForTest("ko-KR"))
        assertTrue(finished)
    }

    @Test
    fun koreanUsesAnOfflineVoiceWhenTheLanguageVoiceIsNotListed() {
        ShadowTextToSpeech.addVoice(voice("ko-kr-x-iss", KOREAN, network = false))

        val speaker = readySpeaker("ko-KR")
        speaker.speakOnce("사과", "ko-KR") {}
        idle()

        assertEquals("오프라인 목소리", "ko-kr-x-iss", speaker.voiceNameForTest("ko-KR"))
    }

    @Test
    fun englishPrefersEnAuLanguageVoice() {
        // 삼성 TTS 처럼: 영어 목소리가 en-AU 로 들어 있다.
        ShadowTextToSpeech.addVoice(voice("en-US-language", Locale.US, network = false))
        ShadowTextToSpeech.addVoice(voice("en-AU-language", Locale.forLanguageTag("en-AU"), network = false))

        val speaker = readySpeaker("en-US")
        speaker.speakOnce("apple", "en-US") {}
        idle()

        assertEquals("영어는 en-AU-language 를 먼저 고른다", "en-AU-language", speaker.voiceNameForTest("en-US"))
    }

    @Test
    fun englishFallsBackToEnUsVoiceWhenThereIsNoEnAuVoice() {
        ShadowTextToSpeech.addVoice(voice("en-US-language", Locale.US, network = false))
        ShadowTextToSpeech.addVoice(voice("ko-KR-language", KOREAN, network = false))

        val speaker = readySpeaker("en-US")
        speaker.speakOnce("apple", "en-US") {}
        idle()

        assertEquals("en-US-language 로 폴백", "en-US-language", speaker.voiceNameForTest("en-US"))
    }

    @Test
    fun preferredLocalesStartWithKoKrAndEnAu() {
        assertEquals(
            listOf("en-AU", "en-US", "en-GB", "en"),
            Speaker.preferredLocales("en-US").map { it.toLanguageTag() }
        )
        assertEquals(listOf("ko-KR"), Speaker.preferredLocales("ko-KR").map { it.toLanguageTag() })
    }

    // -------------------------------------------------- 속도·음높이·읽는 횟수

    @Test
    fun bothReadingsUsePointNineRateAndOnePitch() {
        val speaker = readySpeaker("ko-KR")
        var finished = 0
        speaker.speakTwice("사과", "ko-KR") { finished += 1 }
        idleFor(700)

        assertEquals("1번째·2번째 모두 0.90", listOf(0.90f, 0.90f), speaker.appliedRates.toList())
        assertEquals("음높이 1.00", listOf(1.00f, 1.00f), speaker.appliedPitches.toList())
        assertEquals("완료 알림은 한 번", 1, finished)
    }

    @Test
    fun paceConstantsAreUnchanged() {
        assertEquals(0.90f, Speaker.SPEECH_RATE, 0.0001f)
        assertEquals(0.90f, Speaker.FIRST_SPEECH_RATE, 0.0001f)
        assertEquals(0.90f, Speaker.SECOND_SPEECH_RATE, 0.0001f)
        assertEquals(1.00f, Speaker.PITCH, 0.0001f)
        assertEquals(0.90f, Speaker.rateFor(1), 0.0001f)
        assertEquals(0.90f, Speaker.rateFor(2), 0.0001f)
        assertEquals(350L, Speaker.PAUSE_BETWEEN_MS)
        assertEquals(4000L, Speaker.SAFETY_TIMEOUT_MS)
    }

    @Test
    fun speakOnceReadsExactlyOneTimeAndFinishes() {
        val speaker = readySpeaker("ko-KR")
        var finished = 0
        speaker.speakOnce("사과", "ko-KR") { finished += 1 }
        idleFor(1500)

        assertEquals("한 번 읽고 끝난다", listOf(0.90f), speaker.appliedRates.toList())
        assertEquals(1, finished)
    }

    @Test
    fun secondReadingWaitsThreeHundredFiftyMsAfterTheFirst() {
        val speaker = readySpeaker("ko-KR")
        var finished = 0
        speaker.speakTwice("사과", "ko-KR") { finished += 1 }
        idle()

        // 1번째 발음은 바로 나간다.
        assertEquals(listOf(0.90f), speaker.appliedRates.toList())
        assertEquals(0, finished)

        idleFor(349)
        assertEquals("350ms 전에는 두 번째 발음 없음", listOf(0.90f), speaker.appliedRates.toList())
        assertEquals(0, finished)

        idleFor(1)
        assertEquals("350ms 뒤 두 번째 발음도 같은 속도", listOf(0.90f, 0.90f), speaker.appliedRates.toList())
        assertEquals(1, finished)
    }

    @Test
    fun safetyTimerFinishesWhenTheEngineNeverAnswers() {
        val speaker = readySpeaker("ko-KR")
        var finished = 0
        speaker.speakTwice("사과", "ko-KR") { finished += 1 }
        idle()

        idleFor(Speaker.SAFETY_TIMEOUT_MS + 50)
        assertEquals("4초 안전 타이머가 완료를 알린다", 1, finished)
    }

    // -------------------------------------------------- 매니페스트

    @Test
    fun manifestMakesBothTtsEnginesVisible() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "구글 TTS 패키지 가시성",
            manifest.contains("<package android:name=\"com.google.android.tts\" />")
        )
        assertTrue(
            "삼성 TTS 패키지 가시성",
            manifest.contains("<package android:name=\"com.samsung.SMT\" />")
        )
        assertTrue(
            "TTS 서비스 조회",
            manifest.contains("android.intent.action.TTS_SERVICE")
        )
    }

    // -------------------------------------------------- 도우미

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun idleFor(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun readySpeaker(langTag: String): Speaker {
        val speaker = Speaker(context)
        var ready: Boolean? = null
        speaker.init(langTag) { ready = it }
        wakeEngine(TextToSpeech.SUCCESS)
        assertEquals("엔진 준비", true, ready)
        return speaker
    }

    /** 마지막에 만든 TTS 엔진에 초기화 결과를 알려 준다(실기기의 콜백과 같다). */
    private fun wakeEngine(status: Int) {
        val engine: TextToSpeech? = ShadowTextToSpeech.getLastTextToSpeechInstance()
        assertNotNull("TTS 엔진이 만들어지지 않았다", engine)
        Shadow.extract<ShadowTextToSpeech>(engine!!).onInitListener.onInit(status)
        idle()
    }

    private fun voice(name: String, locale: Locale, network: Boolean): Voice = Voice(
        name,
        locale,
        Voice.QUALITY_HIGH,
        Voice.LATENCY_NORMAL,
        network,
        emptySet()
    )

    private companion object {
        /** 실기기의 한국어 목소리 지역(ko-KR). */
        val KOREAN: Locale = Locale.forLanguageTag("ko-KR")
    }
}
