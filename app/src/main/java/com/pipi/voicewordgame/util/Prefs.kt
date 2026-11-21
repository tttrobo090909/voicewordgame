package com.pipi.voicewordgame.util

import android.content.Context

/** 게임 설정(언어·마지막 카테고리)만 기억한다. 문제 데이터는 [com.pipi.voicewordgame.game.WordStore] 가 맡는다. */
class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("voicewordgame", Context.MODE_PRIVATE)

    var lang: String
        get() = sp.getString("lang", "ko") ?: "ko"
        set(value) = sp.edit().putString("lang", value).apply()

    /** 0 = 전체 무작위, 1~6 = 카테고리 */
    var categoryId: Int
        get() = sp.getInt("category", 0)
        set(value) = sp.edit().putInt("category", value).apply()
}
