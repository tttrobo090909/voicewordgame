package com.pipi.voicewordgame.speech

import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * 인식 결과 Bundle 읽기.
 *
 * 실기기 검증(dictationtest v1.2) 결론:
 * 구글 인식기는 결과를 `SpeechRecognizer.RESULTS_RECOGNITION`("results_recognition") 키로 주고,
 * `RecognizerIntent.EXTRA_RESULTS`("results") 는 비어 있는 경우가 있다.
 * 그래서 RESULTS_RECOGNITION 을 1순위로 읽고, 없을 때만 EXTRA_RESULTS 로 넘어간다.
 */
object ResultBundles {

    fun texts(bundle: Bundle?): List<String> {
        if (bundle == null) return emptyList()

        val primary = list(bundle, SpeechRecognizer.RESULTS_RECOGNITION)
        if (primary.isNotEmpty()) return primary

        val fallback = list(bundle, RecognizerIntent.EXTRA_RESULTS)
        if (fallback.isNotEmpty()) return fallback

        return emptyList()
    }

    /** 어느 키에서 읽었는지(로그·테스트용). */
    fun source(bundle: Bundle?): String {
        if (bundle == null) return "없음"
        if (list(bundle, SpeechRecognizer.RESULTS_RECOGNITION).isNotEmpty()) {
            return SpeechRecognizer.RESULTS_RECOGNITION
        }
        if (list(bundle, RecognizerIntent.EXTRA_RESULTS).isNotEmpty()) {
            return RecognizerIntent.EXTRA_RESULTS
        }
        return "없음"
    }

    fun keys(bundle: Bundle?): String = bundle?.keySet()?.joinToString(",").orEmpty()

    private fun list(bundle: Bundle, key: String): List<String> {
        val raw = try {
            bundle.getStringArrayList(key)
        } catch (e: Exception) {
            null
        }
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.filter { it.isNotBlank() }
    }
}
