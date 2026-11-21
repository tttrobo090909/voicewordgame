package com.pipi.voicewordgame.game

import org.json.JSONArray
import org.json.JSONObject

/** 목소리 지문: 음높이(F0), 스펙트럼 중심, 13밴드 에너지 분포. */
class VoiceProfile(
    val pitch: Float,
    val centroid: Float,
    val bands: FloatArray,
    val levelDb: Float,
    val seconds: Float
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("pitch", pitch.toDouble())
        o.put("centroid", centroid.toDouble())
        o.put("level", levelDb.toDouble())
        o.put("seconds", seconds.toDouble())
        val arr = JSONArray()
        bands.forEach { arr.put(it.toDouble()) }
        o.put("bands", arr)
        return o.toString()
    }

    companion object {
        fun fromJson(raw: String?): VoiceProfile? {
            if (raw.isNullOrBlank()) return null
            return try {
                val o = JSONObject(raw)
                val arr = o.optJSONArray("bands") ?: return null
                if (arr.length() == 0) return null
                val bands = FloatArray(arr.length()) { arr.getDouble(it).toFloat() }
                VoiceProfile(
                    pitch = o.optDouble("pitch", 0.0).toFloat(),
                    centroid = o.optDouble("centroid", 0.0).toFloat(),
                    bands = bands,
                    levelDb = o.optDouble("level", -60.0).toFloat(),
                    seconds = o.optDouble("seconds", 0.0).toFloat()
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
