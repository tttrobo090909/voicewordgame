package com.pipi.voicewordgame

import com.pipi.voicewordgame.game.AnswerMatcher
import com.pipi.voicewordgame.game.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerMatcherTest {

    private val dog = Word(
        id = 1, ko = "강아지", en = "Dog", categoryId = 1, image = "words/001.webp",
        synKo = listOf("멍멍이", "댕댕이", "개"), synEn = listOf("puppy", "doggy")
    )
    private val sun = Word(
        id = 126, ko = "해", en = "Sun", categoryId = 6, image = "words/126.webp",
        synKo = listOf("태양"), synEn = listOf("sun")
    )
    private val lion = Word(
        id = 4, ko = "사자", en = "Lion", categoryId = 1, image = "words/004.webp",
        synKo = emptyList(), synEn = emptyList()
    )
    private val glue = Word(
        id = 206, ko = "풀", en = "Glue", categoryId = 9, image = "words/206.webp",
        synKo = listOf("접착제", "본드"), synEn = listOf("glue")
    )

    @Test
    fun acceptsExactWord() {
        assertEquals("강아지", AnswerMatcher.firstMatch(listOf("강아지"), dog, "ko"))
    }

    @Test
    fun acceptsChildSynonym() {
        assertEquals("멍멍이", AnswerMatcher.firstMatch(listOf("멍멍이"), dog, "ko"))
    }

    @Test
    fun ignoresParticlesAndSpacing() {
        assertNotNull(AnswerMatcher.firstMatch(listOf("강아지예요"), dog, "ko"))
        assertNotNull(AnswerMatcher.firstMatch(listOf("강 아지"), dog, "ko"))
        assertNotNull(AnswerMatcher.firstMatch(listOf("강아지!"), dog, "ko"))
    }

    @Test
    fun acceptsOneSyllableWord() {
        assertNotNull(AnswerMatcher.firstMatch(listOf("해"), sun, "ko"))
        assertNotNull(AnswerMatcher.firstMatch(listOf("해예요"), sun, "ko"))
    }

    @Test
    fun acceptsEnglishInKoreanMode() {
        assertNotNull(AnswerMatcher.firstMatch(listOf("dog"), dog, "ko"))
    }

    @Test
    fun acceptsKoreanInEnglishMode() {
        assertNotNull(AnswerMatcher.firstMatch(listOf("강아지"), dog, "en"))
        assertNotNull(AnswerMatcher.firstMatch(listOf("puppy"), dog, "en"))
    }

    @Test
    fun rejectsDifferentWord() {
        assertNull(AnswerMatcher.firstMatch(listOf("고양이"), dog, "ko"))
        assertNull(AnswerMatcher.firstMatch(listOf("엄마"), dog, "ko"))
    }

    @Test
    fun rejectsShortSubstringNoise() {
        // 1음절 단어가 다른 말 속에 우연히 들어 있어도 정답으로 보지 않는다.
        assertNull(AnswerMatcher.firstMatch(listOf("글루"), glue, "ko"))
        assertNull(AnswerMatcher.firstMatch(listOf("사장님"), lion, "ko"))
    }

    @Test
    fun takesFirstCorrectAlternative() {
        val picked = AnswerMatcher.firstMatch(listOf("고양이", "강아지", "멍멍이"), dog, "ko")
        assertEquals("강아지", picked)
    }

    @Test
    fun acceptsSiblingPictureWord() {
        // 같은 그림을 쓰는 낱말(침대/이불)은 서로 정답으로 인정한다.
        val bed = Word(179, "침대", "Bed", 8, "words/179.webp", emptyList(), emptyList())
        val blanket = Word(181, "이불", "Blanket", 8, "words/179.webp", emptyList(), emptyList())
        assertNotNull(AnswerMatcher.firstMatchAny(listOf("침대"), listOf(blanket, bed), "ko"))
        assertNotNull(AnswerMatcher.firstMatchAny(listOf("blanket"), listOf(blanket, bed), "en"))
    }

    @Test
    fun normalizationHandlesCaseAndPunctuation() {
        assertTrue(AnswerMatcher.normalize("  Dog! ").equals("dog"))
        assertTrue(AnswerMatcher.normalize("강아지~").equals("강아지"))
    }
}
