package com.pipi.voicewordgame

import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.pipi.voicewordgame.admin.AdminActivity
import com.pipi.voicewordgame.util.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.time.Duration

/**
 * v2.4(사용자 피드백) 검증 — 게임 화면 통합 바의 병아리 크기·배치:
 * 1. 병아리를 v2.2만큼 크게(44dp) 되돌려 54dp 바 높이를 채운다.
 * 2. 안내 문구는 바 정중앙에 배경처럼 깔린다(바 높이 전체를 차지하고 가운데 정렬).
 * 3. 병아리가 문구보다 앞 레이어에서 왼쪽 → 오른쪽으로 지나간다.
 * 4. v2.3의 나머지(가로줄 없음·상태별 배경색·바 높이·위아래 여백)는 그대로다.
 *
 * v2.4 나머지 수정:
 * 5. 시간 초과에도 정답을 2번(0.72 → 0.95) 읽고 두 번째가 끝난 뒤 다음 문제로 간다.
 * 6. 상단 진행 표시 `문제 3/15 · 정답 2`, 결과창의 총 문제 수·맞힌 개수.
 * 7. 관리자 게임 설정 라벨에 허용 범위 표기.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V24Test {

    private fun launch(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun dp(activity: MainActivity, value: Int): Float =
        value * activity.resources.displayMetrics.density

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun texts(root: View): List<String> {
        val out = mutableListOf<String>()
        fun walk(view: View) {
            if (view is TextView) out.add(view.text?.toString().orEmpty())
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        return out
    }

    /** 정답 처리 후 다음 문제가 뜰 때까지(또는 결과창이 뜰 때까지) 시계를 돌린다. */
    private fun answerAndWait(activity: MainActivity) {
        activity.findViewById<Button>(R.id.answerButton).performClick()
        var waited = 0L
        while (waited < 7000) {
            val dialogShowing = ShadowDialog.getLatestDialog()?.isShowing == true
            if (activity.isAwaiting || dialogShowing) return
            idle(250)
            waited += 250
        }
    }

    private fun measure(activity: MainActivity) {
        val root = activity.window.decorView
        root.measure(
            View.MeasureSpec.makeMeasureSpec(root.width.takeIf { it > 0 } ?: 1233, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(root.height.takeIf { it > 0 } ?: 2673, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, root.width, root.height)
    }

    // -------------------------------------------------- 1) 병아리 크기

    @Test
    fun timerChickIsAsBigAsV22AndFillsTheBarHeight() {
        val activity = launch()
        val banner = activity.findViewById<FrameLayout>(R.id.timerBanner)
        val chick = activity.findViewById<ImageView>(R.id.timerChick)

        val density = activity.resources.displayMetrics.density
        val chickDp = chick.layoutParams.height / density
        val chickWidthDp = chick.layoutParams.width / density
        val barDp = banner.layoutParams.height / density

        assertTrue("병아리 높이 40~44dp(v2.2 크기): ${chickDp}dp", chickDp in 40f..44f)
        assertEquals("가로·세로 같은 정사각", chickDp, chickWidthDp, 0.01f)
        assertEquals("v2.3의 32dp 보다 큰 44dp", 44f, chickDp, 0.01f)

        assertTrue("바 높이 52~54dp: ${barDp}dp", barDp in 52f..56f)
        // v2.2는 50dp 바에 40dp 병아리(바 높이의 80%). 지금은 54dp 바에 44dp로 같은 비율을 지킨다.
        assertTrue(
            "병아리가 바 높이의 80% 이상(v2.2 비율): ${chickDp / barDp}",
            chickDp / barDp >= 0.8f
        )
    }

    @Test
    fun timerChickSitsVerticallyCenteredAtTheLeftEnd() {
        val activity = launch()
        val chick = activity.findViewById<ImageView>(R.id.timerChick)
        val params = chick.layoutParams as FrameLayout.LayoutParams

        assertEquals(
            "세로 가운데·왼쪽 시작",
            Gravity.CENTER_VERTICAL or Gravity.START,
            params.gravity
        )
        assertEquals("위쪽에 붙이지 않는다", 0, params.topMargin)
    }

    // -------------------------------------------------- 2) 문구는 바 정중앙

    @Test
    fun statusTextFillsTheBarAndSitsInItsCenter() {
        val activity = launch()
        val banner = activity.findViewById<FrameLayout>(R.id.timerBanner)
        val status = activity.findViewById<TextView>(R.id.gameStatus)
        val params = status.layoutParams as FrameLayout.LayoutParams

        assertEquals("문구는 배너 안", banner, status.parent)
        assertEquals("바 높이 전체를 차지", ViewGroup.LayoutParams.MATCH_PARENT, params.height)
        assertEquals("바 폭 전체를 차지", ViewGroup.LayoutParams.MATCH_PARENT, params.width)
        assertEquals("글자는 가운데 정렬", Gravity.CENTER, status.gravity)

        // 실제 레이아웃에서도 문구가 바의 한가운데(세로 중앙)에 온다.
        activity.findViewById<Button>(R.id.startButton).performClick()
        measure(activity)
        val top = status.top.toFloat()
        val bottom = status.bottom.toFloat()
        val barMiddle = banner.height / 2f
        val textMiddle = (top + bottom) / 2f
        assertTrue("문구가 바 높이를 채운다: ${top}~${bottom} / ${banner.height}", top <= 1f && bottom >= banner.height - 1)
        assertEquals("문구 세로 중심 = 바 중심", barMiddle, textMiddle, 1f)

        // 한 줄이고, 뷰가 바 높이 전체에서 가운데 정렬이라 글자도 바 한가운데에 놓인다.
        val line = status.layout
        assertEquals("문구 한 줄", 1, line.lineCount)
        assertTrue("글자 높이는 바보다 작다", line.height < status.height)
    }

    @Test
    fun statusTextKeepsItsWordingAndColorsPerState() {
        val activity = launch()
        val status = activity.findViewById<TextView>(R.id.gameStatus)

        assertEquals("그림을 보고 단어를 말해보세요", status.text.toString())
        activity.findViewById<Button>(R.id.startButton).performClick()
        assertEquals("게임 시작 안내", "그림을 보고 단어를 말해보세요", status.text.toString())
        assertEquals("시작은 파란 바", R.drawable.bg_banner_info, activity.bannerBackgroundForTest)

        activity.findViewById<Button>(R.id.answerButton).performClick()
        assertEquals("정답이에요! 잘했어요", status.text.toString())
        assertEquals("정답이면 초록 바", R.drawable.bg_banner_good, activity.bannerBackgroundForTest)
    }

    // -------------------------------------------------- 3) 병아리가 문구 앞 레이어

    @Test
    fun timerChickIsPaintedInFrontOfTheStatusText() {
        val activity = launch()
        val banner = activity.findViewById<FrameLayout>(R.id.timerBanner)
        val status = activity.findViewById<TextView>(R.id.gameStatus)
        val chick = activity.findViewById<ImageView>(R.id.timerChick)

        // FrameLayout 은 자식 순서대로 그린다 → 뒤에 있는 병아리가 글자 앞에 온다.
        assertEquals("문구 먼저", 0, banner.indexOfChild(status))
        assertEquals("병아리는 그 다음(앞 레이어)", 1, banner.indexOfChild(chick))
        assertTrue("병아리가 문구보다 뒤 인덱스", banner.indexOfChild(chick) > banner.indexOfChild(status))
        assertEquals("바 안에는 문구 + 병아리 둘뿐", 2, banner.childCount)
    }

    @Test
    fun timerChickWalksAcrossTheTextFromLeftToRightInFrontOfIt() {
        val activity = launch()
        activity.findViewById<Button>(R.id.startButton).performClick()
        measure(activity)
        val chick = activity.findViewById<ImageView>(R.id.timerChick)
        val status = activity.findViewById<TextView>(R.id.gameStatus)
        val banner = activity.findViewById<FrameLayout>(R.id.timerBanner)
        val travel = activity.timerTravel

        // 걷는 폭 = 바 안쪽 폭 − 병아리 폭(큰 병아리만큼 짧아진다).
        val expected = (banner.width - banner.paddingLeft - banner.paddingRight - chick.width).toFloat()
        assertEquals("걷는 거리 계산", expected, travel, 1f)

        // 타이머 틱이 자세를 덮어쓰지 않도록, 자세를 맞춘 직후(다음 틱 전)에 확인한다.
        activity.applyTimerPoseForTest(0f)
        assertEquals("시작은 바 왼쪽 끝", banner.paddingLeft.toFloat(), chick.left.toFloat(), 1f)
        assertEquals("시작 위치 이동 0", 0f, chick.translationX, 1f)

        // 바 한가운데쯤에서 병아리는 문구와 실제로 겹친다(글자 앞을 지나간다).
        activity.applyTimerPoseForTest(0.5f)
        val halfX = chick.left + chick.translationX
        assertEquals("가운데는 걷는 거리의 절반", travel / 2f, chick.translationX, 1f)
        assertTrue("가운데에서 문구와 겹친다", halfX < status.right && halfX + chick.width > status.left)
        assertTrue(
            "문구 영역 안을 지난다: $halfX~${halfX + chick.width}",
            halfX + chick.width > status.left + status.width * 0.25f
        )

        activity.applyTimerPoseForTest(1f)
        assertEquals("끝은 오른쪽 끝까지", travel, chick.translationX, 1f)
        val endRight = chick.left + chick.translationX + chick.width
        assertEquals("바 오른쪽 안쪽 끝", (banner.width - banner.paddingRight).toFloat(), endRight, 1f)
    }

    // -------------------------------------------------- 4) v2.3 기능 유지

    @Test
    fun v23TraitsStayAlongsideTheNewChick() {
        val activity = launch()
        val banner = activity.findViewById<FrameLayout>(R.id.timerBanner)
        val gameScreen = activity.findViewById<android.widget.LinearLayout>(R.id.gameScreen)
        val density = activity.resources.displayMetrics.density

        assertEquals("타이머 배너가 게임 화면 맨 위", 0, gameScreen.indexOfChild(banner))
        assertEquals(
            "가로줄 drawable 은 여전히 없다",
            false,
            java.io.File("src/main/res/drawable/bg_timer_track.xml").exists()
        )
        assertTrue("바 높이는 v2.3 그대로 54dp", (banner.layoutParams.height / density) in 52f..56f)
        assertEquals(
            "banner 모서리는 그대로 둥근 배경",
            R.drawable.bg_banner_info,
            activity.bannerBackgroundForTest
        )
    }

    // -------------------------------------------------- 5) 시간 초과도 정답 2번

    @Test
    fun timeUpReadsTheAnswerTwiceBeforeTheNextQuestion() {
        val activity = launch()
        activity.findViewById<Button>(R.id.startButton).performClick()
        val word = activity.currentWord!!
        val reveal = activity.findViewById<TextView>(R.id.answerReveal)
        val score = activity.findViewById<TextView>(R.id.scoreText)
        assertEquals("1번째 문제", 1, activity.servedQuestions)

        idle(10100)
        assertEquals("시간 초과! 정답은 ${word.ko}", reveal.text.toString())
        val spoken = activity.speechLogForTest.last()
        assertEquals("정답·건너뛰기와 같은 단어를 읽는다", word.ko, spoken.first)
        assertEquals("시간 초과도 2번 읽는다", 2, spoken.second)

        // 실제 엔진에 발음을 요청했다면 두 번 모두 0.90(v2.10: 천천히/보통 구분 없이 같은 속도).
        val rates = activity.appliedSpeechRates
        if (rates.isNotEmpty()) {
            assertEquals("0.90 으로 두 번", listOf(0.90f, 0.90f), rates)
        }

        assertEquals("시간 초과는 정답으로 치지 않는다", "문제 1/15 · 정답 0", score.text.toString())
        assertFalse("타이머·정답 대기는 끝난다", activity.isAwaiting)

        // 두 번째 발음이 끝나기 전에는 다음 문제로 넘어가지 않는다.
        idle(400)
        assertEquals("아직 시간 초과 화면", "시간 초과! 정답은 ${word.ko}", reveal.text.toString())

        // 두 번째 발음 완료 → 500ms 뒤 다음 문제.
        var waited = 400L
        while (waited < 7500 && !(activity.isAwaiting && activity.servedQuestions == 2)) {
            idle(250)
            waited += 250
        }
        assertEquals("다음 문제로 넘어간다", 2, activity.servedQuestions)
        assertEquals("무엇일까요?", reveal.text.toString())
        assertTrue("새 문제는 다시 듣는다", activity.isAwaiting)
    }

    @Test
    fun timeUpSpeaksTwiceInEnglishModeToo() {
        val activity = launch()
        activity.findViewById<TextView>(R.id.langEn).performClick()
        activity.findViewById<Button>(R.id.startButton).performClick()
        val word = activity.currentWord!!

        idle(10100)
        assertEquals(word.en, activity.speechLogForTest.last().first)
        assertEquals("영어도 2번", 2, activity.speechLogForTest.last().second)
    }

    // -------------------------------------------------- 6) 진행 표시·결과창

    @Test
    fun topProgressShowsQuestionNumberTotalAndCorrectCount() {
        val activity = launch()
        val score = activity.findViewById<TextView>(R.id.scoreText)
        activity.findViewById<Button>(R.id.startButton).performClick()

        assertEquals("문제 1/15 · 정답 0", score.text.toString())

        activity.findViewById<Button>(R.id.answerButton).performClick()
        idle(0)
        assertEquals("맞힌 뒤 같은 문제 번호에서 정답 1", "문제 1/15 · 정답 1", score.text.toString())

        answerAndWait(activity)
        assertEquals("다음 문제는 2/15", "문제 2/15 · 정답 1", score.text.toString())
    }

    @Test
    fun progressUsesTheConfiguredQuestionCountAsTheTotal() {
        val activity = launch()
        Prefs(activity).questionCount = 10
        activity.findViewById<Button>(R.id.startButton).performClick()

        assertEquals(10, activity.plannedQuestions)
        assertEquals("문제 1/10 · 정답 0", activity.findViewById<TextView>(R.id.scoreText).text.toString())
    }

    @Test
    fun summaryShowsTheTotalQuestionCountAndCorrectAnswers() {
        val activity = launch()
        Prefs(activity).questionCount = 10
        activity.findViewById<Button>(R.id.startButton).performClick()
        assertEquals(10, activity.plannedQuestions)

        repeat(10) {
            assertTrue("${it + 1}번째 문제를 기다린다", activity.isAwaiting)
            answerAndWait(activity)
        }
        idle(400)

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("10문제를 다 풀면 결과창", dialog)
        assertTrue(dialog.isShowing)
        val labels = texts(dialog.window!!.decorView)
        assertTrue("제목: $labels", labels.contains("여기까지!"))
        assertTrue("총 문제 수·맞힌 개수: $labels", labels.any { it == "총 10문제 중 10개 맞혔어요" })
        assertEquals(
            "게임 화면 진행 표시도 마지막 문제",
            "문제 10/10 · 정답 10",
            activity.findViewById<TextView>(R.id.scoreText).text.toString()
        )
    }

    // -------------------------------------------------- 7) 관리자 설정 범위 표기

    @Test
    fun adminGameSettingLabelsShowTheirAllowedRanges() {
        val admin = Robolectric.buildActivity(AdminActivity::class.java).setup().get()
        val labels = texts(admin.window.decorView)

        assertTrue(
            "한 판 문제 수 범위 표기: $labels",
            labels.any { it == "한 판 문제 수 (${Prefs.COUNT_MIN}~${Prefs.COUNT_MAX})" }
        )
        assertTrue(
            "문제 제한시간 범위 표기: $labels",
            labels.any { it == "문제 제한시간 (${Prefs.TIME_MIN}~${Prefs.TIME_MAX}초)" }
        )
        assertTrue("현재 값도 그대로", labels.any { it == "15문제" })
        assertTrue("현재 값도 그대로", labels.any { it == "10초" })
    }

    @Test
    fun adminRangeLabelsFollowThePrefsLimits() {
        // 범위 문구는 Prefs 의 상수와 어긋나면 안 된다.
        assertEquals(10, Prefs.COUNT_MIN)
        assertEquals(40, Prefs.COUNT_MAX)
        assertEquals(3, Prefs.TIME_MIN)
        assertEquals(20, Prefs.TIME_MAX)
    }
}
