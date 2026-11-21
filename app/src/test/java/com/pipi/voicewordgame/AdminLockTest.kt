package com.pipi.voicewordgame

import com.pipi.voicewordgame.admin.Gugu
import com.pipi.voicewordgame.admin.TapCounter
import com.pipi.voicewordgame.speech.RecognitionEngines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** 숨은 관리자 잠금(1.2초 안에 3번 탭 → 랜덤 구구단)과 인식 엔진 선택 로직. */
class AdminLockTest {

    @Test
    fun threeQuickTapsUnlockButSlowTapsDoNot() {
        val counter = TapCounter(windowMs = 1200L, required = 3)
        assertFalse(counter.tap(0L))
        assertFalse(counter.tap(400L))
        assertTrue(counter.tap(900L))
        // 잠금이 풀린 뒤 카운터는 비워진다.
        assertFalse(counter.tap(1000L))
    }

    @Test
    fun tapsSpreadOverMoreThanWindowDoNotUnlock() {
        val counter = TapCounter(windowMs = 1200L, required = 3)
        assertFalse(counter.tap(0L))
        assertFalse(counter.tap(1300L))
        assertFalse(counter.tap(2600L))
    }

    @Test
    fun guguQuestionIsTwoToNineTimesOneToNine() {
        val random = Random(7)
        repeat(200) {
            val q = Gugu.next(random)
            assertTrue(q.a in 2..9)
            assertTrue(q.b in 1..9)
            assertEquals(q.a * q.b, q.answer)
        }
    }

    @Test
    fun guguAnswerCheckRejectsWrongAndBlank() {
        val q = Gugu.Question(6, 7)
        assertTrue(Gugu.isCorrect(q, "42"))
        assertTrue(Gugu.isCorrect(q, " 42 "))
        assertFalse(Gugu.isCorrect(q, "41"))
        assertFalse(Gugu.isCorrect(q, ""))
        assertFalse(Gugu.isCorrect(q, "마흔둘"))
    }

    @Test
    fun googleRecognizerIsPreferredWhenInstalled() {
        val services = listOf(
            "com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService",
            "com.samsung.android.honeyvoice/.VoiceRecognitionService"
        )
        val plan = RecognitionEngines.plan(services)
        assertEquals(RecognitionEngines.GOOGLE_TTS_COMPONENT, plan.component)
        assertTrue(plan.usesGoogle)
    }

    @Test
    fun fallsBackToSystemRecognizerWhenGoogleMissing() {
        val plan = RecognitionEngines.plan(listOf("com.samsung.android.honeyvoice/.VoiceRecognitionService"))
        assertNull(plan.component)
        assertFalse(plan.usesGoogle)
    }
}
