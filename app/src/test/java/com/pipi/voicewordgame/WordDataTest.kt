package com.pipi.voicewordgame

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pipi.voicewordgame.game.Hints
import com.pipi.voicewordgame.game.Word
import com.pipi.voicewordgame.game.WordJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 기본 250문제가 6개 카테고리에 빠짐없이 들어갔는지, 그림·힌트가 준비됐는지 확인한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WordDataTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun assetBook() = WordJson.parse(
        context.assets.open("words.json").bufferedReader().use { it.readText() }
    )

    @Test
    fun sixCategoriesWithRequestedNames() {
        val labels = assetBook().categories.map { it.ko }
        assertEquals(listOf("동물", "음식·과일", "탈것", "사물·집", "자연", "사람·몸"), labels)
    }

    @Test
    fun allTwoHundredFiftyWordsMappedToThoseSixCategories() {
        val book = assetBook()
        assertEquals(250, book.words.size)
        assertEquals(250, book.words.map { it.id }.distinct().size)
        assertTrue(book.words.all { it.categoryId in 1..6 })

        val counts = book.words.groupingBy { it.categoryId }.eachCount()
        assertEquals(51, counts[1])
        assertEquals(50, counts[2])
        assertEquals(25, counts[3])
        assertEquals(69, counts[4])
        assertEquals(24, counts[5])
        assertEquals(31, counts[6])
        assertEquals(250, counts.values.sum())
        // 비어 있는 카테고리가 없어야 한다.
        assertTrue(counts.values.all { it > 0 })
    }

    @Test
    fun dinosaurIsAnimalAndAstronautIsPerson() {
        val book = assetBook()
        assertEquals(1, book.words.first { it.ko == "공룡" }.categoryId)
        assertEquals(6, book.words.first { it.ko == "우주인" }.categoryId)
        assertEquals(6, book.words.first { it.ko == "요리사" }.categoryId)
    }

    @Test
    fun everyDefaultPictureExists() {
        assetBook().words.forEach { word ->
            val stream = runCatching { context.assets.open(word.image) }.getOrNull()
            assertTrue("그림 없음: ${word.image} (${word.ko})", stream != null)
            stream?.close()
        }
    }

    @Test
    fun defaultHintShowsFirstLetterAndMaskedRest() {
        val word = Word(1, "인형", "Doll", 4, "words/199.webp")
        assertEquals("인○", Hints.defaultHint(word, "ko"))
        assertEquals("힌트: 인○ (2글자)", Hints.badge(word, "ko"))
    }

    @Test
    fun customHintOverridesDefault() {
        val word = Word(1, "인형", "Doll", 4, "words/199.webp", hint = "장난감")
        assertEquals("장난감", Hints.hint(word, "ko"))
        assertEquals("힌트: 장난감 (2글자)", Hints.badge(word, "ko"))
    }
}
