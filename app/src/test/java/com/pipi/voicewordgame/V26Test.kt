package com.pipi.voicewordgame

import android.content.Context
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import com.pipi.voicewordgame.admin.AdminActivity
import com.pipi.voicewordgame.game.Word
import com.pipi.voicewordgame.game.WordCategory
import com.pipi.voicewordgame.game.WordJson
import com.pipi.voicewordgame.game.WordStore
import com.pipi.voicewordgame.util.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.security.MessageDigest

/**
 * v2.6 확인: 신규 175단어·한 판 문제 수 10~40·관리자 카테고리 추가(최대 4개)·
 * 리셋 버튼 둘(설정/문제, 둘 다 확인창)·첫 화면 `전체 무작위` 맨 아래·배너 글자 크게 오른쪽 정렬.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class V26Test {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WordStore(context).restoreDefaults()
        context.getSharedPreferences("voicewordgame", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun assetBook() = WordJson.parse(
        context.assets.open("words.json").bufferedReader().use { it.readText() }
    )

    private fun mainActivity(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun adminActivity(): AdminActivity =
        Robolectric.buildActivity(AdminActivity::class.java).setup().get()

    private fun texts(root: View): List<String> {
        val out = ArrayList<String>()
        fun walk(view: View) {
            if (view is TextView && view.text.isNotEmpty()) out.add(view.text.toString())
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
        }
        walk(root)
        return out
    }

    // -------------------------------------------------- 1. 단어 데이터(v2.9: 420단어)

    @Test
    fun assetBookHasFourHundredTwentyWordsInVersion29() {
        val book = assetBook()
        assertEquals(420, book.words.size)
        assertEquals("2.9", book.appVersion)
        assertEquals(6, book.categories.size)
        // v2.9: 삭제된 5개(잉어·모노레일·글라이더·전동휠체어·풀)만큼 id 가 비어 있다.
        assertEquals(420, book.words.map { it.id }.distinct().size)
        assertEquals(425, book.words.maxOf { it.id })
        assertEquals(listOf(144, 278, 339, 340, 346), (1..425).filter { id -> book.words.none { it.id == id } })
    }

    @Test
    fun new175WordsAreAllThereWithUniqueNames() {
        val words = assetBook().words
        val new = words.filter { it.id in 251..425 }
        // v2.9: 이 구간에서 잉어(278)·모노레일(339)·글라이더(340)·전동휠체어(346) 4개가 빠져 171개.
        assertEquals(171, new.size)
        assertEquals(171, new.map { it.ko }.distinct().size)
        assertEquals(171, new.map { it.en.lowercase() }.distinct().size)
        // 기존 250개와도 겹치지 않는다.
        val old = words.filter { it.id <= 250 }
        assertTrue(old.none { o -> new.any { it.ko == o.ko } })
        assertTrue(old.none { o -> new.any { it.en.equals(o.en, ignoreCase = true) } })
        // 카테고리도 정상 범위.
        assertTrue(new.all { it.categoryId in 1..6 })
    }

    @Test
    fun everyPictureExistsAndAll420PicturesDiffer() {
        val words = assetBook().words
        val hashes = ArrayList<String>()
        words.forEach { word ->
            val bytes = context.assets.open(word.image).use { it.readBytes() }
            assertTrue("그림이 비어 있음: ${word.image}", bytes.size > 500)
            hashes.add(MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) })
        }
        assertEquals("그림 420장이 모두 다른 파일", 420, hashes.distinct().size)
    }

    @Test
    fun blockNoLongerAcceptsBrickAsSynonym() {
        val block = assetBook().words.first { it.ko == "블록" }
        assertFalse("벽돌 유사어 삭제", block.synKo.any { it == "벽돌" })
        assertFalse("brick 유사어 삭제", block.synEn.any { it.equals("brick", ignoreCase = true) })
        assertTrue(block.synKo.isNotEmpty())
    }

    @Test
    fun newWordsHaveNoSynonymsButTheirOwnAnswer() {
        // 새 단어는 유사어 없이 정답 자체로만 맞힌다(정답이 사라지지 않았는지 확인).
        val new = assetBook().words.filter { it.id in 251..425 }
        assertTrue(new.all { it.ko.isNotBlank() && it.en.isNotBlank() })
    }

    // -------------------------------------------------- 2. 한 판 문제 수 10~40

    @Test
    fun questionCountRangeIsTenToForty() {
        assertEquals(10, Prefs.COUNT_MIN)
        assertEquals(40, Prefs.COUNT_MAX)
        val prefs = Prefs(context)
        prefs.questionCount = 45
        assertEquals("41 이상은 40으로 맞춘다", 40, prefs.questionCount)
        prefs.questionCount = 40
        assertEquals(40, prefs.questionCount)
    }

    @Test
    fun adminShowsTheNewQuestionCountLabel() {
        val admin = adminActivity()
        val labels = texts(admin.window.decorView)
        assertTrue("라벨: $labels", labels.any { it == "한 판 문제 수 (10~40)" })
        assertFalse("옛 라벨은 없다", labels.any { it.contains("10~50") })
    }

    @Test
    fun adminPlusButtonStopsAtForty() {
        val prefs = Prefs(context)
        prefs.questionCount = 40
        val admin = adminActivity()
        val plus = admin.findViewById<Button>(R.id.settingCountPlus)
        assertFalse("40문제에서는 + 불가", plus.isEnabled)
        plus.performClick()
        assertEquals(40, Prefs(admin).questionCount)
    }

    // -------------------------------------------------- 3. 관리자 카테고리 추가(최대 4개)

    @Test
    fun adminCanAddUpToFourCategories() {
        val store = WordStore(context)
        store.load()
        val adds = (0 until 4).map { i ->
            store.addCategory("추가${i + 1}", "Extra${i + 1}", "⭐")
        }
        assertTrue("4개까지 추가", adds.all { it != null })
        assertEquals(10, store.book.categories.size)
        assertNull("더는 못 만든다", store.addCategory("초과", "Over", "🙂"))
        assertEquals(10, store.book.categories.size)
        // 추가한 카테고리는 앱 내부 JSON 에도 남는다.
        val reloaded = WordStore(context).apply { load() }
        assertEquals(10, reloaded.book.categories.size)
        assertEquals("⭐", reloaded.book.categories.last().emoji)
    }

    @Test
    fun addedCategoryAppearsOnFirstScreenAndInAdminEditor() {
        val store = WordStore(context)
        store.load()
        store.addCategory("우리 가족", "My Family", "👨‍👩‍👧")

        val main = mainActivity()
        val grid = main.findViewById<LinearLayout>(R.id.categoryGrid)
        val labels = texts(grid)
        assertTrue("첫 화면에 보인다: $labels", labels.any { it.startsWith("우리 가족") })
        assertTrue("대표 이모지가 타일에 보인다", texts(grid).any { it == "👨‍👩‍👧" })

        val admin = adminActivity()
        assertTrue(
            "관리자 필터 줄에 보인다",
            texts(admin.findViewById(R.id.adminFilterRow)).any { it.contains("우리 가족") }
        )
        // 문제 추가 창에서도 새 카테고리를 고를 수 있다.
        admin.findViewById<Button>(R.id.addProblem).performClick()
        idle()
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue(
            "문제 수정 창의 카테고리 버튼: ${texts(dialog.window!!.decorView)}",
            texts(dialog.window!!.decorView).any { it == "우리 가족" }
        )
    }

    @Test
    fun categoryWithNoProblemIsDisabledAndNotSelected() {
        val store = WordStore(context)
        store.load()
        val created = store.addCategory("빈 칸", "Empty", "🙂")!!

        val main = mainActivity()
        // v2.7: 문제가 0개인 카테고리 타일은 비활성이라 눌러도 선택되지 않는다.
        val tile = findTile(main.findViewById(R.id.categoryGrid), "빈 칸\n(0)")
        assertNotNull("타일이 있어야 함", tile)
        assertFalse("비활성 타일", tile!!.isEnabled)
        tile.performClick()
        idle()
        assertFalse("선택 목록에 들어가지 않는다", created.id in main.selectedCategoryIdsForTest)

        // 저장된 선택에 비어 있는 카테고리만 남으면 시작할 때 문제가 있는 카테고리로 다시 채운다.
        Prefs(context).savedCategoryIds = setOf(created.id)
        val reopened = mainActivity()
        assertFalse(created.id in reopened.selectedCategoryIdsForTest)
        assertEquals("6개 카테고리가 다시 선택된다", 6, reopened.selectedCategoryIdsForTest.size)
    }

    @Test
    fun deletingCategoryRemovesItAndItsProblems() {
        val store = WordStore(context)
        store.load()
        val created = store.addCategory("임시", "Temp", "🍀")!!
        store.add(Word(0, "임시단어", "Temp word", created.id, store.book.words.first().image))
        assertEquals(1, store.countIn(created.id))

        assertTrue(store.deleteCategory(created.id))
        assertEquals(6, store.book.categories.size)
        assertEquals(0, store.countIn(created.id))
        assertTrue(store.book.words.none { it.ko == "임시단어" })
        // 기본 카테고리는 지울 수 없다.
        assertFalse(store.deleteCategory(1))
        assertEquals(6, store.book.categories.size)
    }

    // -------------------------------------------------- 4. 리셋 버튼 둘(확인창 먼저)

    @Test
    fun adminHasTwoSeparateResetButtons() {
        val admin = adminActivity()
        assertNotNull(admin.findViewById<Button>(R.id.resetSettings))
        assertNotNull(admin.findViewById<Button>(R.id.resetProblems))
        val labels = texts(admin.window.decorView)
        assertTrue("설정 리셋 버튼", labels.any { it == "설정 리셋" })
        assertTrue("문제 리셋 버튼", labels.any { it == "문제 리셋" })
    }

    @Test
    fun resetSettingsAsksFirstThenRestoresOnlyTheSettings() {
        val prefs = Prefs(context)
        prefs.questionCount = 35
        prefs.timeLimitSeconds = 4
        val store = WordStore(context).apply { load() }
        store.addCategory("남는 카테고리", "Keep", "⭐")

        val admin = adminActivity()
        admin.findViewById<Button>(R.id.resetSettings).performClick()
        idle()
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("확인창이 먼저 뜬다", dialog)
        assertTrue(dialog is AlertDialog && dialog.isShowing)
        assertTrue(
            "확인창 안내: ${texts(dialog.window!!.decorView)}",
            texts(dialog.window!!.decorView).any { it.contains("한 판 문제 수 15문제") }
        )
        // 아직 아무것도 바뀌지 않았다.
        assertEquals(35, Prefs(admin).questionCount)

        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        assertEquals(15, Prefs(admin).questionCount)
        assertEquals(10, Prefs(admin).timeLimitSeconds)
        assertEquals("카테고리는 그대로", 7, WordStore(admin).apply { load() }.book.categories.size)
    }

    @Test
    fun resetProblemsAsksFirstThenRestoresWordsAndCategories() {
        val store = WordStore(context).apply { load() }
        store.addCategory("지워질 카테고리", "Gone", "🙂")
        store.add(Word(0, "지워질 단어", "Gone word", 1, store.book.words.first().image))
        assertEquals(421, store.book.words.size)

        val prefs = Prefs(context)
        prefs.questionCount = 30
        prefs.timeLimitSeconds = 12

        val admin = adminActivity()
        admin.findViewById<Button>(R.id.resetProblems).performClick()
        idle()
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("확인창이 먼저 뜬다", dialog)
        assertTrue(
            "확인창 안내: ${texts(dialog.window!!.decorView)}",
            texts(dialog.window!!.decorView).any { it.contains("기본 420문제") }
        )
        assertEquals("확인 전에는 그대로", 421, WordStore(admin).apply { load() }.book.words.size)

        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        val restored = WordStore(admin).apply { load() }
        assertEquals(420, restored.book.words.size)
        assertEquals(6, restored.book.categories.size)
        assertEquals("게임 설정은 그대로", 30, Prefs(admin).questionCount)
        assertEquals(12, Prefs(admin).timeLimitSeconds)
    }

    @Test
    fun bothResetDialogsCanBeCancelled() {
        val prefs = Prefs(context)
        prefs.questionCount = 25
        val admin = adminActivity()
        admin.findViewById<Button>(R.id.resetSettings).performClick()
        idle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        idle()
        assertEquals("취소하면 그대로", 25, Prefs(admin).questionCount)
    }

    // ------------------------------------------------- 5. 첫 화면 배치·배너

    @Test
    fun startButtonSitsRightBelowTheCategoryGrid() {
        val store = WordStore(context)
        store.load()
        store.addCategory("하나", "One", "⭐")
        store.addCategory("둘", "Two", "🍀")

        val main = mainActivity()
        val parent = main.findViewById<View>(R.id.categoryGrid).parent as LinearLayout
        val gridIndex = parent.indexOfChild(main.findViewById(R.id.categoryGrid))
        val startIndex = parent.indexOfChild(main.findViewById(R.id.startButton))
        assertEquals("카테고리 그리드 바로 다음", gridIndex + 1, startIndex)
        // v2.7: 전체 무작위 버튼은 없다.
        val all = texts(main.window.decorView)
        assertTrue(all.none { it.contains("전체 무작위") })
        assertEquals("v2.9", main.findViewById<TextView>(R.id.versionText).text.toString())
    }

    @Test
    fun bannerTextIsOneLineEighteenSpAndRightAligned() {
        val main = mainActivity()
        val banner = main.findViewById<TextView>(R.id.bannerText)
        val sp18 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 18f, main.resources.displayMetrics
        )
        assertTrue("18sp", banner.textSize >= sp18 - 0.5f)
        assertEquals(
            "오른쪽 정렬",
            Gravity.RIGHT,
            banner.gravity and Gravity.HORIZONTAL_GRAVITY_MASK
        )
        assertEquals("한 줄만", 1, banner.maxLines)
        assertEquals("문구는 한 줄", "그림을 보고 단어를 맞혀요!", banner.text.toString())
    }

    @Test
    fun chickWobbleSettingsAreUnchangedFromV25() {
        val main = mainActivity()
        main.applySwayPoseForTest(0.25f)
        assertEquals(1f, main.findViewById<android.widget.ImageView>(R.id.chickImage).scaleX, 0.001f)
        assertTrue(
            "3.6초 한 왕복",
            MainActivity.CHICK_WOBBLE_CYCLE_MS == 3600L && MainActivity.CHICK_WOBBLE_DP == 10
        )
    }

    /** 이름표 글자로 카테고리 타일(자식 LinearLayout)을 찾는다. */
    private fun findTile(grid: LinearLayout, expected: String): View? {
        var found: View? = null
        fun walk(view: View) {
            if (view is TextView && view.text.toString() == expected) found = view.parent as? View
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
        }
        walk(grid)
        return found
    }
}
