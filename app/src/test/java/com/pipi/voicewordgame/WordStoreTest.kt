package com.pipi.voicewordgame

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pipi.voicewordgame.game.Word
import com.pipi.voicewordgame.game.WordStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 관리자 화면의 추가·수정·삭제·기본값 복원이 앱 내부 파일에 저장되고 다시 읽히는지 확인한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WordStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 테스트마다 기본값에서 시작
        WordStore(context).restoreDefaults()
    }

    @Test
    fun startsFromDefault250() {
        val store = WordStore(context)
        store.load()
        assertEquals(250, store.book.words.size)
        assertEquals("2.5", store.book.appVersion)
    }

    @Test
    fun addedProblemIsSavedAndVisibleInGamePool() {
        val store = WordStore(context)
        store.load()
        val created = store.add(
            Word(
                id = 0, ko = "로봇청소기", en = "Robot vacuum", categoryId = 4,
                image = "words/001.webp", synKo = listOf("청소기"), hint = ""
            )
        )
        assertTrue(created.id > 250)

        val reloaded = WordStore(context)
        reloaded.load()
        val found = reloaded.book.find(created.id)
        assertNotNull(found)
        assertEquals("로봇청소기", found!!.ko)
        assertTrue(reloaded.book.byCategory(4).any { it.id == created.id })
        assertEquals(251, reloaded.book.words.size)
    }

    @Test
    fun editedProblemKeepsPictureHintAndSynonyms() {
        val store = WordStore(context)
        store.load()
        val original = store.book.words.first { it.ko == "강아지" }
        store.update(
            original.copy(
                ko = "멍멍이",
                en = "Puppy",
                image = "file:images/custom_test.webp",
                synKo = listOf("강아지", "개"),
                hint = "왈왈"
            )
        )

        val reloaded = WordStore(context)
        reloaded.load()
        val edited = reloaded.book.find(original.id)!!
        assertEquals("멍멍이", edited.ko)
        assertEquals("file:images/custom_test.webp", edited.image)
        assertEquals("왈왈", edited.hint)
        assertEquals(listOf("강아지", "개"), edited.synKo)
    }

    @Test
    fun deletedProblemIsGoneAfterReload() {
        val store = WordStore(context)
        store.load()
        val target = store.book.words.first()
        store.delete(target.id)

        val reloaded = WordStore(context)
        reloaded.load()
        assertNull(reloaded.book.find(target.id))
        assertEquals(249, reloaded.book.words.size)
    }

    @Test
    fun restoreDefaultsBringsBackOriginalData() {
        val store = WordStore(context)
        store.load()
        val target = store.book.words.first()
        store.delete(target.id)
        store.add(Word(0, "테스트", "Test", 1, "words/001.webp"))

        val restored = WordStore(context)
        restored.load()
        restored.restoreDefaults()

        val reloaded = WordStore(context)
        reloaded.load()
        assertEquals(250, reloaded.book.words.size)
        assertNotNull(reloaded.book.find(target.id))
        assertTrue(reloaded.book.words.none { it.ko == "테스트" })
    }
}
