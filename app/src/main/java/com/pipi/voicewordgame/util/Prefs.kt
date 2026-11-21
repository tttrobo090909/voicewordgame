package com.pipi.voicewordgame.util

import android.content.Context
import com.pipi.voicewordgame.game.Player
import com.pipi.voicewordgame.game.VoiceProfile
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("voicewordgame", Context.MODE_PRIVATE)

    var lang: String
        get() = sp.getString("lang", "ko") ?: "ko"
        set(value) = sp.edit().putString("lang", value).apply()

    var categoryId: Int
        get() = sp.getInt("category", 0)
        set(value) = sp.edit().putInt("category", value).apply()

    var hasNoiseBaseline: Boolean
        get() = sp.getBoolean("noise_set", false)
        set(value) = sp.edit().putBoolean("noise_set", value).apply()

    var noiseBaselineDb: Float
        get() = sp.getFloat("noise_db", -60f)
        set(value) = sp.edit().putFloat("noise_db", value).apply()

    /** 이 기기에서 파이프 오디오 소스를 쓸 수 없으면 true (마이크 직접 사용 모드). */
    var pipeBlocked: Boolean
        get() = sp.getBoolean("pipe_blocked", false)
        set(value) = sp.edit().putBoolean("pipe_blocked", value).apply()

    fun noiseBaselineOrNull(): Float? = if (hasNoiseBaseline) noiseBaselineDb else null

    fun loadPlayers(): MutableList<Player> {
        val raw = sp.getString("players", null)
        val out = ArrayList<Player>()
        if (raw.isNullOrBlank()) return out
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Player(
                        id = o.optLong("id", (i + 1).toLong()),
                        name = o.optString("name", ""),
                        avatarId = o.optInt("avatar", i),
                        profile = VoiceProfile.fromJson(o.optString("profile", ""))
                    )
                )
            }
        } catch (e: Exception) {
            out.clear()
        }
        return out
    }

    fun savePlayers(players: List<Player>) {
        val arr = JSONArray()
        players.forEach { p ->
            val o = JSONObject()
            o.put("id", p.id)
            o.put("name", p.name)
            o.put("avatar", p.avatarId)
            p.profile?.let { o.put("profile", it.toJson()) }
            arr.put(o)
        }
        sp.edit().putString("players", arr.toString()).apply()
    }
}
