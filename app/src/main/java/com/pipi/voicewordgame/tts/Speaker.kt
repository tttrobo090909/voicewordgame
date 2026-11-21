package com.pipi.voicewordgame.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** 정답 단어를 또박또박 읽어 주는 TTS (한국어·영어). */
class Speaker(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null

    var ready: Boolean = false
        private set
    var languageWarning: String? = null
        private set

    fun init(onReady: (Boolean) -> Unit) {
        if (tts != null) {
            onReady(ready)
            return
        }
        var callbackUsed = false
        tts = TextToSpeech(appContext) { status ->
            val ok = status == TextToSpeech.SUCCESS
            ready = ok
            if (ok) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                    override fun onError(utteranceId: String?, errorCode: Int) {}
                })
                tts?.setSpeechRate(0.88f)
                tts?.setPitch(1.05f)
            }
            if (!callbackUsed) {
                callbackUsed = true
                onReady(ok)
            }
        }
    }

    /** 언어가 없으면 한국어로라도 읽는다. */
    fun speak(text: String, langTag: String) {
        val engine = tts ?: return
        if (!ready) return
        val locale = if (langTag.startsWith("en")) Locale.US else Locale.KOREAN
        var mode = try {
            engine.setLanguage(locale)
        } catch (e: Exception) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
        if (mode == TextToSpeech.LANG_MISSING_DATA || mode == TextToSpeech.LANG_NOT_SUPPORTED) {
            languageWarning = if (langTag.startsWith("en")) "영어 발음 음성이 없어 한국어로 읽어 줄게요" else "발음 음성을 불러오지 못했어요"
            mode = try {
                engine.setLanguage(Locale.KOREAN)
            } catch (e: Exception) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }
            if (mode == TextToSpeech.LANG_MISSING_DATA || mode == TextToSpeech.LANG_NOT_SUPPORTED) {
                languageWarning = "이 기기에 발음 음성이 없어요"
                return
            }
        }
        try {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "word-${System.currentTimeMillis()}")
        } catch (e: Exception) {
            // 말하기 실패는 조용히 넘어간다(화면에는 이미 정답이 보인다).
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            // ignore
        }
        tts = null
        ready = false
    }
}
