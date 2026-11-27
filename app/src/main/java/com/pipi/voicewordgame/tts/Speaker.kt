package com.pipi.voicewordgame.tts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 정답 단어를 읽어 주는 TTS (v2.10 — `들어봐요!` v1.2 의 TTS 개선 1번).
 *
 * - **한국어(ko-KR)**: 구글 TTS(`com.google.android.tts`) 를 먼저 열고, 없으면 기기 기본 엔진.
 *   목소리는 `ko-KR-language`(오프라인)가 있으면 그것을, 없으면 ko-KR 의 오프라인 목소리를 쓴다.
 * - **영어(en-US·en-AU)**: 삼성 TTS(`com.samsung.SMT`) 를 먼저 열고, 없으면 구글 TTS → 기기 기본 엔진.
 *   목소리는 `en-AU-language` → `en-US-language` 순으로 고른다(폴백 엔진에서도 영어가 자연스럽게 나오도록).
 * - 두 번 모두 **속도 0.90 · 음높이 1.00** 으로 읽는다(1번째 발음 완료 → 350ms 쉼 → 2번째).
 * - 언어마다 엔진을 따로 열어 둔다(ko·en). 게임 중 `한글 / ENG` 를 바꿔도 엔진을 다시 여는 기다림이 없다.
 * - [speakTwice]: 정답·건너뛰기·시간 초과 때 2번(1번째 발음 완료 → 350ms 쉼 → 2번째 발음 → 완료되면 onFinished).
 * - [speakOnce]: 한 번만 읽고 onFinished.
 *   TTS 가 없거나 오류가 나면 4초 안전 타이머가 대신 onFinished 를 부른다(게임이 멈추지 않게).
 */
class Speaker(context: Context) {

    companion object {
        const val GOOGLE_TTS_ENGINE = "com.google.android.tts"
        const val SAMSUNG_TTS_ENGINE = "com.samsung.SMT"

        /** v2.10: 두 번 모두 같은 속도(TTS 테스트기 v1.1·들어봐요! v1.2 에서 고른 0.90). */
        const val SPEECH_RATE = 0.90f

        /** v2.10: 음높이는 기본값 그대로(1.00). */
        const val PITCH = 1.00f

        /** 예전 이름 호환 — 이제 첫 번째 발음도 [SPEECH_RATE] 로 읽는다. */
        const val FIRST_SPEECH_RATE = SPEECH_RATE

        /** 예전 이름 호환 — 이제 두 번째 발음도 [SPEECH_RATE] 로 읽는다. */
        const val SECOND_SPEECH_RATE = SPEECH_RATE

        const val PAUSE_BETWEEN_MS = 350L
        const val SAFETY_TIMEOUT_MS = 4000L
        private const val UTTERANCE_FIRST = "answer-1"
        private const val UTTERANCE_SECOND = "answer-2"

        /** 지금 문제를 읽는 속도. 1번째·2번째 모두 같다. */
        fun rateFor(round: Int): Float = SPEECH_RATE

        fun isEnglish(langTag: String): Boolean =
            langTag.lowercase(Locale.ROOT).startsWith("en")

        /**
         * 언어별로 먼저 열어 볼 엔진 순서(마지막 `null` 은 기기 기본 엔진).
         * 한국어는 구글 TTS → 기본, 영어는 삼성 TTS → 구글 TTS → 기본.
         */
        fun engineCandidates(langTag: String): List<String?> =
            if (isEnglish(langTag)) {
                listOf(SAMSUNG_TTS_ENGINE, GOOGLE_TTS_ENGINE, null)
            } else {
                listOf(GOOGLE_TTS_ENGINE, null)
            }

        /**
         * 그 언어에서 차례로 시도할 지역(locale).
         * 한국어는 `ko-KR`, 영어는 `en-AU`(삼성 TTS 의 영어 목소리)를 먼저 보고, 없으면 `en-US`·`en-GB`·`en`.
         */
        fun preferredLocales(langTag: String): List<Locale> =
            if (isEnglish(langTag)) {
                listOf(
                    Locale.forLanguageTag("en-AU"),
                    Locale.US,
                    Locale.forLanguageTag("en-GB"),
                    Locale.ENGLISH
                )
            } else {
                listOf(Locale.forLanguageTag("ko-KR"))
            }

        /** 엔진이 주는 목소리 이름 규칙: `ko-KR-language` · `en-AU-language`. */
        fun voiceNameFor(locale: Locale): String {
            val tag = if (locale.country.isNotBlank()) "${locale.language}-${locale.country}" else locale.language
            return "$tag-language"
        }
    }

    /** 한 언어(ko 또는 en)의 엔진 한 벌. 언어마다 따로 열어 둔다. */
    private class Slot(val family: String) {
        var tts: TextToSpeech? = null
        var ready: Boolean = false
        var opening: Boolean = false

        /** 실제로 연 엔진 패키지(기기 기본 엔진이면 null). */
        var enginePackage: String? = null
        val waiters: MutableList<(Boolean) -> Unit> = mutableListOf()
    }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val slots = HashMap<String, Slot>()

    private var pendingText: String? = null
    private var pendingLang: String = "ko-KR"
    private var plannedRounds: Int = 2
    private var onFinished: (() -> Unit)? = null
    private val finished = AtomicBoolean(true)

    /** 지금 읽고 있는 언어의 엔진(ko/en). */
    private var activeFamily: String = "ko"

    /** 발화 요청 번호 — 엔진을 여는 동안 새 요청이 오면 옛 요청은 버린다. */
    private var generation: Int = 0

    /** 엔진에 실제로 요청한 발음 속도를 차례대로 남긴다(1번째·2번째). 단위 테스트가 읽는다. */
    internal val appliedRates: MutableList<Float> =
        java.util.Collections.synchronizedList(mutableListOf<Float>())

    /** 엔진에 실제로 요청한 음높이를 차례대로 남긴다. 단위 테스트가 읽는다. */
    internal val appliedPitches: MutableList<Float> =
        java.util.Collections.synchronizedList(mutableListOf<Float>())

    private val safety = Runnable { finishOnce() }

    private val secondUtterance = Runnable { speakOnce(2) }

    /** 안전 타이머를 (다시) 건다 — 엔진을 여는 동안과 발화가 막히는 경우 모두를 막아 준다. */
    private fun armSafety(delayMs: Long = SAFETY_TIMEOUT_MS) {
        handler.removeCallbacks(safety)
        handler.postDelayed(safety, delayMs)
    }

    private val progressListener = object : UtteranceProgressListener() {
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
    }

    /**
     * 그 언어의 엔진을 (아직 안 열었으면) 연다. 다 열리거나 끝내 못 열면 [onReady] 를 부른다.
     * 한국어는 구글 TTS → 기본, 영어는 삼성 TTS → 구글 TTS → 기본 순으로 시도한다.
     */
    fun init(langTag: String = "ko-KR", onReady: (Boolean) -> Unit = {}) {
        val slot = slotFor(langTag)
        if (slot.ready) {
            onReady(true)
            return
        }
        slot.waiters.add(onReady)
        if (slot.opening) return
        slot.opening = true
        openEngine(slot, engineCandidates(langTag), 0)
    }

    private fun openEngine(slot: Slot, candidates: List<String?>, index: Int) {
        if (index >= candidates.size) {
            slot.opening = false
            slot.ready = false
            finishWaiters(slot, false)
            return
        }
        val packageName = candidates[index]
        slot.enginePackage = packageName
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                slot.ready = true
                slot.opening = false
                configure(slot)
                finishWaiters(slot, true)
            } else {
                // 이 엔진이 없거나 열리지 않았으면 다음 후보(구글 → 기본)로 넘어간다.
                closeSlot(slot)
                openEngine(slot, candidates, index + 1)
            }
        }

        val created = try {
            if (packageName == null) TextToSpeech(appContext, listener)
            else TextToSpeech(appContext, listener, packageName)
        } catch (e: Exception) {
            null
        }
        if (created == null) {
            openEngine(slot, candidates, index + 1)
            return
        }
        slot.tts = created
        created.setOnUtteranceProgressListener(progressListener)
    }

    private fun configure(slot: Slot) {
        runCatching {
            slot.tts?.setSpeechRate(SPEECH_RATE)
            slot.tts?.setPitch(PITCH)
        }
    }

    private fun finishWaiters(slot: Slot, ok: Boolean) {
        val waiting = slot.waiters.toList()
        slot.waiters.clear()
        waiting.forEach { it(ok) }
    }

    /**
     * 정답 단어를 두 번 읽고 [onFinished] 를 정확히 한 번 부른다.
     * (다음 문제로 넘기는 것은 호출하는 쪽이 500ms 뒤에 한다)
     */
    fun speakTwice(text: String, langTag: String, onFinished: () -> Unit) {
        speak(text, langTag, rounds = 2, onFinished = onFinished)
    }

    /**
     * 정답 단어를 **한 번만** 읽고 [onFinished] 를 정확히 한 번 부른다.
     */
    fun speakOnce(text: String, langTag: String, onFinished: () -> Unit) {
        speak(text, langTag, rounds = 1, onFinished = onFinished)
    }

    private fun speak(text: String, langTag: String, rounds: Int, onFinished: () -> Unit) {
        stop()
        val request = ++generation
        pendingText = text
        pendingLang = langTag
        plannedRounds = rounds
        this.onFinished = onFinished
        finished.set(false)
        appliedRates.clear()
        appliedPitches.clear()

        handler.removeCallbacks(safety)
        armSafety()

        val slot = slotFor(langTag)
        activeFamily = slot.family

        if (!slot.ready) {
            // 엔진을 여는 동안 걸린 요청은 그 엔진이 준비되면 이어서 읽는다.
            init(langTag) { ok ->
                if (request != generation) return@init
                if (ok && !finished.get()) {
                    if (applyLanguage(slot, pendingLang)) speakOnce(1) else finishOnce()
                } else if (!ok) {
                    finishOnce()
                }
            }
            return
        }
        if (!applyLanguage(slot, pendingLang)) {
            // 이 기기에 해당 언어 음성이 없으면 소리 없이 다음 문제로 넘어간다.
            handler.removeCallbacks(safety)
            handler.postDelayed({ finishOnce() }, 250)
            return
        }
        speakOnce(1)
    }

    private fun speakOnce(round: Int) {
        val engine = slots[activeFamily]?.tts
        val text = pendingText ?: run {
            finishOnce()
            return
        }
        if (engine == null) {
            finishOnce()
            return
        }
        val id = if (round == 1) UTTERANCE_FIRST else UTTERANCE_SECOND
        // 실제 발화가 시작되는 순간 안전 타이머를 새로 건다(엔진을 열느라 늦어진 경우에도 충분히 읽도록).
        if (round == 1) armSafety()
        // v2.10: 1번째·2번째 모두 0.90 속도 · 1.00 음높이.
        val rate = rateFor(round)
        appliedRates.add(rate)
        appliedPitches.add(PITCH)
        runCatching { engine.setSpeechRate(rate) }
        runCatching { engine.setPitch(PITCH) }
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
        }
        val result = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        }.getOrDefault(TextToSpeech.ERROR)
        if (result != TextToSpeech.SUCCESS) finishOnce()
    }

    /**
     * 그 언어의 지역과 목소리를 엔진에 적용한다.
     * 영어는 en-AU → en-US → en-GB → en 순으로, 한국어는 ko-KR 로 시도한다.
     * 그 지역 목소리가 이 엔진에 아예 없으면 다음 지역으로 넘어간다(폴백에서도 영어가 자연스럽게 나오도록).
     */
    private fun applyLanguage(slot: Slot, langTag: String): Boolean {
        val engine = slot.tts ?: return false
        var accepted = false
        preferredLocales(langTag).forEach { locale ->
            val status = runCatching { engine.setLanguage(locale) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            val ok = status != TextToSpeech.LANG_MISSING_DATA &&
                status != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ok) return@forEach
            accepted = true
            if (chooseVoice(engine, locale)) return true
            // 이 지역 목소리가 목록에 없으면 다음 지역(en-AU → en-US …)을 본다.
        }
        // 지역은 받아들였지만 목소리 목록을 못 고른 경우 — 엔진 기본 목소리로 읽는다.
        return accepted
    }

    /**
     * 그 지역에 맞는 목소리를 고른다.
     * 1) `ko-KR-language` · `en-AU-language` 처럼 이름이 붙은 목소리
     * 2) 같은 지역의 오프라인 목소리(인터넷 없이도 나는 소리)
     * 3) 같은 지역의 아무 목소리
     * 고를 목소리가 없으면 false — 다음 지역(en-AU → en-US …)으로 넘어간다.
     * 엔진이 목소리 목록을 알려 주지 않으면 그 지역 기본 목소리를 그대로 쓰고 true.
     */
    private fun chooseVoice(engine: TextToSpeech, locale: Locale): Boolean {
        val voices: List<Voice> = runCatching { engine.voices?.toList() }.getOrNull().orEmpty()
        if (voices.isEmpty()) return true
        val named = voices.firstOrNull { it.name.equals(voiceNameFor(locale), ignoreCase = true) }
        // 같은 지역의 오프라인 목소리 중 품질이 가장 좋은 것(이름 순서로 고정).
        val offline = voices
            .filter { it.locale == locale && !it.isNetworkConnectionRequired }
            .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })
            .firstOrNull()
        val anySameCountry = voices.firstOrNull {
            it.locale.language == locale.language && it.locale.country == locale.country
        }
        // 나라 코드가 비어 있는 목소리(ko 등)만 있어도 그 언어면 쓴다.
        val anySameLanguage = voices.firstOrNull { it.locale.language == locale.language }
        val chosen = named ?: offline ?: anySameCountry ?: anySameLanguage ?: return false
        runCatching { engine.voice = chosen }
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
        slots.values.forEach { slot -> runCatching { slot.tts?.stop() } }
    }

    fun shutdown() {
        stop()
        slots.values.forEach { closeSlot(it) }
        slots.clear()
    }

    private fun closeSlot(slot: Slot) {
        runCatching {
            slot.tts?.stop()
            slot.tts?.shutdown()
        }
        slot.tts = null
        slot.ready = false
    }

    private fun slotFor(langTag: String): Slot {
        val family = if (isEnglish(langTag)) "en" else "ko"
        return slots.getOrPut(family) { Slot(family) }
    }

    // ---------------------------------------------------------------- 테스트용 훅

    /** 그 언어로 실제 연 엔진 패키지(기기 기본 엔진이면 null). 아직 안 열었으면 null. */
    internal fun enginePackageForTest(langTag: String): String? {
        val family = if (isEnglish(langTag)) "en" else "ko"
        return slots[family]?.enginePackage
    }

    /** 그 언어의 엔진이 준비됐는지. */
    internal fun isReadyForTest(langTag: String): Boolean {
        val family = if (isEnglish(langTag)) "en" else "ko"
        return slots[family]?.ready == true
    }

    /** 그 언어의 엔진이 지금 향한 목소리 이름(엔진이 알려 주면). */
    internal fun voiceNameForTest(langTag: String): String? {
        val family = if (isEnglish(langTag)) "en" else "ko"
        return runCatching { slots[family]?.tts?.voice?.name }.getOrNull()
    }
}
