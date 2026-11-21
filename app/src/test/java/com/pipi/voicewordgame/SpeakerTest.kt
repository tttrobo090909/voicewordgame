package com.pipi.voicewordgame

import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import com.pipi.voicewordgame.tts.Speaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowTextToSpeech
import java.time.Duration
import java.util.Locale

/**
 * v2.1 발음 속도 차등: 1번째는 천천히(0.72) · 350ms 쉼 · 2번째는 보통(0.95),
 * 두 번째가 끝나면 완료를 알리고, TTS 가 멈추면 4초 안전 타이머가 대신 알린다.
 *
 * Robolectric 의 TTS 그림자는 OnInitListener 를 스스로 부르지 않으므로 테스트에서 직접 성공을 알린다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeakerTest {

    private lateinit var speaker: Speaker
    private lateinit var shadowTts: ShadowTextToSpeech

    @Before
    fun setUp() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.KOREAN)
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)

        speaker = Speaker(ApplicationProvider.getApplicationContext())
        speaker.init { }
        shadowOf(Looper.getMainLooper()).idle()

        val engine = ShadowTextToSpeech.getLastTextToSpeechInstance()
        shadowTts = Shadow.extract(engine)
        shadowTts.onInitListener.onInit(TextToSpeech.SUCCESS)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("TTS 초기화 성공 후 ready", speaker.ready)
    }

    @Test
    fun speechRatePlanIsSlowThenNormal() {
        assertEquals(0.72f, Speaker.FIRST_SPEECH_RATE, 0.0001f)
        assertEquals(0.95f, Speaker.SECOND_SPEECH_RATE, 0.0001f)
        assertEquals(0.72f, Speaker.rateFor(1), 0.0001f)
        assertEquals(0.95f, Speaker.rateFor(2), 0.0001f)
        assertEquals(350L, Speaker.PAUSE_BETWEEN_MS)
        assertEquals(4000L, Speaker.SAFETY_TIMEOUT_MS)
    }

    @Test
    fun firstReadsSlowlyThenSecondReadsNormallyAfterThreeHundredFiftyMs() {
        var finished = 0
        speaker.speakTwice("사과", "ko-KR") { finished += 1 }
        shadowOf(Looper.getMainLooper()).idle()

        // 1번째 발음은 천천히.
        assertEquals(listOf("사과"), shadowTts.spokenTextList.toList())
        assertEquals(listOf(0.72f), speaker.appliedRates.toList())

        // 1번째 발음이 끝나고 350ms 가 되기 전에는 2번째 발음이 나오지 않는다.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(349))
        assertEquals("350ms 전에는 두 번째 발음 없음", listOf("사과"), shadowTts.spokenTextList.toList())
        assertEquals(0, finished)

        // 350ms 뒤 2번째 발음은 보통 속도. 두 번째 발음이 끝나면 완료를 알린다.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertEquals(listOf("사과", "사과"), shadowTts.spokenTextList.toList())
        assertEquals(listOf(0.72f, 0.95f), speaker.appliedRates.toList())
        assertEquals(1, finished)
    }
}
