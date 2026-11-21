package com.pipi.voicewordgame.game

import android.content.Context
import org.json.JSONObject

data class Word(
    val id: Int,
    val ko: String,
    val en: String,
    val categoryId: Int,
    val image: String,
    val synKo: List<String>,
    val synEn: List<String>
) {
    fun text(lang: String): String = if (lang == "en") en else ko
}

data class WordCategory(val id: Int, val ko: String, val en: String, val image: String) {
    fun label(lang: String): String = if (lang == "en") en else ko
}

data class AvatarInfo(val id: Int, val name: String, val image: String)

/** assets/words.json 을 읽어 250단어·10카테고리·아바타를 제공한다. */
object WordRepository {

    var words: List<Word> = emptyList()
        private set
    var categories: List<WordCategory> = emptyList()
        private set
    var avatars: List<AvatarInfo> = emptyList()
        private set
    var loaded: Boolean = false
        private set

    private var byId: Map<Int, Word> = emptyMap()

    fun load(context: Context) {
        if (loaded) return
        val raw = context.assets.open("words.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)

        val catArr = root.getJSONArray("categories")
        val cats = ArrayList<WordCategory>(catArr.length())
        for (i in 0 until catArr.length()) {
            val o = catArr.getJSONObject(i)
            cats.add(
                WordCategory(
                    id = o.getInt("id"),
                    ko = o.getString("ko"),
                    en = o.optString("en", o.getString("ko")),
                    image = o.getString("image")
                )
            )
        }
        categories = cats

        val avatarArr = root.getJSONArray("avatars")
        val avs = ArrayList<AvatarInfo>(avatarArr.length())
        for (i in 0 until avatarArr.length()) {
            val o = avatarArr.getJSONObject(i)
            avs.add(AvatarInfo(o.getInt("id"), o.optString("name", ""), o.getString("image")))
        }
        avatars = avs

        val wordArr = root.getJSONArray("words")
        val list = ArrayList<Word>(wordArr.length())
        for (i in 0 until wordArr.length()) {
            val o = wordArr.getJSONObject(i)
            list.add(
                Word(
                    id = o.getInt("id"),
                    ko = o.getString("ko"),
                    en = o.optString("en", ""),
                    categoryId = o.getInt("category"),
                    image = o.getString("image"),
                    synKo = stringList(o.optJSONArray("synKo")),
                    synEn = stringList(o.optJSONArray("synEn"))
                )
            )
        }
        words = list
        byId = list.associateBy { it.id }
        loaded = true
    }

    private fun stringList(arr: org.json.JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "")
            if (s.isNotBlank()) out.add(s)
        }
        return out
    }

    fun find(id: Int): Word? = byId[id]

    fun categoryOf(word: Word): WordCategory? = categories.firstOrNull { it.id == word.categoryId }

    fun byCategory(categoryId: Int): List<Word> =
        if (categoryId <= 0) words else words.filter { it.categoryId == categoryId }

    /** 같은 그림을 함께 쓰는 단어들(이모지 한 장으로는 구분이 어려운 낱말). */
    fun siblingsOf(word: Word): List<Word> =
        words.filter { it.id != word.id && it.image == word.image }
}
