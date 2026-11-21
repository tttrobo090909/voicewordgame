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

    // ------------------------------------------------ v2.1: 1글자 한글 힌트 스포일러 방지

    @Test
    fun singleLetterKoreanWordShowsOnlyItsChoseong() {
        assertEquals("ㅅ", Hints.defaultHint(Word(1, "손", "Hand", 6, "words/1.webp"), "ko"))
        assertEquals("ㄴ", Hints.defaultHint(Word(2, "눈", "Eye", 6, "words/2.webp"), "ko"))
        assertEquals("ㅋ", Hints.defaultHint(Word(3, "코", "Nose", 6, "words/3.webp"), "ko"))
        assertEquals("ㄱ", Hints.defaultHint(Word(4, "공", "Ball", 4, "words/4.webp"), "ko"))
        assertEquals("ㅈ", Hints.defaultHint(Word(5, "집", "House", 4, "words/5.webp"), "ko"))
        assertEquals("ㅎ", Hints.defaultHint(Word(6, "해", "Sun", 5, "words/6.webp"), "ko"))
        assertEquals("ㄷ", Hints.defaultHint(Word(7, "달", "moon", 5, "words/7.webp"), "ko"))

        // 배지 문구도 글자가 아니라 초성으로 나온다.
        val hand = Word(8, "손", "Hand", 6, "words/8.webp")
        assertEquals("힌트: ㅅ (1글자)", Hints.badge(hand, "ko"))
    }

    @Test
    fun everySingleLetterKoreanWordInTheBookHidesBehindChoseong() {
        val singles = assetBook().words.filter { it.ko.length == 1 }
        assertTrue("1글자 한글 단어가 있어야 함", singles.isNotEmpty())
        singles.forEach { word ->
            val shown = Hints.hint(word, "ko")
            assertEquals("${word.ko} 힌트는 초성 한 글자", 1, shown.length)
            assertEquals("${word.ko} 의 초성", Hints.choseong(word.ko[0]), shown)
            assertTrue("힌트가 정답 글자를 그대로 보여 주면 안 됨: ${word.ko}", shown != word.ko)
        }
    }

    @Test
    fun choseongCoversTheNineteenLeadingConsonants() {
        assertEquals("ㄱ", Hints.choseong('가'))
        assertEquals("ㄲ", Hints.choseong('까'))
        assertEquals("ㄴ", Hints.choseong('나'))
        assertEquals("ㄷ", Hints.choseong('다'))
        assertEquals("ㄸ", Hints.choseong('따'))
        assertEquals("ㄹ", Hints.choseong('라'))
        assertEquals("ㅁ", Hints.choseong('마'))
        assertEquals("ㅂ", Hints.choseong('바'))
        assertEquals("ㅃ", Hints.choseong('빠'))
        assertEquals("ㅅ", Hints.choseong('사'))
        assertEquals("ㅆ", Hints.choseong('싸'))
        assertEquals("ㅇ", Hints.choseong('아'))
        assertEquals("ㅈ", Hints.choseong('자'))
        assertEquals("ㅉ", Hints.choseong('짜'))
        assertEquals("ㅊ", Hints.choseong('차'))
        assertEquals("ㅋ", Hints.choseong('카'))
        assertEquals("ㅌ", Hints.choseong('타'))
        assertEquals("ㅍ", Hints.choseong('파'))
        assertEquals("ㅎ", Hints.choseong('하'))
        // 한글 음절이 아니면 글자 그대로.
        assertEquals("A", Hints.choseong('A'))
    }

    @Test
    fun englishShortWordsKeepTheOldFirstLetterMask() {
        assertEquals("B○○", Hints.defaultHint(Word(1, "벌", "Bee", 1, "words/1.webp"), "en"))
        assertEquals("O○", Hints.defaultHint(Word(2, "소", "Ox", 1, "words/2.webp"), "en"))
        assertEquals("A", Hints.defaultHint(Word(3, "에이", "A", 1, "words/3.webp"), "en"))
    }

    @Test
    fun customHintStillWinsForSingleLetterWord() {
        val word = Word(1, "손", "Hand", 6, "words/1.webp", hint = "다섯 손가락")
        assertEquals("다섯 손가락", Hints.hint(word, "ko"))
        assertEquals("힌트: 다섯 손가락 (1글자)", Hints.badge(word, "ko"))
    }
}
