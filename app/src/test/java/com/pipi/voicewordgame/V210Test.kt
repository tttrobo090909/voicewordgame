package com.pipi.voicewordgame

import android.content.Context
import android.media.AudioAttributes
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.pipi.voicewordgame.audio.SfxPlayer
import com.pipi.voicewordgame.game.WordStore
import com.pipi.voicewordgame.tts.Speaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowTextToSpeech
import java.time.Duration
import java.util.Locale

/**
 * v2.10 검증 — `들어봐요! v1.2` 의 1번(TTS 개선)·4번(정답·오답 효과음)을 `말해봐요!` 에 옮긴 버전.
 *
 * 1) TTS: 한국어=구글 TTS·영어=삼성 TTS 우선, `ko-KR-language`·`en-AU-language` 목소리,
 *    속도 0.90·음높이 1.00 으로 두 번 읽기, `<queries>` 에 두 엔진 선언.
 * 2) 효과음: 정답 → `sfx_correct`, 음성인식 오답·시간 초과 → `sfx_wrong`,
 *    건너뛰기·못 들었어요 → 소리 없음. SoundPool(USAGE_GAME)이라 음성인식과 겹치지 않는다.
 * 3) targetSdk·compileSdk 36, 화면 표기 v2.10.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class V210Test {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WordStore(context).restoreDefaults()
        context.getSharedPreferences("voicewordgame", Context.MODE_PRIVATE).edit().clear().commit()
        ShadowTextToSpeech.addLanguageAvailability(Locale.forLanguageTag("ko-KR"))
        ShadowTextToSpeech.addLanguageAvailability(Locale.forLanguageTag("en-AU"))
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
    }

    private fun launch(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun idleFor(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    /** 마지막에 만든 TTS 엔진에 초기화 성공을 알린다(실기기의 엔진 콜백과 같다). */
    private fun makeTtsReady() {
        val engine = ShadowTextToSpeech.getLastTextToSpeechInstance() ?: return
        runCatching {
            Shadow.extract<ShadowTextToSpeech>(engine).onInitListener.onInit(TextToSpeech.SUCCESS)
        }
        idle()
    }

    private fun startGame(main: MainActivity) {
        main.findViewById<View>(R.id.startButton).performClick()
        idle()
    }

    // -------------------------------------------------- 1) TTS 엔진·목소리·속도

    @Test
    fun koreanSpeaksWithGoogleTtsAndTheLanguageVoiceAtPointNine() {
        ShadowTextToSpeech.addVoice(voice("ko-kr-x-iss", Locale.forLanguageTag("ko-KR")))
        ShadowTextToSpeech.addVoice(voice("ko-KR-language", Locale.forLanguageTag("ko-KR")))

        val main = launch()
        makeTtsReady() // 첫 화면에서 연 한국어 엔진
        startGame(main)

        // 말해봐요! 는 정답을 판정할 때 단어를 두 번 읽는다.
        main.findViewById<Button>(R.id.answerButton).performClick()
        idleFor(400)

        assertTrue("정답 단어를 읽는다", main.speechLogForTest.isNotEmpty())
        assertEquals("구글 TTS", Speaker.GOOGLE_TTS_ENGINE, main.speakerEngineForTest("ko-KR"))
        assertEquals("ko-KR-language 목소리", "ko-KR-language", main.speakerVoiceForTest("ko-KR"))
        assertEquals("두 번 모두 0.90", listOf(0.90f, 0.90f), main.appliedSpeechRates)
        assertEquals("두 번 모두 음높이 1.00", listOf(1.00f, 1.00f), main.appliedSpeechPitches)
    }

    @Test
    fun englishSpeaksWithSamsungTtsAtPointNine() {
        ShadowTextToSpeech.addVoice(voice("ko-KR-language", Locale.forLanguageTag("ko-KR")))
        ShadowTextToSpeech.addVoice(voice("en-US-language", Locale.US))

        val main = launch()
        makeTtsReady()
        startGame(main)

        // 게임 화면에서 ENG 로 바꾸면 정답을 영어로 읽는다(영어 엔진은 삼성 TTS 부터).
        main.findViewById<View>(R.id.langEn).performClick()
        idle()
        main.findViewById<Button>(R.id.answerButton).performClick()
        idle()

        assertEquals("영어는 삼성 TTS", Speaker.SAMSUNG_TTS_ENGINE, main.speakerEngineForTest("en-US"))
        makeTtsReady() // 영어(삼성) 엔진 초기화
        idleFor(400)

        assertEquals("영어도 0.90", 0.90f, main.appliedSpeechRates.last(), 0.0001f)
        assertEquals("영어도 음높이 1.00", 1.00f, main.appliedSpeechPitches.last(), 0.0001f)
    }

    @Test
    fun englishPrefersEnAuLanguageVoiceWhenSamsungOffersIt() {
        ShadowTextToSpeech.addVoice(voice("en-AU-language", Locale.forLanguageTag("en-AU")))
        ShadowTextToSpeech.addVoice(voice("en-US-language", Locale.US))

        val main = launch()
        makeTtsReady()
        startGame(main)
        main.findViewById<View>(R.id.langEn).performClick()
        idle()
        main.findViewById<Button>(R.id.answerButton).performClick()
        idle()
        makeTtsReady()
        idleFor(400)

        assertEquals("en-AU-language 를 먼저 고른다", "en-AU-language", main.speakerVoiceForTest("en-US"))
    }

    @Test
    fun bothReadingsOfTheAnswerUsePointNine() {
        val main = launch()
        makeTtsReady()
        startGame(main)

        main.findViewById<Button>(R.id.answerButton).performClick()
        idleFor(400)

        assertEquals("두 번 모두 0.90", listOf(0.90f, 0.90f), main.appliedSpeechRates)
        assertEquals("두 번 모두 1.00", listOf(1.00f, 1.00f), main.appliedSpeechPitches)
    }

    // -------------------------------------------------- 2) 정답·오답 효과음

    @Test
    fun bothSoundEffectFilesArePackaged() {
        val main = launch()
        val correct = main.resources.openRawResource(R.raw.sfx_correct).use { it.readBytes().size }
        val wrong = main.resources.openRawResource(R.raw.sfx_wrong).use { it.readBytes().size }
        assertTrue("정답 효과음 파일($correct bytes)", correct > 40_000)
        assertTrue("오답 효과음 파일($wrong bytes)", wrong > 30_000)
    }

    @Test
    fun correctAnswerPlaysTheCorrectSound() {
        val main = launch()
        startGame(main)
        val word = main.currentWord!!

        main.findViewById<Button>(R.id.answerButton).performClick()
        idle()

        assertEquals("정답 효과음 한 번", listOf(SfxPlayer.Sound.CORRECT), main.sfxPlayedForTest)
        val pool = main.sfxPlayerForTest.poolForTest
        assertNotNull("SoundPool 이 준비됐다", pool)
        assertTrue("sfx_correct 재생", shadowOf(pool!!).wasResourcePlayed(R.raw.sfx_correct))
        assertFalse("sfx_wrong 은 나지 않는다", shadowOf(pool).wasResourcePlayed(R.raw.sfx_wrong))
        assertEquals("정답으로 센다", 1, main.correctCountForTest)
        assertFalse("다음 문제로 넘어가는 중", main.isAwaiting)
        assertEquals("정답 단어를 그대로 두 번 읽는다", word.text("ko") to 2, main.speechLogForTest.last())
    }

    @Test
    fun voiceAnswerThatMatchesPlaysTheCorrectSound() {
        val main = launch()
        startGame(main)
        val word = main.currentWord!!

        main.recognitionListenerForTest.onAnswers(listOf(word.ko))
        idle()

        assertEquals(listOf(SfxPlayer.Sound.CORRECT), main.sfxPlayedForTest)
        assertEquals(1, main.correctCountForTest)
        assertEquals("정답! ${word.ko}", main.findViewById<TextView>(R.id.answerReveal).text.toString())
    }

    @Test
    fun voiceAnswerThatDoesNotMatchPlaysTheWrongSoundAndTheRoundGoesOn() {
        val main = launch()
        startGame(main)
        val word = main.currentWord!!
        assertTrue("정답을 기다리는 중", main.isAwaiting)

        main.recognitionListenerForTest.onAnswers(listOf("전혀다른낱말"))
        idle()

        assertEquals("오답 효과음 한 번", listOf(SfxPlayer.Sound.WRONG), main.sfxPlayedForTest)
        val pool = main.sfxPlayerForTest.poolForTest!!
        assertTrue("sfx_wrong 재생", shadowOf(pool).wasResourcePlayed(R.raw.sfx_wrong))
        assertFalse("sfx_correct 은 나지 않는다", shadowOf(pool).wasResourcePlayed(R.raw.sfx_correct))
        assertEquals("들은 대로 보여 준다", "이렇게 들었어요 · 전혀다른낱말",
            main.findViewById<TextView>(R.id.gameStatus).text.toString())
        assertTrue("계속 말해 볼 수 있다", main.isAwaiting)
        assertTrue("제한시간도 계속 흐른다", main.isRoundTimerRunning)
        assertEquals("정답으로 세지 않는다", 0, main.correctCountForTest)
        assertEquals("아직 같은 문제", word.id, main.currentWord!!.id)

        // 오답 효과음(0.4초)이 끝난 뒤 다시 듣는다 — 마이크 입력과 소리가 겹치지 않는다.
        idleFor(500)
        assertTrue(main.isAwaiting)
    }

    @Test
    fun timeUpPlaysTheWrongSound() {
        val main = launch()
        startGame(main)
        assertEquals("아직 효과음 없음", emptyList<SfxPlayer.Sound>(), main.sfxPlayedForTest)

        idleFor(main.roundLimitForTest + 50)

        assertEquals("시간 초과는 오답", listOf(SfxPlayer.Sound.WRONG), main.sfxPlayedForTest)
        val pool = main.sfxPlayerForTest.poolForTest!!
        assertTrue("sfx_wrong 재생", shadowOf(pool).wasResourcePlayed(R.raw.sfx_wrong))
        assertEquals("정답으로 세지 않는다", 0, main.correctCountForTest)
        assertFalse("다음 문제로 넘어가는 중", main.isAwaiting)
    }

    @Test
    fun skippingAndNotHearingDoNotPlayAnySound() {
        val main = launch()
        startGame(main)

        main.recognitionListenerForTest.onNoMatch()
        idle()
        assertEquals("못 들었어요는 판정이 아니다", emptyList<SfxPlayer.Sound>(), main.sfxPlayedForTest)

        main.findViewById<Button>(R.id.skipButton).performClick()
        idle()
        assertEquals("건너뛰기도 효과음 없음", emptyList<SfxPlayer.Sound>(), main.sfxPlayedForTest)
    }

    @Test
    fun soundPoolIsReleasedWithTheScreen() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val main = controller.get()
        startGame(main)
        val player = main.sfxPlayerForTest
        assertNotNull(player.poolForTest)

        controller.destroy()

        assertNull("화면이 닫히면 SoundPool 을 놓아 준다", player.poolForTest)
    }

    @Test
    fun effectSoundIsAGameSoundThatDoesNotTouchTheMicrophone() {
        assertEquals(
            "USAGE_GAME — 음성인식(DIRECT 마이크 입력)과 다른 출력 스트림",
            AudioAttributes.USAGE_GAME,
            SfxPlayer.USAGE
        )
        assertEquals("동시 2개까지", 2, SfxPlayer.MAX_STREAMS)
        assertEquals("볼륨 1.0", 1.0f, SfxPlayer.VOLUME, 0.0001f)
    }

    // -------------------------------------------------- 3) API 36 · 버전

    @Test
    fun targetsApiThirtySixAndShowsV210() {
        val main = launch()
        val info = main.packageManager.getPackageInfo(main.packageName, 0)
        assertEquals("targetSdk 36", 36, info.applicationInfo!!.targetSdkVersion)
        assertEquals("v2.10", main.findViewById<TextView>(R.id.versionText).text.toString())
        assertEquals("v2.10", BuildConfig.VERSION_SHORT)
        assertTrue("versionName 은 2.10_MMdd_HHmm: ${BuildConfig.VERSION_NAME}",
            Regex("""2\.10_\d{4}_\d{4}""").matches(BuildConfig.VERSION_NAME))
    }

    private fun voice(name: String, locale: Locale): Voice =
        Voice(name, locale, Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, emptySet())
}
