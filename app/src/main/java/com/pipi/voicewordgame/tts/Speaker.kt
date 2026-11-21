package com.pipi.voicewordgame.tts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 정답 단어를 두 번 읽어 주는 TTS.
 *
 * - 구글 TTS(com.google.android.tts) 엔진을 우선 연결하고, 없으면 기기 기본 엔진.
 * - speechRate 0.92 · pitch 1.05 로 또박또박 부드럽게.
 * - [speakTwice]: 1번째 발음 완료(onDone) → 300ms 쉼 → 2번째 발음 → 완료되면 onFinished.
 *   TTS 가 없거나 오류가 나면 3.5초 안전 타이머가 대신 onFinished 를 부른다(게임이 멈추지 않게).
 */
class Speaker(context: Context) {

    companion object {
        const val GOOGLE_TTS_ENGINE = "com.google.android.tts"
        const val SPEECH_RATE = 0.92f
        const val PITCH = 1.05f
        const val PAUSE_BETWEEN_MS = 300L
        const val SAFETY_TIMEOUT_MS = 3500L
        private const val UTTERANCE_FIRST = "answer-1"
        private const val UTTERANCE_SECOND = "answer-2"
    }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null

    private var pendingText: String? = null
    private var pendingLang: String = "ko-KR"
    private var onFinished: (() -> Unit)? = null
    private val finished = AtomicBoolean(true)
    private var retriedWithDefaultEngine = false

    var ready: Boolean = false
        private set

    var engineLabel: String = ""
        private set

    private val safety = Runnable { finishOnce() }

    private val secondUtterance = Runnable { speakOnce(2) }

    fun init(onReady: (Boolean) -> Unit = {}) {
        if (tts != null) {
            onReady(ready)
            return
        }
        create(useGoogle = true, onReady = onReady)
    }

    private fun create(useGoogle: Boolean, onReady: (Boolean) -> Unit) {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                engineLabel = runCatching { tts?.defaultEngine.orEmpty() }.getOrNull().orEmpty()
                configure()
                onReady(true)
            } else {
                ready = false
                onReady(false)
                // 구글 엔진이 없거나 초기화에 실패하면 기기 기본 엔진으로 한 번 더 시도한다.
                if (useGoogle && !retriedWithDefaultEngine) {
                    retriedWithDefaultEngine = true
                    shutdown()
                    create(useGoogle = false, onReady = onReady)
                }
            }
        }

        tts = try {
            if (useGoogle) {
                TextToSpeech(appContext, listener, GOOGLE_TTS_ENGINE)
            } else {
                TextToSpeech(appContext, listener)
            }
        } catch (e: Exception) {
            ready = false
            if (useGoogle && !retriedWithDefaultEngine) {
                retriedWithDefaultEngine = true
                return create(useGoogle = false, onReady = onReady)
            }
            null
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                when (utteranceId) {
                    UTTERANCE_FIRST -> handler.postDelayed(secondUtterance, PAUSE_BETWEEN_MS)
                    UTTERANCE_SECOND -> finishOnce()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                finishOnce()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                finishOnce()
            }
        })
    }

    private fun configure() {
        runCatching {
            tts?.setSpeechRate(SPEECH_RATE)
            tts?.setPitch(PITCH)
        }
    }

    /**
     * 정답 단어를 두 번 읽고 [onFinished] 를 정확히 한 번 부른다.
     * (다음 문제로 넘기는 것은 호출하는 쪽이 500ms 뒤에 한다)
     */
    fun speakTwice(text: String, langTag: String, onFinished: () -> Unit) {
        stop()
        pendingText = text
        pendingLang = langTag
        this.onFinished = onFinished
        finished.set(false)

        handler.removeCallbacks(safety)
        handler.postDelayed(safety, SAFETY_TIMEOUT_MS)

        if (!ready) {
            init { ok ->
                if (ok && !finished.get()) {
                    if (applyLanguage(pendingLang)) speakOnce(1) else finishOnce()
                } else if (!ok) {
                    finishOnce()
                }
            }
            return
        }
        if (!applyLanguage(langTag)) {
            // 이 기기에 해당 언어 음성이 없으면 소리 없이 다음 문제로 넘어간다.
            handler.postDelayed({ finishOnce() }, 250)
            return
        }
        speakOnce(1)
    }

    private fun speakOnce(round: Int) {
        val engine = tts
        val text = pendingText ?: run {
            finishOnce()
            return
        }
        if (engine == null || !ready) {
            finishOnce()
            return
        }
        val id = if (round == 1) UTTERANCE_FIRST else UTTERANCE_SECOND
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
        }
        val result = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        }.getOrDefault(TextToSpeech.ERROR)
        if (result != TextToSpeech.SUCCESS) finishOnce()
    }

    private fun applyLanguage(langTag: String): Boolean {
        val engine = tts ?: return false
        val locale = if (langTag.startsWith("en")) Locale.US else Locale.KOREAN
        val primary = runCatching { engine.setLanguage(locale) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (primary == TextToSpeech.LANG_MISSING_DATA || primary == TextToSpeech.LANG_NOT_SUPPORTED) {
            val korean = runCatching { engine.setLanguage(Locale.KOREAN) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            return korean != TextToSpeech.LANG_MISSING_DATA && korean != TextToSpeech.LANG_NOT_SUPPORTED
        }
        return true
    }

    private fun finishOnce() {
        if (finished.getAndSet(true)) return
        handler.removeCallbacks(safety)
        handler.removeCallbacks(secondUtterance)
        val callback = onFinished
        onFinished = null
        callback?.invoke()
    }

    fun stop() {
        handler.removeCallbacks(safety)
        handler.removeCallbacks(secondUtterance)
        pendingText = null
        onFinished = null
        finished.set(true)
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        stop()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ready = false
    }
}
