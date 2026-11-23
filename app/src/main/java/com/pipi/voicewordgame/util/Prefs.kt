package com.pipi.voicewordgame.util

import android.content.Context

/**
 * 게임 설정(언어·마지막 카테고리·한 판 문제 수·문제 제한시간)만 기억한다.
 * 문제 데이터는 [com.pipi.voicewordgame.game.WordStore] 가 맡는다.
 */
class Prefs(context: Context) {

    companion object {
        /** 한 판 문제 수: 10~40, 기본 15. (v2.6: 상한 50 → 40) */
        const val COUNT_MIN = 10
        const val COUNT_MAX = 40
        const val COUNT_DEFAULT = 15

        /** 문제 제한시간(초): 3~20, 기본 9. */
        const val TIME_MIN = 3
        const val TIME_MAX = 20
        const val TIME_DEFAULT = 9
    }

    private val sp = context.applicationContext.getSharedPreferences("voicewordgame", Context.MODE_PRIVATE)

    var lang: String
        get() = sp.getString("lang", "ko") ?: "ko"
        set(value) = sp.edit().putString("lang", value).apply()

    /** 0 = 전체 무작위, 1~6 = 카테고리 */
    var categoryId: Int
        get() = sp.getInt("category", 0)
        set(value) = sp.edit().putInt("category", value).apply()

    /** 한 판에 낼 문제 수(10~40, 기본 15). 저장값이 41 이상이면 40으로 맞춘다. */
    var questionCount: Int
        get() = sp.getInt("question_count", COUNT_DEFAULT).coerceIn(COUNT_MIN, COUNT_MAX)
        set(value) = sp.edit().putInt("question_count", value.coerceIn(COUNT_MIN, COUNT_MAX)).apply()

    /** 한 문제 제한시간(초, 3~20, 기본 9). */
    var timeLimitSeconds: Int
        get() = sp.getInt("time_limit_seconds", TIME_DEFAULT).coerceIn(TIME_MIN, TIME_MAX)
        set(value) = sp.edit().putInt("time_limit_seconds", value.coerceIn(TIME_MIN, TIME_MAX)).apply()

    /** 설정만 기본값(15문제·9초)으로 되돌린다. 문제 목록은 건드리지 않는다. */
    fun resetGameSettings() {
        sp.edit()
            .putInt("question_count", COUNT_DEFAULT)
            .putInt("time_limit_seconds", TIME_DEFAULT)
            .apply()
    }
}
