package com.pipi.voicewordgame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v2.1 디자인 요구사항: 상·하단 여유, 제목 탭 무반응, 병아리 배너 확대·살랑살랑,
 * 카테고리 2열×3행 + 전체 무작위 맨 아래, 3dp 코랄 선택 테두리.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V21DesignTest {

    private fun launch(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun px(activity: MainActivity, dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), activity.resources.displayMetrics
    ).toInt()

    private fun rows(activity: MainActivity): LinearLayout =
        activity.findViewById<LinearLayout>(R.id.categoryGrid)

    private fun tile(activity: MainActivity, row: Int, column: Int): View =
        (rows(activity).getChildAt(row) as LinearLayout).getChildAt(column)

    private fun render(drawable: Drawable, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    /** 리플 안쪽의 실제 모양(색·테두리)만 꺼낸다. */
    private fun shapeOf(drawable: Drawable): Drawable =
        if (drawable is LayerDrawable && drawable.numberOfLayers > 0) drawable.getDrawable(0) else drawable

    // ------------------------------------------------------------ 5) 카테고리 배치

    @Test
    fun sixCategoriesComeFirstInTwoColumnsAndRandomIsLast() {
        val activity = launch()
        val grid = rows(activity)
        assertEquals("2열×3행", 3, grid.childCount)
        for (row in 0 until grid.childCount) {
            assertEquals(2, (grid.getChildAt(row) as LinearLayout).childCount)
        }
        assertEquals(
            listOf("동물 (51)", "음식·과일 (50)", "탈것 (25)", "사물·집 (69)", "자연 (24)", "사람·몸 (31)"),
            (0 until 3).flatMap { row ->
                (0 until 2).map { column ->
                    tile(activity, row, column).findViewById<TextView>(R.id.tileLabel).text.toString()
                }
            }
        )

        // 전체 무작위는 카테고리 6개 바로 아래(같은 부모 안에서 그리드 다음).
        val random = activity.findViewById<Button>(R.id.randomButton)
        val parent = random.parent as LinearLayout
        assertEquals(
            "무작위 버튼은 카테고리 그리드 바로 뒤",
            parent.indexOfChild(grid) + 1,
            parent.indexOfChild(random)
        )
    }

    @Test
    fun categoryTilesAreFifteenPercentTaller() {
        val activity = launch()
        repeat(3) { row ->
            repeat(2) { column ->
                val height = tile(activity, row, column).layoutParams.height
                assertEquals("카테고리 타일 높이 78dp(68dp 대비 +15%)", px(activity, 78), height)
                assertTrue(height >= px(activity, 68) * 1.1f)
            }
        }
    }

    // ------------------------------------------------------------ 6) 선택 테두리

    @Test
    fun selectedCategoryGetsThickCoralBorderAndDeeperFill() {
        val activity = launch()
        val coral = Color.parseColor("#E05A3F")
        val tint = ContextCompat.getColor(activity, R.color.tint_animal)

        val first = tile(activity, 0, 0)
        first.performClick()
        val selected = render(first.background, 240, 120)
        assertEquals("선택된 타일 테두리는 진한 코랄", coral, selected.getPixel(4, 60))
        assertNotEquals("선택되면 배경이 살짝 진해진다", tint, selected.getPixel(120, 60))
        assertEquals(ColorUtils.blendARGB(tint, coral, 0.12f), selected.getPixel(120, 60))

        // 다른 카테고리를 고르면 이전 타일의 테두리는 사라진다.
        tile(activity, 0, 1).performClick()
        val unselected = render(first.background, 240, 120)
        assertEquals("선택이 풀리면 배경은 원래 톤", tint, unselected.getPixel(120, 60))
        assertEquals("선택이 풀리면 테두리 없음", tint, unselected.getPixel(4, 60))

        // 새로 고른 타일에는 테두리가 생긴다.
        val second = render(tile(activity, 0, 1).background, 240, 120)
        assertEquals(coral, second.getPixel(4, 60))
    }

    @Test
    fun randomButtonSelectedStateIsClearlyDifferentFromUnselected() {
        val activity = launch()
        val random = activity.findViewById<Button>(R.id.randomButton)

        val selected = render(shapeOf(random.background), 240, 120)
        assertEquals("선택된 무작위 테두리", Color.parseColor("#E05A3F"), selected.getPixel(4, 60))
        assertEquals("선택된 무작위 배경", Color.parseColor("#FFD98A"), selected.getPixel(120, 60))

        tile(activity, 1, 0).performClick()
        val unselected = render(shapeOf(random.background), 240, 120)
        assertEquals("선택 해제 시 테두리", Color.parseColor("#E9C97B"), unselected.getPixel(4, 60))
        assertEquals("선택 해제 시 배경", Color.parseColor("#FFE8A3"), unselected.getPixel(120, 60))
        assertNotEquals(selected.getPixel(4, 60), unselected.getPixel(4, 60))
        assertNotEquals(selected.getPixel(120, 60), unselected.getPixel(120, 60))
    }

    // ------------------------------------------------------------ 2) 제목 탭 무반응

    @Test
    fun titleTapHasNoRippleFocusOrPressEffect() {
        val activity = launch()
        val title = activity.findViewById<TextView>(R.id.titleText)

        assertNull("리플·눌림 배경 없음", title.background)
        assertNull("포그라운드 하이라이트 없음", title.foreground)
        assertFalse("탭 소리 없음", title.isSoundEffectsEnabled)
        assertFalse("진동 없음", title.isHapticFeedbackEnabled)
        assertTrue("탭은 계속 받는다(관리자 입구)", title.isClickable)

        repeat(3) { title.performClick() }
        assertNull("탭 후에도 배경이 생기지 않는다", title.background)
    }

    // ------------------------------------------------------------ 4) 병아리 배너

    @Test
    fun bannerAndChickAreTwentyPercentBigger() {
        val activity = launch()
        val banner = activity.findViewById<LinearLayout>(R.id.slimBanner)
        val chick = activity.findViewById<ImageView>(R.id.chickImage)

        assertEquals("배너 높이 96dp → 116dp", px(activity, 116), banner.layoutParams.height)
        assertEquals("병아리 76dp → 92dp", px(activity, 92), chick.layoutParams.width)
        assertEquals(px(activity, 92), chick.layoutParams.height)
    }

    @Test
    fun chickWobblesLeftAndRightOnFirstScreen() {
        val activity = launch()
        val chick = activity.findViewById<ImageView>(R.id.chickImage)

        assertTrue("첫 화면에서 병아리가 흔들린다", activity.isChickWobbling)
        // 흔들림 폭과 한 왕복 시간은 코드 상수로 고정한다(±10dp, 1.4초).
        assertEquals(10, MainActivity.CHICK_WOBBLE_DP)
        assertEquals(1400L, MainActivity.CHICK_WOBBLE_CYCLE_MS)
        assertEquals("가운데에서 시작", 0f, chick.translationX, 0.01f)
    }

    @Test
    fun wobbleStopsWhenLeavingTheFirstScreenAndResumesOnResume() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertTrue(activity.isChickWobbling)

        controller.pause()
        assertFalse("화면을 떠나면 애니메이션을 정리한다", activity.isChickWobbling)
        assertEquals(0f, activity.findViewById<ImageView>(R.id.chickImage).translationX, 0.01f)

        controller.resume()
        assertTrue("돌아오면 다시 흔들린다", activity.isChickWobbling)

        activity.findViewById<Button>(R.id.startButton).performClick()
        assertFalse("게임 화면에서는 흔들리지 않는다", activity.isChickWobbling)
    }
}
