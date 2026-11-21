package com.pipi.voicewordgame.game

import org.json.JSONArray
import org.json.JSONObject

/** 문제 한 개. [hint] 가 비어 있으면 [Hints] 가 첫 글자 힌트를 자동으로 만든다. */
data class Word(
    val id: Int,
    val ko: String,
    val en: String,
    val categoryId: Int,
    val image: String,
    val synKo: List<String> = emptyList(),
    val synEn: List<String> = emptyList(),
    val hint: String = ""
) {
    fun text(lang: String): String = if (lang == "en") en else ko

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("ko", ko)
        o.put("en", en)
        o.put("category", categoryId)
        o.put("image", image)
        o.put("synKo", JSONArray(synKo))
        o.put("synEn", JSONArray(synEn))
        o.put("hint", hint)
        return o
    }
}

/** v2.0 카테고리: 동물 / 음식·과일 / 탈것 / 사물·집 / 자연 / 사람·몸 */
data class WordCategory(val id: Int, val ko: String, val en: String) {
    fun label(lang: String): String = if (lang == "en") en else ko
}

data class WordBook(
    val words: List<Word>,
    val categories: List<WordCategory>,
    val appVersion: String = ""
) {
    fun byCategory(categoryId: Int): List<Word> =
        if (categoryId <= 0) words else words.filter { it.categoryId == categoryId }

    fun find(id: Int): Word? = words.firstOrNull { it.id == id }

    fun categoryOf(word: Word): WordCategory? = categories.firstOrNull { it.id == word.categoryId }

    fun copyWithWords(words: List<Word>): WordBook = copy(words = words)
}

/** words.json 읽기/쓰기(앱 assets 과 내부 저장 파일이 같은 형식을 쓴다). */
object WordJson {

    const val SCHEMA = 2

    fun parse(raw: String): WordBook {
        val root = JSONObject(raw)

        val catArr = root.optJSONArray("categories") ?: JSONArray()
        val categories = ArrayList<WordCategory>(catArr.length())
        for (i in 0 until catArr.length()) {
            val o = catArr.getJSONObject(i)
            categories.add(
                WordCategory(
                    id = o.getInt("id"),
                    ko = o.getString("ko"),
                    en = o.optString("en", o.getString("ko"))
                )
            )
        }

        val wordArr = root.optJSONArray("words") ?: JSONArray()
        val words = ArrayList<Word>(wordArr.length())
        for (i in 0 until wordArr.length()) {
            val o = wordArr.getJSONObject(i)
            words.add(
                Word(
                    id = o.getInt("id"),
                    ko = o.getString("ko"),
                    en = o.optString("en", ""),
                    categoryId = o.optInt("category", 1),
                    image = o.getString("image"),
                    synKo = strings(o.optJSONArray("synKo")),
                    synEn = strings(o.optJSONArray("synEn")),
                    hint = o.optString("hint", "")
                )
            )
        }

        return WordBook(words, categories, root.optString("appVersion", ""))
    }

    fun write(book: WordBook): String {
        val root = JSONObject()
        root.put("schema", SCHEMA)
        root.put("appVersion", book.appVersion)
        root.put("wordCount", book.words.size)

        val cats = JSONArray()
        book.categories.forEach { c ->
            cats.put(JSONObject().apply {
                put("id", c.id)
                put("ko", c.ko)
                put("en", c.en)
            })
        }
        root.put("categories", cats)

        val arr = JSONArray()
        book.words.forEach { arr.put(it.toJson()) }
        root.put("words", arr)
        return root.toString(1)
    }

    private fun strings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "")
            if (s.isNotBlank()) out.add(s)
        }
        return out
    }
}
