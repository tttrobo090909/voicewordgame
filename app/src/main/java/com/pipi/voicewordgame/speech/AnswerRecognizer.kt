package com.pipi.voicewordgame.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Bundle

/**
 * 음성인식 래퍼 — 실기기에서 검증된 dictationtest v1.2 방식 그대로.
 *
 * - 구글 인식기(com.google.android.tts)를 ComponentName 으로 우선 지정, 없으면 시스템 기본.
 * - 마이크는 DIRECT(일반 마이크) 고정 — 오디오 파이프를 쓰지 않는다.
 * - 조기 종료를 만드는 침묵 감지 extra(EXTRA_SPEECH_INPUT_*)는 보내지 않는다.
 * - 결과는 RESULTS_RECOGNITION 1순위, EXTRA_RESULTS 폴백([ResultBundles]).
 */
class AnswerRecognizer(context: Context) {

    interface Listener {
        fun onReady()
        fun onPartial(text: String)
        fun onAnswers(texts: List<String>)
        fun onNoMatch()
        fun onSpeechEnd()
        fun onError(code: Int)
    }

    private val appContext: Context = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    var listener: Listener? = null

    /** 지금 어떤 인식기를 쓰고 있는지(화면 표시·진단용). */
    var engineLabel: String = ""
        private set

    fun isAvailable(): Boolean = try {
        SpeechRecognizer.isRecognitionAvailable(appContext)
    } catch (e: Exception) {
        false
    }

    /** 기기에 설치된 인식 서비스 목록(package/class). */
    fun recognitionServices(): List<String> = try {
        appContext.packageManager
            .queryIntentServices(Intent("android.speech.RecognitionService"), 0)
            .map { "${it.serviceInfo.packageName}/${it.serviceInfo.name}" }
    } catch (e: Exception) {
        emptyList()
    }

    fun start(langTag: String): Boolean {
        if (!isAvailable()) return false
        val rec = ensureRecognizer() ?: return false
        try {
            if (listening) {
                runCatching { rec.cancel() }
                listening = false
            }
            rec.setRecognitionListener(recognitionListener)
            rec.startListening(buildIntent(langTag))
            listening = true
            return true
        } catch (e: Exception) {
            listening = false
            return false
        }
    }

    private fun buildIntent(langTag: String): Intent {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        // 침묵 감지 extra 는 넣지 않는다(일부 인식기에서 빈 결과·조기 종료를 만든다).
        return intent
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        val plan = RecognitionEngines.plan(recognitionServices())

        if (plan.component != null) {
            val name = ComponentName.unflattenFromString(plan.component)
            if (name != null) {
                val created = runCatching {
                    SpeechRecognizer.createSpeechRecognizer(appContext, name)
                }.getOrNull()
                if (created != null) {
                    recognizer = created
                    engineLabel = "구글 인식기"
                    return created
                }
            }
        }

        val fallback = runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()
        if (fallback != null) {
            recognizer = fallback
            engineLabel = "시스템 기본 인식기"
        }
        return fallback
    }

    /** 인식기가 응답하지 않을 때 새로 만든다(ERROR_CLIENT 등에서 복구). */
    fun recreate() {
        destroyRecognizer()
    }

    fun cancel() {
        val rec = recognizer ?: return
        runCatching { rec.cancel() }
        listening = false
    }

    fun destroy() = destroyRecognizer()

    private fun destroyRecognizer() {
        val rec = recognizer
        recognizer = null
        listening = false
        runCatching {
            rec?.setRecognitionListener(null)
            rec?.destroy()
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listening = true
            listener?.onReady()
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            listener?.onSpeechEnd()
        }

        override fun onError(error: Int) {
            listening = false
            listener?.onError(error)
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val texts = ResultBundles.texts(results)
            if (texts.isEmpty()) listener?.onNoMatch() else listener?.onAnswers(texts)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val texts = ResultBundles.texts(partialResults)
            if (texts.isNotEmpty()) listener?.onPartial(texts[0])
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
