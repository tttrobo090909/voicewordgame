package com.pipi.voicewordgame.speech

/**
 * 어떤 음성인식 엔진을 쓸지 고르는 순수 로직(안드로이드 타입 없음 → 단위 테스트 가능).
 *
 * 실기기에서 검증된 방식(dictationtest v1.2):
 * 1순위 구글 TTS 패키지가 제공하는 인식기(`GoogleTTSRecognitionService`)를 ComponentName 으로 직접 지정,
 * 기기에 없으면 시스템 기본 인식기로 대체.
 */
object RecognitionEngines {

    const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
    const val GOOGLE_TTS_SERVICE_CLASS =
        "com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService"
    const val GOOGLE_TTS_COMPONENT = "$GOOGLE_TTS_PACKAGE/$GOOGLE_TTS_SERVICE_CLASS"

    /** @param component 명시할 ComponentName(package/class). null 이면 시스템 기본 인식기. */
    data class Plan(val component: String?, val reason: String) {
        val usesGoogle: Boolean get() = component != null
    }

    /** PackageManager 가 돌려준 인식 서비스 목록(package/class)에 구글 인식기가 있는지. */
    fun hasGoogleTts(services: List<String>): Boolean =
        services.any { it.startsWith("$GOOGLE_TTS_PACKAGE/") }

    fun plan(services: List<String>): Plan =
        if (hasGoogleTts(services)) {
            Plan(GOOGLE_TTS_COMPONENT, "구글 인식기를 직접 지정")
        } else {
            Plan(null, "구글 인식기가 없어 시스템 기본 인식기 사용")
        }
}
