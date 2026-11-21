package com.pipi.voicewordgame.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * SpeechRecognizer 래퍼.
 * API 33+ 에서는 AudioRecord 가 읽은 PCM 을 파이프(EXTRA_AUDIO_SOURCE)로 먹여
 * 마이크 동시 점유 충돌을 피한다. 파이프를 쓸 수 없는 기기에서는 마이크를 직접 쓴다.
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

    companion object {
        private const val EXTRA_AUDIO_SOURCE = "android.speech.extra.AUDIO_SOURCE"
        private const val EXTRA_AUDIO_SOURCE_CHANNEL_COUNT = "android.speech.extra.AUDIO_SOURCE_CHANNEL_COUNT"
    }

    private val appContext: Context = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    var listener: Listener? = null

    fun isAvailable(): Boolean = try {
        SpeechRecognizer.isRecognitionAvailable(appContext)
    } catch (e: Exception) {
        false
    }

    fun start(langTag: String, audioSource: ParcelFileDescriptor?): Boolean {
        if (!isAvailable()) return false
        val rec = ensureRecognizer() ?: return false
        try {
            if (listening) {
                try {
                    rec.cancel()
                } catch (e: Exception) {
                    // ignore
                }
                listening = false
            }
            rec.setRecognitionListener(object : RecognitionListener {
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
                    val texts = results?.getStringArrayList(RecognizerIntent.EXTRA_RESULTS)
                    if (texts == null || texts.isEmpty()) listener?.onNoMatch()
                    else listener?.onAnswers(texts)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val texts = partialResults?.getStringArrayList(RecognizerIntent.EXTRA_RESULTS)
                    if (!texts.isNullOrEmpty()) listener?.onPartial(texts[0])
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            rec.startListening(buildIntent(langTag, audioSource))
            listening = true
            return true
        } catch (e: Exception) {
            listening = false
            return false
        }
    }

    private fun buildIntent(langTag: String, audioSource: ParcelFileDescriptor?): Intent {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 400L)
        if (audioSource != null) {
            intent.putExtra(EXTRA_AUDIO_SOURCE, audioSource)
            intent.putExtra(EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
        }
        return intent
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        val existing = recognizer
        if (existing != null) return existing
        return try {
            SpeechRecognizer.createSpeechRecognizer(appContext).also { recognizer = it }
        } catch (e: Exception) {
            null
        }
    }

    fun stop() {
        val rec = recognizer ?: return
        try {
            if (listening) rec.stopListening()
        } catch (e: Exception) {
            // ignore
        }
        listening = false
    }

    fun cancel() {
        val rec = recognizer ?: return
        try {
            rec.cancel()
        } catch (e: Exception) {
            // ignore
        }
        listening = false
    }

    fun destroy() {
        val rec = recognizer
        recognizer = null
        listening = false
        try {
            rec?.setRecognitionListener(null)
            rec?.destroy()
        } catch (e: Exception) {
            // ignore
        }
    }
}
