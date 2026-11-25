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
import java.security.MessageDigest

/** 기본 420문제(v2.9, 들어봐요! v1.1과 동일)가 6개 카테고리에 빠짐없이 들어갔는지, 그림·힌트가 준비됐는지 확인한다. */
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
    fun allFourHundredTwentyWordsMappedToThoseSixCategories() {
        val book = assetBook()
        assertEquals(420, book.words.size)
        assertEquals(420, book.words.map { it.id }.distinct().size)
        assertTrue(book.words.all { it.categoryId in 1..6 })

        val counts = book.words.groupingBy { it.categoryId }.eachCount()
        assertEquals(90, counts[1])
        assertEquals(94, counts[2])
        assertEquals(37, counts[3])
        assertEquals(116, counts[4])
        assertEquals(41, counts[5])
        assertEquals(42, counts[6])
        assertEquals(420, counts.values.sum())
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

    // ------------------------------------------------ v2.4: 250단어·그림 전면 정리

    private fun md5(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun koreanAndEnglishWordsAreAllUniqueAcrossTheWholeBook() {
        val words = assetBook().words
        assertEquals(420, words.size)
        // v2.9: `배`는 과일(Pear, 59)과 배(Ship, 110) 두 가지 — 그림이 달라 둘 다 둔다.
        assertEquals("한글 단어 중복은 배 하나뿐", 419, words.map { it.ko }.distinct().size)
        assertEquals("배 두 개", listOf("Pear", "Ship"), words.filter { it.ko == "배" }.map { it.en }.sorted())
        assertEquals("영문 단어 중복 0건", 420, words.map { it.en.lowercase() }.distinct().size)
    }

    @Test
    fun everyPictureFileIsActuallyDifferent() {
        val words = assetBook().words
        val hashes = words.map { word ->
            val bytes = context.assets.open(word.image).use { it.readBytes() }
            md5(bytes)
        }
        assertEquals("그림 420장이 모두 다른 파일", 420, hashes.distinct().size)
    }

    @Test
    fun v24WordsMatchTheirPictures() {
        val book = assetBook()
        // B. 그림에 맞춰 단어 교체(13)
        val renamed = mapOf(
            "커피" to "Coffee", "크루아상" to "Croissant", "약" to "Medicine",
            "서커스" to "Circus", "도시락" to "Lunchbox", "선물" to "Gift",
            "돋보기" to "Magnifying glass", "코알라" to "Koala", "아보카도" to "Avocado",
            "카레" to "Curry", "화산" to "Volcano", "회전목마" to "Carousel",
            "성" to "Castle"
        )
        renamed.forEach { (ko, en) ->
            val word = book.words.firstOrNull { it.ko == ko }
            assertTrue("$ko 단어가 있어야 함", word != null)
            assertEquals("$ko 의 영문", en, word!!.en)
        }
        // 옛 단어는 사라졌다.
        // (매미·오렌지·국수는 v2.6에서 새 그림과 함께 다시 들어왔다)
        listOf("카페", "베이커리", "약국", "동물원", "식당", "마트", "도서관", "호수", "놀이터", "유치원")
            .forEach { old -> assertTrue("옛 단어 $old 는 없다", book.words.none { it.ko == old }) }

        // A. 3D 그림을 새로 만든 14개도 그대로 있다.
        listOf("선풍기", "냉장고", "세탁기", "청소기", "불도저", "포크레인", "열기구",
            "치마", "이불", "책상", "그네", "경찰서", "소방서", "강")
            .forEach { ko -> assertTrue("$ko 단어가 있어야 함", book.words.any { it.ko == ko }) }
    }

    @Test
    fun v29NamesAndCategoriesMatchListeningGame() {
        val book = assetBook()
        // v2.9(들어봐요! v1.1): 배(과일 Pear)는 그대로, 보트는 배(Ship)로 이름을 바꿔 둘 다 둔다.
        assertEquals("Pear", book.words.first { it.ko == "배" }.en)
        assertEquals("Ship", book.words.first { it.ko == "배" && it.en == "Ship" }.en)
        assertEquals("3", book.words.first { it.ko == "배" && it.en == "Ship" }.categoryId.toString())
        // 바다 → 파도, 갈비 → 갈비찜.
        assertEquals("Wave", book.words.first { it.ko == "파도" }.en)
        assertEquals("Galbijjim", book.words.first { it.ko == "갈비찜" }.en)
        // 눈(신체)과 눈송이는 그대로 구분된다.
        assertEquals("Snowflake", book.words.first { it.ko == "눈송이" }.en)
        assertEquals("Eye", book.words.first { it.ko == "눈" }.en)
        assertEquals("Lotion", book.words.first { it.ko == "로션" }.en)
    }

    @Test
    fun v29DeletedWordsAreGone() {
        val book = assetBook()
        listOf("잉어", "모노레일", "글라이더", "전동휠체어", "풀", "보트", "바다", "갈비")
            .forEach { gone -> assertTrue("삭제·개명된 단어 $gone 는 없다", book.words.none { it.ko == gone }) }
        // 삭제된 그림 파일도 앱에 남아 있지 않다.
        listOf("words/144.webp", "words/278.webp", "words/339.webp", "words/340.webp", "words/346.webp")
            .forEach { img ->
                val stream = runCatching { context.assets.open(img) }.getOrNull()
                assertTrue("삭제된 그림 $img 가 남아 있다", stream == null)
                stream?.close()
            }
    }

    @Test
    fun v29MovedWordsAreInFoodAndFruit() {
        val book = assetBook()
        listOf("도시락", "커피", "크루아상").forEach { ko ->
            assertEquals("$ko 는 음식·과일", 2, book.words.first { it.ko == ko }.categoryId)
        }
    }

    @Test
    fun newSingleLetterWordsStillHideBehindChoseong() {
        val book = assetBook()
        listOf("약" to "ㅇ", "성" to "ㅅ", "강" to "ㄱ").forEach { (ko, choseong) ->
            val word = book.words.first { it.ko == ko }
            assertEquals("$ko 힌트", choseong, Hints.hint(word, "ko"))
            assertEquals("힌트: $choseong (1글자)", Hints.badge(word, "ko"))
        }
    }
}
