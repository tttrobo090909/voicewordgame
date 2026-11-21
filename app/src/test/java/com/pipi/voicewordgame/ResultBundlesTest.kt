package com.pipi.voicewordgame

import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.pipi.voicewordgame.speech.ResultBundles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 실기기 버그(dictationtest v1.2)의 재발 방지:
 * 결과는 RESULTS_RECOGNITION 1순위, EXTRA_RESULTS 폴백으로 읽는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResultBundlesTest {

    @Test
    fun readsResultsRecognitionFirst() {
        val bundle = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("사과", "사과예요"))
        }
        assertEquals(listOf("사과", "사과예요"), ResultBundles.texts(bundle))
        assertEquals(SpeechRecognizer.RESULTS_RECOGNITION, ResultBundles.source(bundle))
    }

    @Test
    fun fallsBackToExtraResultsWhenRecognitionKeyMissing() {
        val bundle = Bundle().apply {
            putStringArrayList(RecognizerIntent.EXTRA_RESULTS, arrayListOf("바나나"))
        }
        assertEquals(listOf("바나나"), ResultBundles.texts(bundle))
        assertEquals(RecognizerIntent.EXTRA_RESULTS, ResultBundles.source(bundle))
    }

    @Test
    fun fallsBackWhenRecognitionKeyIsEmpty() {
        val bundle = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf())
            putStringArrayList(RecognizerIntent.EXTRA_RESULTS, arrayListOf("포도"))
        }
        assertEquals(listOf("포도"), ResultBundles.texts(bundle))
    }

    @Test
    fun prefersRecognitionKeyWhenBothPresent() {
        val bundle = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("고양이"))
            putStringArrayList(RecognizerIntent.EXTRA_RESULTS, arrayListOf("강아지"))
        }
        assertEquals(listOf("고양이"), ResultBundles.texts(bundle))
    }

    @Test
    fun emptyForNullOrBlankResults() {
        assertTrue(ResultBundles.texts(null).isEmpty())
        val blank = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("", "  "))
        }
        assertTrue(ResultBundles.texts(blank).isEmpty())
    }
}
