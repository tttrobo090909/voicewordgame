package com.pipi.voicewordgame

import android.content.Context
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import com.pipi.voicewordgame.admin.AdminActivity
import com.pipi.voicewordgame.game.Word
import com.pipi.voicewordgame.game.WordStore
import com.pipi.voicewordgame.ui.ChickWalk
import com.pipi.voicewordgame.util.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * v2.7 확인 — 사용자가 확정한 8가지:
 * 1) 배너 한 줄 + 병아리는 글자 왼쪽 여백 안에서만 이동
 * 2) 카테고리 글자 18sp(+10%)·두 줄 가운데 정렬
 * 3) 카테고리 이름 8자 제한
 * 4) 홀수개여도 모든 타일이 2열 절반 너비
 * 5) `전체 무작위` 제거 → 카테고리 다중 선택
 * 6) 문제 제한시간 기본 10초
 * 7) 관리자: 카테고리 박스 삭제 + 필터 줄에 `+ 카테고리`·문제 수·롱클릭 수정
 * 8) 첫 화면 하단 `v2.8` 만 표시
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class V27Test {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WordStore(context).restoreDefaults()
        context.getSharedPreferences("voicewordgame", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

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

    /** 화면을 실제 크기(1233×2673px)로 재워 레이아웃을 끝낸다. */
    private fun layout(activity: AppCompatActivity) {
        val root = activity.window.decorView
        val w = 1233
        val h = 2673
        root.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, w, h)
    }

    private fun px(activity: MainActivity, dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), activity.resources.displayMetrics
    ).toInt()

    private fun row(activity: MainActivity, index: Int): LinearLayout =
        activity.findViewById<LinearLayout>(R.id.categoryGrid).getChildAt(index) as LinearLayout

    private fun tile(activity: MainActivity, row: Int, column: Int): View = row(activity, row).getChildAt(column)

    private fun tileLabel(activity: MainActivity, row: Int, column: Int): TextView =
        tile(activity, row, column).findViewById(R.id.tileLabel)

    // -------------------------------------------------- 1) 배너 한 줄 + 병아리 왼쪽 여백

    @Test
    fun bannerIsOneLineExpandedSizeRightAlignedWithoutLineBreak() {
        val main = mainActivity()
        val banner = main.findViewById<TextView>(R.id.bannerText)
        assertEquals("줄바꿈 없는 한 줄", "그림을 보고 단어를 맞혀요!", banner.text.toString())
        assertEquals("maxLines = 1", 1, banner.maxLines)
        assertEquals(
            "오른쪽 정렬",
            Gravity.RIGHT,
            banner.gravity and Gravity.HORIZONTAL_GRAVITY_MASK
        )
        val sp205 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 20.5f, main.resources.displayMetrics
        )
        val sp21 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 21f, main.resources.displayMetrics
        )
        assertTrue(
            "v2.8: 20.5~21sp(18sp 대비 약 +15%)",
            banner.textSize >= sp205 - 0.5f && banner.textSize <= sp21 + 0.5f
        )
        assertTrue("배너 문구는 굵게", banner.typeface.style and android.graphics.Typeface.BOLD != 0)
    }

    @Test
    fun chickWalksOnlyInsideTheBlankSpaceLeftOfTheText() {
        val main = mainActivity()
        layout(main)
        val banner = main.findViewById<TextView>(R.id.bannerText)
        val slim = main.findViewById<FrameLayout>(R.id.slimBanner)
        val chick = main.findViewById<ImageView>(R.id.chickImage)

        val textLeft = banner.right - banner.paint.measureText(banner.text.toString())
        val room = main.chickSwayRoomForTest
        assertTrue("글자 왼쪽에 움직일 여백이 있다: $room", room > 0f)
        assertTrue(
            "병아리는 글자 왼쪽 여백보다 넓게 움직이지 않는다",
            room <= textLeft - slim.paddingLeft
        )

        // 애니메이션 전 구간에서 병아리가 글자와 겹치지 않고 배너 밖으로 나가지 않는다.
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { progress ->
            main.applySwayPoseForTest(progress)
            val left = chick.left + chick.translationX
            val right = left + chick.width
            assertTrue("배너 왼쪽 밖으로 나가지 않는다(p=$progress)", left >= slim.paddingLeft - 1f)
            assertTrue("글자와 겹치지 않는다(p=$progress, right=$right, textLeft=$textLeft)", right <= textLeft - 1f)
        }
    }

    @Test
    fun chickSwayIsSymmetricAroundTheBlankCenter() {
        val main = mainActivity()
        layout(main)
        val chick = main.findViewById<ImageView>(R.id.chickImage)
        val room = main.chickSwayRoomForTest

        main.applySwayPoseForTest(0.25f)
        val right = chick.translationX
        main.applySwayPoseForTest(0.75f)
        val left = chick.translationX
        assertEquals("여백 가운데에서 출발", room / 2f, (right + left) / 2f, 1f)
        assertTrue("좌우로 고르게 움직인다", right > left)
        assertNotEquals("양 끝 모두 움직인다", right, left, 1f)
    }

    // -------------------------------------------------- 2) 카테고리 글자 18sp·두 줄 가운데

    @Test
    fun categoryLabelsAreTwoLinesCenteredAtEighteenSp() {
        val main = mainActivity()
        val label = tileLabel(main, 0, 0)
        assertEquals("이름 아래 문제 수를 두 줄로", "동물\n(91)", label.text.toString())
        assertEquals("두 줄까지", 2, label.maxLines)
        assertEquals("가운데 정렬", Gravity.CENTER, label.gravity)
        val sp18 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 18f, main.resources.displayMetrics
        )
        assertEquals("18sp(약 +10%)", sp18, label.textSize, 1f)
        // 나머지 타일도 같은 두 줄 규칙.
        assertEquals("음식·과일\n(91)", tileLabel(main, 0, 1).text.toString())
        assertEquals("탈것\n(40)", tileLabel(main, 1, 0).text.toString())
    }

    // -------------------------------------------------- 3) 카테고리 이름 8자 제한

    @Test
    fun adminCategoryNameIsLimitedToEightCharacters() {
        val admin = adminActivity()
        admin.findViewById<Button>(R.id.addCategory).performClick()
        idle()
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("카테고리 창", dialog)
        val labels = texts(dialog.window!!.decorView)
        assertTrue("8자 안내 문구: $labels", labels.any { it.contains("8자") })

        val ko = dialog.findViewById<EditText>(R.id.categoryKo)
        ko.setText("아홉글자카테고리이름")
        assertEquals("입력창이 8자로 자른다", 8, ko.text.length)

        dialog.findViewById<EditText>(R.id.categoryEn).setText("Nine")
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()

        val store = WordStore(admin).apply { load() }
        val created = store.book.categories.last()
        assertEquals("8자까지만 저장", 8, created.ko.length)
        assertEquals("아홉글자카테고리", created.ko)
    }

    // -------------------------------------------------- 4) 홀수개여도 2열 절반 너비

    @Test
    fun oddCategoryCountStillKeepsHalfWidthTiles() {
        val store = WordStore(context).apply { load() }
        store.addCategory("우리 가족", "My Family", "👨‍👩‍👧")
        val main = mainActivity()
        layout(main)

        val grid = main.findViewById<LinearLayout>(R.id.categoryGrid)
        assertEquals("7개 → 4줄", 4, grid.childCount)
        val lastRow = row(main, 3)
        assertEquals("마지막 줄은 빈 칸으로 채운다", 2, lastRow.childCount)
        assertFalse("빈 칸은 고를 수 없다", lastRow.getChildAt(1).isClickable)

        val firstTileWidth = tile(main, 0, 0).width
        assertTrue("타일 폭이 0보다 크다", firstTileWidth > 0)
        for (r in 0 until 4) {
            for (c in 0 until 2) {
                val child = tile(main, r, c)
                if (r == 3 && c == 1) continue
                assertTrue(
                    "모든 타일이 같은 절반 너비(행 $r, 열 $c): ${child.width} vs $firstTileWidth",
                    kotlin.math.abs(child.width - firstTileWidth) <= 2
                )
            }
        }
        // 빈 칸까지 합치면 한 줄 폭이 같다.
        assertEquals(grid.width, lastRow.width)
    }

    // -------------------------------------------------- 5) 다중 선택

    @Test
    fun allCategoriesAreSelectedAtFirstAndTapsToggleThem() {
        val main = mainActivity()
        assertEquals("초기에는 문제가 있는 모든 카테고리 선택", setOf(1, 2, 3, 4, 5, 6), main.selectedCategoryIdsForTest)

        tile(main, 0, 0).performClick()
        idle()
        assertFalse("한 번 누르면 선택 해제", 1 in main.selectedCategoryIdsForTest)
        assertEquals("나머지는 그대로", setOf(2, 3, 4, 5, 6), main.selectedCategoryIdsForTest)

        tile(main, 0, 0).performClick()
        idle()
        assertTrue("다시 누르면 선택", 1 in main.selectedCategoryIdsForTest)
        assertEquals(setOf(1, 2, 3, 4, 5, 6), main.selectedCategoryIdsForTest)

        // 여러 개를 동시에 고를 수 있고, 그 선택이 저장된다.
        tile(main, 1, 0).performClick()
        tile(main, 2, 1).performClick()
        idle()
        assertEquals(setOf(1, 2, 4, 5), main.selectedCategoryIdsForTest)
        assertEquals("앱을 다시 열어도 유지", setOf(1, 2, 4, 5), Prefs(context).savedCategoryIds)
        assertEquals(setOf(1, 2, 4, 5), mainActivity().selectedCategoryIdsForTest)
    }

    @Test
    fun gameUsesOnlyTheSelectedCategoriesMerged() {
        val main = mainActivity()
        // 탈것(40) + 자연(42) 만 남긴다.
        listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1).forEach { (r, c) -> tile(main, r, c).performClick() }
        idle()
        assertEquals(setOf(3, 5), main.selectedCategoryIdsForTest)

        Prefs(main).questionCount = Prefs.COUNT_MAX
        main.findViewById<Button>(R.id.startButton).performClick()
        idle()

        assertEquals("합친 문제 수(40+42)만큼 낼 수 있다", 40, main.plannedQuestions)
        val word = main.currentWord!!
        assertTrue("고른 카테고리에서만 출제: ${word.ko}", word.categoryId == 3 || word.categoryId == 5)
    }

    @Test
    fun startingWithNoCategoryShowsGuidanceAndStaysOnSetup() {
        val main = mainActivity()
        for (r in 0 until 3) for (c in 0 until 2) tile(main, r, c).performClick()
        idle()
        assertTrue(main.selectedCategoryIdsForTest.isEmpty())

        main.findViewById<Button>(R.id.startButton).performClick()
        idle()
        assertEquals(
            "고른 카테고리가 없다고 알려 준다",
            main.getString(R.string.need_selection),
            main.findViewById<TextView>(R.id.bannerText).text.toString()
        )
        assertEquals(View.VISIBLE, main.findViewById<LinearLayout>(R.id.setupScreen).visibility)
        assertEquals(View.GONE, main.findViewById<LinearLayout>(R.id.gameScreen).visibility)
    }

    @Test
    fun removedCategoryDropsOutOfTheSelection() {
        val store = WordStore(context).apply { load() }
        val created = store.addCategory("임시", "Temp", "🍀")!!
        store.add(Word(0, "임시단어", "Temp word", created.id, store.book.words.first().image))
        val main = mainActivity()
        assertTrue(created.id in main.selectedCategoryIdsForTest)

        store.deleteCategory(created.id)
        val reopened = mainActivity()
        assertFalse("지운 카테고리는 선택에서 빠진다", created.id in reopened.selectedCategoryIdsForTest)
        assertEquals(6, reopened.selectedCategoryIdsForTest.size)
    }

    // -------------------------------------------------- 6) 기본 제한시간 10초

    @Test
    fun timeLimitDefaultsToTenSeconds() {
        assertEquals(10, Prefs.TIME_DEFAULT)
        assertEquals(10_000L, ChickWalk.DEFAULT_LIMIT_MS)
        val prefs = Prefs(context)
        assertEquals("처음에는 10초", 10, prefs.timeLimitSeconds)

        val main = mainActivity()
        main.findViewById<Button>(R.id.startButton).performClick()
        idle()
        assertEquals("게임에 10초가 걸린다", 10_000L, main.roundLimitForTest)
    }

    @Test
    fun adminShowsTenSecondsAndResetRestoresTen() {
        val prefs = Prefs(context)
        prefs.timeLimitSeconds = 7
        val admin = adminActivity()
        assertEquals("7초", admin.findViewById<TextView>(R.id.settingTimeValue).text.toString())

        admin.findViewById<Button>(R.id.resetSettings).performClick()
        idle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(
            "확인창에 10초: ${texts(dialog.window!!.decorView)}",
            texts(dialog.window!!.decorView).any { it.contains("10초") }
        )
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        assertEquals("설정 리셋은 10초", 10, Prefs(admin).timeLimitSeconds)
        assertEquals("10초", admin.findViewById<TextView>(R.id.settingTimeValue).text.toString())
    }

    // -------------------------------------------------- 7) 관리자 필터 줄 통합

    @Test
    fun adminHasNoCategoryCardAndFilterRowStartsWithAddCategory() {
        val admin = adminActivity()
        val labels = texts(admin.window.decorView)
        assertFalse("카테고리 카드 제목이 사라졌다", labels.any { it == "카테고리" })
        assertTrue(
            "롱클릭 안내가 필터 줄 아래에 있다",
            labels.any { it == "카테고리를 길게 누르면 수정·삭제할 수 있어요" }
        )

        val row = admin.findViewById<LinearLayout>(R.id.adminFilterRow)
        assertEquals("+ 카테고리 + 전체 + 6개", 8, row.childCount)
        assertEquals("맨 왼쪽", admin.getString(R.string.admin_category_add), (row.getChildAt(0) as Button).text.toString())
        assertEquals("전체 (425)", (row.getChildAt(1) as Button).text.toString())
        assertEquals("동물 (91)", (row.getChildAt(2) as Button).text.toString())
        assertEquals("사람·몸 (42)", (row.getChildAt(7) as Button).text.toString())
    }

    @Test
    fun filterCountsFollowAddedProblemsAndDeletedCategories() {
        val admin = adminActivity()
        val row = admin.findViewById<LinearLayout>(R.id.adminFilterRow)

        // 동물(1) 카테고리로 문제 하나 추가 → 숫자가 바로 늘어난다.
        admin.findViewById<Button>(R.id.addProblem).performClick()
        idle()
        val dialog = ShadowDialog.getLatestDialog()
        dialog.findViewById<EditText>(R.id.editKo).setText("강아지풀")
        dialog.findViewById<EditText>(R.id.editEn).setText("Foxtail")
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()

        assertEquals("전체 (426)", (row.getChildAt(1) as Button).text.toString())
        assertEquals("동물 (92)", (row.getChildAt(2) as Button).text.toString())

        // 새 카테고리를 만들어 그 안에 문제를 하나 넣고, 카테고리를 지우면 숫자가 함께 줄어든다.
        admin.findViewById<Button>(R.id.addCategory).performClick()
        idle()
        val categoryDialog = ShadowDialog.getLatestDialog()
        categoryDialog.findViewById<EditText>(R.id.categoryKo).setText("우리 가족")
        categoryDialog.findViewById<EditText>(R.id.categoryEn).setText("My Family")
        (categoryDialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        val withCategory = admin.findViewById<LinearLayout>(R.id.adminFilterRow)
        assertEquals("9개 버튼", 9, withCategory.childCount)
        assertEquals("우리 가족 (0)", (withCategory.getChildAt(8) as Button).text.toString())

        // 롱클릭 → 카테고리 삭제 → 확인.
        assertTrue((withCategory.getChildAt(8) as Button).performLongClick())
        idle()
        val editor = ShadowDialog.getLatestDialog() as AlertDialog
        editor.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        idle()
        val confirm = ShadowDialog.getLatestDialog() as AlertDialog
        confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()

        val after = admin.findViewById<LinearLayout>(R.id.adminFilterRow)
        assertEquals("다시 8개 버튼", 8, after.childCount)
        assertEquals("전체 (426)", (after.getChildAt(1) as Button).text.toString())
    }

    @Test
    fun longPressingCategoryChipOpensTheEditor() {
        val admin = adminActivity()
        val row = admin.findViewById<LinearLayout>(R.id.adminFilterRow)
        val animal = row.getChildAt(2) as Button

        assertTrue("롱클릭이 먹힌다", animal.performLongClick())
        idle()
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        assertTrue(
            "수정 창이 열린다: ${texts(dialog.window!!.decorView)}",
            texts(dialog.window!!.decorView).any { it.contains("카테고리 수정") }
        )
        assertEquals("동물", dialog.findViewById<EditText>(R.id.categoryKo).text.toString())
    }

    @Test
    fun addingCategoryAppearsInFilterRowWithCount() {
        val admin = adminActivity()
        admin.findViewById<Button>(R.id.addCategory).performClick()
        idle()
        val dialog = ShadowDialog.getLatestDialog()
        dialog.findViewById<EditText>(R.id.categoryKo).setText("우리 가족")
        dialog.findViewById<EditText>(R.id.categoryEn).setText("My Family")
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()

        val row = admin.findViewById<LinearLayout>(R.id.adminFilterRow)
        assertEquals("버튼이 하나 늘어난다", 9, row.childCount)
        assertTrue(
            "새 카테고리 (0)",
            texts(row).any { it.contains("우리 가족") && it.contains("(0)") }
        )
        // 첫 화면에서도 8자 이하 이름이 두 줄로 보인다.
        val main = mainActivity()
        val grid = main.findViewById<LinearLayout>(R.id.categoryGrid)
        assertEquals("8개 → 4줄", 4, grid.childCount)
        assertTrue(texts(grid).any { it == "우리 가족\n(0)" })
    }

    // -------------------------------------------------- 8) 하단 버전 표기

    @Test
    fun footerShowsOnlyV28() {
        val main = mainActivity()
        assertEquals("v2.8", main.findViewById<TextView>(R.id.versionText).text.toString())
        assertEquals("v2.8", BuildConfig.VERSION_SHORT)
        assertTrue("빌드 이름은 2.8_날짜_시각", BuildConfig.VERSION_NAME.startsWith("2.8_"))
        assertTrue(
            "versionCode 는 기존 11232258 보다 커야 덮어쓰기 설치된다",
            BuildConfig.VERSION_CODE > 11232258
        )
    }
}
