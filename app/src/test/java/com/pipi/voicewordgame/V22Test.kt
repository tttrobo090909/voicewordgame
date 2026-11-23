package com.pipi.voicewordgame

import android.app.Dialog
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.Duration

/**
 * v2.2 대중 공개용 수정본 검증:
 * 아장아장 걷는 병아리(방향 반전·바운스·기울기), 배너 글씨 확대, `게임 시작!` 위치,
 * 게임 화면 통합 병아리 타이머(기본 9초 제한·시간 초과 1회 발음), 그만하기 팝업 1회,
 * 게임 중 제목 3탭 관리자(타이머 일시정지·재개), ENG 2번 정책, INTERNET 권한 제거.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class V22Test {

    private fun launch(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun px(activity: MainActivity, dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), activity.resources.displayMetrics
    ).toInt()

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

    /** 게임 화면에서 상태 안내(문구+배경)를 담는 바의 개수 — v2.3은 타이머 배너 하나뿐이다. */
    private fun countStatusBars(root: View): Int {
        var count = 0
        fun walk(view: View) {
            if (view.id == R.id.gameStatus) count++
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        return count
    }

    // -------------------------------------------------- 1) 첫 화면 병아리 아장아장

    @Test
    fun firstScreenChickStaysUprightAndSwaysGently() {
        val activity = launch()
        val chick = activity.findViewById<ImageView>(R.id.chickImage)
        val swayPx = px(activity, MainActivity.CHICK_WOBBLE_DP).toFloat()

        // 오른쪽으로 가는 구간: 정방향(scaleX = 1), 오른쪽(+10dp) 이동, 바운스·기울기는 잔잔하게.
        activity.applySwayPoseForTest(0.20f)
        assertEquals("오른쪽으로 갈 때도 scaleX = 1", 1f, chick.scaleX, 0.001f)
        assertTrue("오른쪽으로 이동", chick.translationX > swayPx * 0.9f)

        activity.applySwayPoseForTest(0.05f)
        assertTrue("걷는 동안 위아래 바운스가 들어간다", chick.translationY < -0.5f)
        assertTrue(
            "바운스는 4dp보다 잔잔하다: ${chick.translationY}",
            chick.translationY >= -px(activity, 4).toFloat()
        )
        assertNotEquals("걸음마다 기울기가 들어간다", 0f, chick.rotation, 0.01f)
        assertTrue("기울기도 과하지 않다(±3도 이하): ${chick.rotation}", kotlin.math.abs(chick.rotation) <= 3f)

        // 왼쪽으로 돌아오는 구간에도 몸은 뒤집히지 않고(명암이 좌우로 바뀌지 않음) 왼쪽(-)으로 이동한다.
        activity.applySwayPoseForTest(0.70f)
        assertEquals("왼쪽으로 갈 때도 scaleX = 1", 1f, chick.scaleX, 0.001f)
        assertTrue("왼쪽으로 이동", chick.translationX < -swayPx * 0.9f)

        // 왕복 중간(0.5)에서는 가운데.
        activity.applySwayPoseForTest(0.5f)
        assertEquals(0f, chick.translationX, 0.01f)
        assertEquals(1f, chick.scaleX, 0.001f)

        // 애니메이션 자체는 계속 살아 있고, 한 왕복은 3.6초로 느긋하다.
        assertTrue(activity.isChickWobbling)
        assertEquals(4, com.pipi.voicewordgame.ui.ChickWalk.SWAY_STEPS.toInt())
        assertEquals(3600L, MainActivity.CHICK_WOBBLE_CYCLE_MS)
    }

    @Test
    fun bannerLineIsBiggerAndStillBold() {
        val activity = launch()
        val banner = activity.findViewById<TextView>(R.id.bannerText)
        val twentySp = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 20f, activity.resources.displayMetrics
        )
        assertTrue("배너 문구가 20sp 이상", banner.textSize >= twentySp - 0.5f)
        assertTrue("배너 문구는 굵게", banner.typeface.style and android.graphics.Typeface.BOLD != 0)
        assertEquals("그림을 보고 단어를 맞혀요!", banner.text.toString())
    }

    // -------------------------------------------------- 2) 게임 시작 버튼 위치·하단 표기

    @Test
    fun startButtonSitsRightBelowRandomButtonAndFooterNotesStay() {
        val activity = launch()
        val random = activity.findViewById<Button>(R.id.randomButton)
        val start = activity.findViewById<Button>(R.id.startButton)
        val parent = random.parent as LinearLayout

        assertEquals("게임 시작은 전체 무작위 바로 아래", parent.indexOfChild(random) + 1, parent.indexOfChild(start))
        assertEquals("같은 스크롤 영역 안", parent, start.parent)
        assertTrue("버튼 사이에 적당한 간격", start.layoutParams is LinearLayout.LayoutParams)
        val params = start.layoutParams as LinearLayout.LayoutParams
        assertTrue("간격 8~20dp", params.topMargin in px(activity, 8)..px(activity, 20))

        // 하단 작은 글씨(버전·라이선스·음성 안내)는 그대로 남아 있다.
        assertEquals("게임 시작!", start.text.toString())
        val version = activity.findViewById<TextView>(R.id.versionText)
        assertTrue(version.text.toString().startsWith("v2.3_"))
        assertTrue("버전 표기는 시작 버튼 아래", parent.indexOfChild(version) > parent.indexOfChild(start))
        val notes = texts(activity.window.decorView)
        assertTrue(
            notes.any { it.contains("Noto Emoji(CC-BY 4.0)") && it.contains("음성은 녹음·저장하지 않아요") }
        )
    }

    // -------------------------------------------------- 3) 게임 화면 슬림 병아리 타이머

    @Test
    fun gameScreenHasOneIntegratedTimerAndStatusBannerRightUnderTheTopBar() {
        val activity = launch()
        val gameScreen = activity.findViewById<LinearLayout>(R.id.gameScreen)
        val banner = activity.findViewById<FrameLayout>(R.id.timerBanner)
        val chick = activity.findViewById<ImageView>(R.id.timerChick)
        val status = activity.findViewById<TextView>(R.id.gameStatus)

        assertEquals("타이머 배너가 게임 화면 맨 위", 0, gameScreen.indexOfChild(banner))
        val heightDp = banner.layoutParams.height / activity.resources.displayMetrics.density
        assertTrue("배너 높이 52~56dp: $heightDp", heightDp in 52f..56f)
        assertNotNull("배너 안에 병아리가 있다", chick.parent)
        assertEquals(banner, chick.parent)
        assertEquals("상태 안내 문구도 같은 배너 안에 있다(v2.3 통합)", banner, status.parent)
        assertEquals("상태 바는 하나만", 1, countStatusBars(activity.window.decorView))
    }

    @Test
    fun timerChickWalksFromLeftEdgeToRightEdge() {
        val activity = launch()
        activity.findViewById<Button>(R.id.startButton).performClick()
        val chick = activity.findViewById<ImageView>(R.id.timerChick)
        val travel = activity.timerTravel

        activity.applyTimerPoseForTest(0f)
        assertEquals("시작은 왼쪽 끝", 0f, chick.translationX, 0.5f)

        activity.applyTimerPoseForTest(0.5f)
        assertEquals(travel * 0.5f, chick.translationX, 1f)
        assertTrue("걷는 동안 바운스", chick.translationY <= 0f)

        activity.applyTimerPoseForTest(0.1f)
        assertNotEquals("걸음마다 기울기", 0f, chick.rotation, 0.01f)

        activity.applyTimerPoseForTest(1f)
        assertEquals("끝은 오른쪽 끝", travel, chick.translationX, 1f)
        assertTrue(
            "9초 제한 안에 걷는 시간은 8.7초",
            com.pipi.voicewordgame.ui.ChickWalk.walkMs(9000L).let { it > 0 && it < 9000L }
        )
    }

    @Test
    fun roundStartsTheTimerAndNineSecondsReadsAnswerOnceThenMovesOn() {
        val activity = launch()
        activity.findViewById<Button>(R.id.startButton).performClick()
        val reveal = activity.findViewById<TextView>(R.id.answerReveal)
        val score = activity.findViewById<TextView>(R.id.scoreText)

        assertEquals("기본 제한시간은 9초", 9000L, activity.roundLimitForTest)
        assertTrue("문제가 시작되면 제한시간 타이머가 돈다", activity.isRoundTimerRunning)
        assertTrue(activity.isTimerWalking)
        assertTrue(activity.isAwaiting)
        val word = activity.currentWord!!
        assertEquals("무엇일까요?", reveal.text.toString())

        idle(8900)
        assertTrue("9초 전에는 시간 초과가 아니다", activity.isRoundTimerRunning)
        assertEquals("무엇일까요?", reveal.text.toString())

        idle(200)
        assertFalse("9초가 지나면 타이머 정지", activity.isRoundTimerRunning)
        assertFalse("9초가 지나면 정답 대기 종료", activity.isAwaiting)
        assertFalse("타이머 병아리도 멈춘다", activity.isTimerWalking)
        assertEquals("시간 초과! 정답은 ${word.ko}", reveal.text.toString())
        assertEquals("시간 초과는 정답을 1번만 읽는다", 1, activity.speechLogForTest.last().second)
        assertEquals("시간 초과는 점수에 들어가지 않는다", "맞힌 문제 0개", score.text.toString())

        // 한 번 읽고 나면 다음 문제로 넘어간다.
        idle(600)
        assertEquals("무엇일까요?", reveal.text.toString())
        assertTrue("다음 문제는 타이머를 다시 건다", activity.isRoundTimerRunning)
        assertTrue(activity.isAwaiting)
    }

    @Test
    fun alsoWhenTimeUpTheMicStatusIsCleared() {
        val activity = launch()
        activity.findViewById<Button>(R.id.startButton).performClick()
        activity.findViewById<TextView>(R.id.micStatus).text = "듣고 있어요"

        idle(9000)
        assertEquals("시간 초과 시 마이크 안내도 지운다", "", activity.findViewById<TextView>(R.id.micStatus).text.toString())
    }

    @Test
    fun correctAnswerWithinEightSecondsStopsTheTimer() {
        val activity = launch()
        activity.findViewById<Button>(R.id.startButton).performClick()
        val word = activity.currentWord!!

        activity.findViewById<Button>(R.id.answerButton).performClick()
        assertFalse("정답을 맞히면 타이머 즉시 정지", activity.isRoundTimerRunning)
        assertFalse(activity.isTimerWalking)
        assertTrue("정답·건너뛰기는 기존처럼 2번 읽는다", activity.speechLogForTest.all { it.second == 2 })
        assertEquals(word.ko, activity.speechLogForTest.first().first)

        // 원래 8초 타이머가 살아 있었다면 이 사이에 시간 초과 발음이 남았을 것이다.
        idle(8000)
        assertTrue("시간 초과는 일어나지 않는다", activity.speechLogForTest.none { it.second == 1 })
        assertEquals("맞힌 문제 1개", activity.findViewById<TextView>(R.id.scoreText).text.toString())
    }

    @Test
    fun skipAlsoStopsTheTimerAndReadsTwice() {
        val activity = launch()
        activity.findViewById<Button>(R.id.startButton).performClick()
        activity.findViewById<Button>(R.id.skipButton).performClick()

        assertFalse(activity.isRoundTimerRunning)
        assertEquals(2, activity.speechLogForTest.last().second)
    }

    // -------------------------------------------------- 4) 그만하기 팝업 1회

    @Test
    fun stopButtonEndsGameAndShowsOnlyTheSummaryDialog() {
        val activity = launch()
        activity.findViewById<Button>(R.id.startButton).performClick()
        activity.findViewById<Button>(R.id.answerButton).performClick()
        idle(600)

        activity.findViewById<Button>(R.id.stopButton).performClick()
        idle(0)

        val dialogs = ShadowDialog.getShownDialogs()
        assertEquals("결과창 하나만 뜬다", 1, dialogs.size)
        val dialog = dialogs.first() as Dialog
        assertTrue(dialog.isShowing)
        val labels = texts(dialog.window!!.decorView)
        assertTrue("결과창 제목: $labels", labels.contains("여기까지!"))
        assertTrue("맞힌 문제 수: $labels", labels.any { it.startsWith("맞힌 문제 1개") })
        assertTrue("닫기 버튼", labels.contains("닫기"))
        assertTrue("한 번 더 버튼", labels.contains("한 번 더"))
        assertFalse("확인창(그만할까요?)은 없다", labels.any { it.contains("그만할까요") })

        assertEquals("게임 화면은 닫힌다", View.GONE, activity.findViewById<LinearLayout>(R.id.gameScreen).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<LinearLayout>(R.id.setupScreen).visibility)
        assertFalse(activity.isRoundTimerRunning)
    }

    // -------------------------------------------------- 5) 게임 중 관리자 진입

    @Test
    fun tripleTapOnTitleDuringGameOpensGuguAndPausesThenResumesTimer() {
        val activity = launch()
        activity.findViewById<Button>(R.id.startButton).performClick()
        val reveal = activity.findViewById<TextView>(R.id.answerReveal)
        val title = activity.findViewById<TextView>(R.id.titleText)

        idle(1500)
        repeat(3) { title.performClick() }
        idle(0)

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("게임 중에도 구구단 잠금이 뜬다", dialog)
        assertTrue(dialog.isShowing)
        assertNotNull(dialog.findViewById<TextView>(R.id.guguQuestion))
        assertFalse("잠금창이 떠 있는 동안 타이머 정지", activity.isRoundTimerRunning)
        assertTrue(
            "남은 시간을 기억한다: ${activity.pausedRoundRemainingMs}",
            activity.pausedRoundRemainingMs in 7000L..7800L
        )

        // 잠금창이 떠 있는 동안에는 8초가 지나도 시간 초과가 나지 않는다.
        idle(10000)
        assertTrue("일시정지 중 시간 초과 없음", activity.speechLogForTest.isEmpty())
        assertEquals("무엇일까요?", reveal.text.toString())

        // 취소하면 남은 시간부터 다시 걷는다.
        val remaining = activity.pausedRoundRemainingMs
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        idle(0)
        assertTrue("돌아오면 타이머 재개", activity.isRoundTimerRunning)

        idle(remaining - 400)
        assertTrue("남은 시간 전에는 시간 초과 없음", activity.isRoundTimerRunning)
        idle(600)
        assertFalse("남은 시간이 지나면 시간 초과", activity.isRoundTimerRunning)
        assertEquals(1, activity.speechLogForTest.last().second)
    }

    @Test
    fun correctGuguDuringGameOpensAdminAndComesBackCleanly() {
        val activity = launch()
        activity.findViewById<Button>(R.id.startButton).performClick()
        val title = activity.findViewById<TextView>(R.id.titleText)

        repeat(3) { title.performClick() }
        idle(0)
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val question = dialog.findViewById<TextView>(R.id.guguQuestion)!!.text.toString()
        val match = Regex("(\\d+)\\s*×\\s*(\\d+)").find(question)!!
        val answer = match.groupValues[1].toInt() * match.groupValues[2].toInt()
        dialog.findViewById<EditText>(R.id.guguInput)!!.setText(answer.toString())
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle(0)

        val intent = shadowOf(activity).nextStartedActivity
        assertNotNull("관리자 화면으로 이동", intent)
        assertEquals("com.pipi.voicewordgame.admin.AdminActivity", intent.component?.className)
        assertFalse("관리자로 가는 동안 타이머 정지", activity.isRoundTimerRunning)
    }

    // -------------------------------------------------- 6) ENG 2번 정책

    @Test
    fun engModeOnlySwitchesWordsNotUiText() {
        val activity = launch()
        activity.findViewById<TextView>(R.id.langEn).performClick()

        // 첫 화면: 카테고리 타일만 영어, 나머지 문구는 한국어.
        val grid = activity.findViewById<LinearLayout>(R.id.categoryGrid)
        val firstLabel = (grid.getChildAt(0) as LinearLayout)
            .getChildAt(0).findViewById<TextView>(R.id.tileLabel).text.toString()
        assertTrue("카테고리 타일은 영어: $firstLabel", firstLabel.startsWith("Animals"))
        assertEquals("전체 무작위 (250)", activity.findViewById<Button>(R.id.randomButton).text.toString())
        assertEquals("게임 시작!", activity.findViewById<Button>(R.id.startButton).text.toString())
        assertEquals("그림을 보고 단어를 맞혀요!", activity.findViewById<TextView>(R.id.bannerText).text.toString())

        // 게임 화면: 버튼·안내 문구는 한국어, 정답 단어는 영어.
        activity.findViewById<Button>(R.id.startButton).performClick()
        val reveal = activity.findViewById<TextView>(R.id.answerReveal)
        val word = activity.currentWord!!
        assertEquals("무엇일까요?", reveal.text.toString())
        assertEquals("힌트", activity.findViewById<Button>(R.id.hintButton).text.toString())
        assertEquals("건너뛰기", activity.findViewById<Button>(R.id.skipButton).text.toString())
        assertEquals("정답 처리", activity.findViewById<Button>(R.id.answerButton).text.toString())
        assertEquals("그만하기", activity.findViewById<Button>(R.id.stopButton).text.toString())
        assertEquals("맞힌 문제 0개", activity.findViewById<TextView>(R.id.scoreText).text.toString())

        activity.findViewById<Button>(R.id.answerButton).performClick()
        assertEquals("정답! ${word.en}", reveal.text.toString())
        assertEquals(
            "정답 배너도 한국어",
            "정답이에요! 잘했어요",
            activity.findViewById<TextView>(R.id.gameStatus).text.toString()
        )
    }

    @Test
    fun engModeTimeUpLineKeepsKoreanPrefix() {
        val activity = launch()
        activity.findViewById<TextView>(R.id.langEn).performClick()
        activity.findViewById<Button>(R.id.startButton).performClick()
        val word = activity.currentWord!!

        idle(9100)
        assertEquals(
            "시간 초과! 정답은 ${word.en}",
            activity.findViewById<TextView>(R.id.answerReveal).text.toString()
        )
    }

    @Test
    fun switchingLanguageDuringGameKeepsKoreanNotice() {
        val activity = launch()
        activity.findViewById<Button>(R.id.startButton).performClick()
        activity.findViewById<TextView>(R.id.langEn).performClick()

        val status = activity.findViewById<TextView>(R.id.gameStatus).text.toString()
        assertEquals("이제 영어로 말해요", status)
    }

    // -------------------------------------------------- 7) 권한·버전

    @Test
    fun manifestKeepsOnlyRecordAudioPermission() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue("마이크 권한 유지", manifest.contains("android.permission.RECORD_AUDIO"))
        assertFalse("쓰지 않는 INTERNET 권한 제거", manifest.contains("android.permission.INTERNET"))
    }

    @Test
    fun versionIsV23() {
        val activity = launch()
        val version = activity.findViewById<TextView>(R.id.versionText).text.toString()
        assertTrue("버전 표기 v2.3_...: $version", version.startsWith("v2.3_"))
        assertTrue(version.length >= "v2.3_1123_0000".length)
    }
}
