package com.pipi.voicewordgame

import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v2.8 확인 — 첫 화면 배너 글자를 약 15% 키운 한 가지 변경:
 * 1) 배너 문구는 18sp → 20.7sp(20.5~21sp)로 커지고 배너 폭의 약 60~62%를 차지한다.
 * 2) 여전히 한 줄이고 줄바꿈·말줄임(…) 없이 들어간다(글자 영역을 넘지 않는다).
 * 3) 좁아진 왼쪽 여백 안에서 병아리가 움직이며 글자와 겹치지 않는다.
 * 4) 하단 표기는 현재 `v2.9`, versionName 은 `2.9_1125_HHmm`, versionCode 는 1125HHMM(기존보다 큼). v2.9에서 기대값만 갱신.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V28Test {

    private fun mainActivity(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java).setup().get()

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

    private fun sp(activity: AppCompatActivity, value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, value, activity.resources.displayMetrics
    )

    // -------------------------------------------------- 1) 글자 크기 약 +15%

    @Test
    fun bannerTextIsAboutFifteenPercentLarger() {
        val main = mainActivity()
        val banner = main.findViewById<TextView>(R.id.bannerText)
        val sp18 = sp(main, 18f)
        assertEquals("문구 그대로", "그림을 보고 단어를 맞혀요!", banner.text.toString())
        assertTrue(
            "18sp 보다 14~17% 크다(현재 ${banner.textSize}px, 18sp=${sp18}px)",
            banner.textSize >= sp18 * 1.14f && banner.textSize <= sp18 * 1.17f
        )
        assertTrue(
            "20.5~21sp 범위",
            banner.textSize >= sp(main, 20.5f) - 0.5f && banner.textSize <= sp(main, 21f) + 0.5f
        )
        assertTrue("굵은 글씨 유지", banner.typeface.style and Typeface.BOLD != 0)
    }

    // -------------------------------------------------- 2) 배너 폭의 60~62%

    @Test
    fun bannerTextTakesAboutSixtyToSixtyTwoPercentOfTheBanner() {
        val main = mainActivity()
        layout(main)
        val banner = main.findViewById<TextView>(R.id.bannerText)
        val slim = main.findViewById<FrameLayout>(R.id.slimBanner)
        val textWidth = banner.paint.measureText(banner.text.toString())
        val ratio = textWidth / slim.width
        assertTrue("배너 폭의 60~62% (실제 ${"%.3f".format(ratio)})", ratio >= 0.60f && ratio <= 0.62f)
    }

    // -------------------------------------------------- 3) 한 줄·말줄임 없음

    @Test
    fun bannerLineStaysOnOneLineWithoutEllipsisInsideItsTextBox() {
        val main = mainActivity()
        layout(main)
        val banner = main.findViewById<TextView>(R.id.bannerText)
        assertEquals("maxLines = 1", 1, banner.maxLines)
        assertNull("말줄임표를 쓰지 않는다", banner.ellipsize)
        assertEquals("줄바꿈 없음", 1, banner.lineCount)
        assertEquals(
            "오른쪽 정렬",
            Gravity.RIGHT,
            banner.gravity and Gravity.HORIZONTAL_GRAVITY_MASK
        )
        val textWidth = banner.paint.measureText(banner.text.toString())
        val available = banner.width - banner.paddingLeft - banner.paddingRight
        assertTrue(
            "글자 영역($available px)에 말줄임 없이 다 들어간다(글자 ${textWidth}px)",
            textWidth <= available
        )
        // 화면에 그려진 줄의 폭도 실제 글자 폭과 같아야 잘리지 않는다.
        assertEquals("그려진 줄 폭 = 글자 폭", textWidth, banner.layout.getLineWidth(0), 1f)
    }

    // -------------------------------------------------- 4) 병아리는 글자와 겹치지 않는다

    @Test
    fun chickKeepsWalkingLeftOfTheWiderText() {
        val main = mainActivity()
        layout(main)
        val banner = main.findViewById<TextView>(R.id.bannerText)
        val slim = main.findViewById<FrameLayout>(R.id.slimBanner)
        val chick = main.findViewById<ImageView>(R.id.chickImage)

        val textLeft = slim.width - slim.paddingRight - banner.paint.measureText(banner.text.toString())
        val room = main.chickSwayRoomForTest
        assertTrue("글자 왼쪽에 움직일 여백이 남아 있다: $room", room > 0f)
        assertTrue("병아리는 글자 왼쪽 여백보다 넓게 움직이지 않는다", room <= textLeft - slim.paddingLeft)

        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { progress ->
            main.applySwayPoseForTest(progress)
            val left = chick.left + chick.translationX
            val right = left + chick.width
            assertTrue("배너 왼쪽 밖으로 나가지 않는다(p=$progress)", left >= slim.paddingLeft - 1f)
            assertTrue(
                "글자와 겹치지 않는다(p=$progress, right=$right, textLeft=$textLeft)",
                right <= textLeft - 1f
            )
        }
    }

    // -------------------------------------------------- 5) 버전 표기·코드

    @Test
    fun versionShowsV29WithTodayStamp() {
        val main = mainActivity()
        assertEquals("v2.9", main.findViewById<TextView>(R.id.versionText).text.toString())
        assertEquals("v2.9", BuildConfig.VERSION_SHORT)
        assertTrue(
            "versionName 은 2.9_1125_HHmm: ${BuildConfig.VERSION_NAME}",
            Regex("""2\.9_1125_\d{4}""").matches(BuildConfig.VERSION_NAME)
        )
        assertEquals(
            "versionCode = 1124HHMM: ${BuildConfig.VERSION_CODE}",
            BuildConfig.VERSION_NAME.substringAfter('_').replace("_", "").toInt(),
            BuildConfig.VERSION_CODE
        )
        assertTrue("기존 11240641(v2.8) 보다 커야 덮어쓰기 설치된다", BuildConfig.VERSION_CODE > 11240641)
    }
}
