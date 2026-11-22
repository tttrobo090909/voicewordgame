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
 * 정답 단어를 읽어 주는 TTS.
 *
 * - 구글 TTS(com.google.android.tts) 엔진을 우선 연결하고, 없으면 기기 기본 엔진.
 * - v2.1: 1번째는 **천천히(speechRate 0.72)** → 350ms 쉼 → 2번째는 **보통 속도(0.95)**.
 *   pitch 는 1.05 로 유지.
 * - [speakTwice]: 1번째 발음 완료(onDone) → 350ms 쉼 → 2번째 발음 → 완료되면 onFinished.
 * - v2.2 [speakOnce]: 시간 초과일 때 정답을 **한 번만** 읽고 onFinished.
 *   TTS 가 없거나 오류가 나면 4초 안전 타이머가 대신 onFinished 를 부른다(게임이 멈추지 않게).
 */
class Speaker(context: Context) {

    companion object {
        const val GOOGLE_TTS_ENGINE = "com.google.android.tts"

        /** 1번째 발음: 천천히. */
        const val FIRST_SPEECH_RATE = 0.72f

        /** 2번째 발음: 보통 속도. */
        const val SECOND_SPEECH_RATE = 0.95f

        const val PITCH = 1.05f
        const val PAUSE_BETWEEN_MS = 350L
        const val SAFETY_TIMEOUT_MS = 4000L
        private const val UTTERANCE_FIRST = "answer-1"
        private const val UTTERANCE_SECOND = "answer-2"

        /** 1번째 발음은 천천히, 2번째 발음은 보통 속도로 읽는다. */
        fun rateFor(round: Int): Float =
            if (round <= 1) FIRST_SPEECH_RATE else SECOND_SPEECH_RATE
    }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null

    private var pendingText: String? = null
    private var pendingLang: String = "ko-KR"
    private var plannedRounds: Int = 2
    private var onFinished: (() -> Unit)? = null
    private val finished = AtomicBoolean(true)
    private var retriedWithDefaultEngine = false

    /** 엔진에 실제로 요청한 발음 속도를 차례대로 남긴다(1번째·2번째). 단위 테스트가 읽는다. */
    internal val appliedRates: MutableList<Float> = java.util.Collections.synchronizedList(mutableListOf<Float>())

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
                    UTTERANCE_FIRST ->
                        if (plannedRounds <= 1) finishOnce()
                        else handler.postDelayed(secondUtterance, PAUSE_BETWEEN_MS)

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
            tts?.setSpeechRate(FIRST_SPEECH_RATE)
            tts?.setPitch(PITCH)
        }
    }

    /**
     * 정답 단어를 두 번 읽고 [onFinished] 를 정확히 한 번 부른다.
     * (다음 문제로 넘기는 것은 호출하는 쪽이 500ms 뒤에 한다)
     */
    fun speakTwice(text: String, langTag: String, onFinished: () -> Unit) {
        speak(text, langTag, rounds = 2, onFinished = onFinished)
    }

    /**
     * 정답 단어를 **한 번만** 읽고 [onFinished] 를 정확히 한 번 부른다(시간 초과용).
     */
    fun speakOnce(text: String, langTag: String, onFinished: () -> Unit) {
        speak(text, langTag, rounds = 1, onFinished = onFinished)
    }

    private fun speak(text: String, langTag: String, rounds: Int, onFinished: () -> Unit) {
        stop()
        pendingText = text
        pendingLang = langTag
        plannedRounds = rounds
        this.onFinished = onFinished
        finished.set(false)
        appliedRates.clear()

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
        // 발음마다 속도를 바꿔야 해서 speak 직전에 적용한다(1번째 천천히 · 2번째 보통).
        // 한 번만 읽는 경우(시간 초과)에도 같은 천천히 속도를 쓴다.
        val rate = rateFor(round)
        appliedRates.add(rate)
        runCatching { engine.setSpeechRate(rate) }
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
        runCatching { tts?.setSpeechRate(FIRST_SPEECH_RATE) }
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
