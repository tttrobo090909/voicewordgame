package com.pipi.voicewordgame

import android.content.Intent
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
import com.pipi.voicewordgame.admin.AdminActivity
import com.pipi.voicewordgame.game.GameSession
import com.pipi.voicewordgame.game.Word
import com.pipi.voicewordgame.ui.ChickWalk
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
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.Duration

/**
 * v2.3(사용자 최종 확정) 검증:
 * 1. 관리자 설정 `한 판 문제 수`(10~40, 기본 15)·`문제 제한시간`(3~20초, 기본 10초)와 게임 연동·자동 결과창
 * 2. 첫 화면 병아리 좌우 반전 제거·느린 왕복
 * 3. 게임 화면 가로줄 제거 + 통합 타이머·상태 바 + 위아래 여백 확대
 * 4. 첫 화면 하단 안내 문구 변경
 * 5. 개인정보처리방침(docs/privacy.html + 구구단 게이트 링크·폴백)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class V23Test {

    private fun launch(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun admin(): AdminActivity =
        Robolectric.buildActivity(AdminActivity::class.java).setup().get()

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

    private fun solveGugu(dialog: AlertDialog) {
        val question = dialog.findViewById<TextView>(R.id.guguQuestion)!!.text.toString()
        val match = Regex("(\\d+)\\s*×\\s*(\\d+)").find(question)!!
        val answer = match.groupValues[1].toInt() * match.groupValues[2].toInt()
        dialog.findViewById<EditText>(R.id.guguInput)!!.setText(answer.toString())
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle(0)
    }

    // -------------------------------------------------- 1) 설정 값(문제 수·제한시간)

    @Test
    fun settingsDefaultTo15QuestionsAnd10Seconds() {
        val activity = launch()
        val prefs = Prefs(activity)
        assertEquals("기본 한 판 문제 수 15", 15, prefs.questionCount)
        assertEquals("기본 제한시간 10초(v2.7)", 10, prefs.timeLimitSeconds)
        assertEquals(10, Prefs.COUNT_MIN)
        assertEquals(40, Prefs.COUNT_MAX)
        assertEquals(3, Prefs.TIME_MIN)
        assertEquals(20, Prefs.TIME_MAX)
    }

    @Test
    fun settingsAreClampedToTheirRanges() {
        val activity = launch()
        val prefs = Prefs(activity)

        prefs.questionCount = 3
        assertEquals("10문제 아래로는 못 내린다", 10, prefs.questionCount)
        prefs.questionCount = 99
        assertEquals("40문제 위로는 못 올린다", Prefs.COUNT_MAX, prefs.questionCount)

        prefs.timeLimitSeconds = 1
        assertEquals("3초 아래로는 못 내린다", 3, prefs.timeLimitSeconds)
        prefs.timeLimitSeconds = 42
        assertEquals("20초 위로는 못 올린다", 20, prefs.timeLimitSeconds)

        prefs.questionCount = 30
        prefs.timeLimitSeconds = 12
        assertEquals(30, prefs.questionCount)
        assertEquals(12, prefs.timeLimitSeconds)
    }

    @Test
    fun gameUsesTheConfiguredQuestionCountAndTimeLimit() {
        val activity = launch()
        val prefs = Prefs(activity)
        prefs.questionCount = 20
        prefs.timeLimitSeconds = 6

        activity.findViewById<Button>(R.id.startButton).performClick()
        assertEquals("설정한 20문제", 20, activity.plannedQuestions)
        assertEquals("설정한 6초 제한", 6000L, activity.roundLimitForTest)

        idle(5900)
        assertTrue("6초 전에는 계속 진행", activity.isRoundTimerRunning)
        idle(200)
        assertFalse("6초가 지나면 시간 초과", activity.isRoundTimerRunning)
        assertEquals("v2.4: 시간 초과도 2번 발음", 2, activity.speechLogForTest.last().second)
    }

    @Test
    fun poolSmallerThanTheSettingServesOnlyWhatIsThere() {
        val words = (1..3).map {
            Word(id = it, ko = "낱말$it", en = "word$it", categoryId = 1, image = "words/001.webp")
        }
        val session = GameSession()
        session.setPool(words, 15)
        assertEquals("남은 단어가 적으면 그 만큼만", 3, session.plannedCount())
        repeat(3) { assertNotNull("3문제는 나온다", session.next()) }
        assertEquals("같은 문제를 다시 내지 않고 끝난다", null, session.next())
        assertEquals(3, session.servedCount)
    }

    @Test
    fun fifteenQuestionsFinishTheGameAndShowTheSummary() {
        val activity = launch()
        activity.findViewById<Button>(R.id.startButton).performClick()
        assertEquals(15, activity.plannedQuestions)

        repeat(15) {
            assertTrue("${it + 1}번째 문제를 기다린다", activity.isAwaiting)
            answerAndWait(activity)
        }
        idle(400)

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("15문제를 다 풀면 결과창이 뜬다", dialog)
        assertTrue(dialog.isShowing)
        val labels = texts(dialog.window!!.decorView)
        assertTrue("결과창 제목: $labels", labels.contains("여기까지!"))
        assertTrue("결과창의 총 문제 수·맞힌 개수: $labels", labels.any { it == "총 15문제 중 15개 맞혔어요" })
        assertTrue(labels.contains("닫기"))
        assertTrue(labels.contains("한 번 더"))
        assertEquals(
            "게임 화면은 닫힌다",
            View.GONE,
            activity.findViewById<LinearLayout>(R.id.gameScreen).visibility
        )
        assertEquals(
            View.VISIBLE,
            activity.findViewById<LinearLayout>(R.id.setupScreen).visibility
        )
        assertEquals("진행 표시도 15/15·정답 15", "문제 15/15 · 정답 15", activity.findViewById<TextView>(R.id.scoreText).text.toString())
    }

    // -------------------------------------------------- 2) 첫 화면 병아리

    @Test
    fun firstScreenChickNeverMirrorsAndWalksSlowly() {
        val activity = launch()
        val chick = activity.findViewById<ImageView>(R.id.chickImage)

        listOf(0f, 0.1f, 0.25f, 0.5f, 0.66f, 0.75f, 0.9f, 1f).forEach { progress ->
            activity.applySwayPoseForTest(progress)
            assertEquals("진행도 $progress 에서도 정방향", 1f, chick.scaleX, 0.0001f)
        }

        // 바운스는 3dp를 넘지 않고, 기울기도 ±2.5도로 잔잔하다.
        var deepest = 0f
        var steepest = 0f
        listOf(0f, 0.02f, 0.05f, 0.12f, 0.2f, 0.3f, 0.4f, 0.5f, 0.62f, 0.8f, 1f).forEach { progress ->
            activity.applySwayPoseForTest(progress)
            deepest = minOf(deepest, chick.translationY)
            steepest = maxOf(steepest, kotlin.math.abs(chick.rotation))
            assertEquals("진행도 $progress 에서도 정방향", 1f, chick.scaleX, 0.0001f)
        }
        assertTrue("바운스는 3dp 를 넘지 않는다: $deepest", deepest >= -px(activity, 3).toFloat() - 0.5f)
        assertTrue("바운스는 눈에 보일 만큼은 있다: $deepest", deepest < -0.2f)
        assertTrue("기울기는 ±2.5도 이하: $steepest", steepest <= 2.55f)
        assertEquals("3.6초에 한 왕복", 3600L, MainActivity.CHICK_WOBBLE_CYCLE_MS)
        assertEquals("바운스 상수도 3dp", 3, MainActivity.CHICK_BOUNCE_DP)
    }

    // -------------------------------------------------- 3) 게임 화면 통합 타이머·여백

    @Test
    fun timerTrackLineIsGoneAndStatusBannerIsMergedIntoTimerBar() {
        val activity = launch()
        val banner = activity.findViewById<FrameLayout>(R.id.timerBanner)
        val status = activity.findViewById<TextView>(R.id.gameStatus)
        val chick = activity.findViewById<ImageView>(R.id.timerChick)

        assertEquals("가로줄 drawable 은 더 이상 쓰지 않는다", false, File("src/main/res/drawable/bg_timer_track.xml").exists())
        assertEquals("배너 안에는 안내 문구 + 병아리 둘뿐", 2, banner.childCount)
        assertEquals("안내 문구가 배너 안", banner, status.parent)
        assertEquals("병아리도 배너 안", banner, chick.parent)

        val heightDp = banner.layoutParams.height / activity.resources.displayMetrics.density
        assertTrue("배너 높이 52~56dp: $heightDp", heightDp in 52f..56f)

        // 상태 문구는 병아리와 같은 바에 뜨고, 상태에 따라 바 배경색이 바뀐다.
        assertEquals("그림을 보고 단어를 말해보세요", status.text.toString())
        activity.findViewById<Button>(R.id.startButton).performClick()
        assertEquals("게임 시작 안내", "그림을 보고 단어를 말해보세요", status.text.toString())
        assertEquals(R.drawable.bg_banner_info, activity.bannerBackgroundForTest)

        activity.findViewById<Button>(R.id.answerButton).performClick()
        assertEquals("정답이에요! 잘했어요", status.text.toString())
        assertEquals("정답이면 초록 바", R.drawable.bg_banner_good, activity.bannerBackgroundForTest)
    }

    @Test
    fun timeUpAlsoUsesTheSameIntegratedBarWithBadColor() {
        val activity = launch()
        activity.findViewById<Button>(R.id.startButton).performClick()
        idle(10100)

        assertEquals(
            "시간 초과예요",
            activity.findViewById<TextView>(R.id.gameStatus).text.toString()
        )
        assertEquals(R.drawable.bg_banner_bad, activity.bannerBackgroundForTest)
    }

    @Test
    fun gameScreenHasMoreTopAndBottomBreathingRoomWhileTheCardStays() {
        val activity = launch()
        val gameScreen = activity.findViewById<LinearLayout>(R.id.gameScreen)
        val banner = activity.findViewById<FrameLayout>(R.id.timerBanner)
        val cardRow = gameScreen.getChildAt(1) as FrameLayout
        val micStatus = activity.findViewById<TextView>(R.id.micStatus)
        val density = activity.resources.displayMetrics.density

        val topGap = gameScreen.paddingTop +
            (banner.layoutParams as LinearLayout.LayoutParams).topMargin +
            (cardRow.layoutParams as LinearLayout.LayoutParams).topMargin
        val topGapDp = topGap / density
        assertTrue("위쪽 여백이 넉넉하다: ${topGapDp}dp", topGapDp >= 28f)

        val bottomGap = (micStatus.layoutParams as LinearLayout.LayoutParams).bottomMargin +
            (micStatus.layoutParams as LinearLayout.LayoutParams).topMargin +
            gameScreen.paddingBottom
        val bottomGapDp = bottomGap / density
        assertTrue("아래쪽 여백이 넉넉하다: ${bottomGapDp}dp", bottomGapDp >= 24f)

        // 그림 카드는 여전히 남는 자리를 채우고(크기 키우지 않음), 양옆 여백도 그대로.
        val params = cardRow.layoutParams as LinearLayout.LayoutParams
        assertEquals("카드 높이는 weight 로 채운다", 0, params.height)
        assertEquals(1f, params.weight, 0.0001f)
        assertEquals(px(activity, 14), params.marginStart)
        assertEquals(px(activity, 14), params.marginEnd)
    }

    // -------------------------------------------------- 4) 하단 안내 문구·개인정보처리방침

    @Test
    fun footerNoteUsesTheNewVoiceWording() {
        val activity = launch()
        val notes = texts(activity.window.decorView)
        assertTrue(
            "새 문구: $notes",
            notes.any { it.contains("음성은 녹음·저장하지 않아요 (기기 음성인식만 사용)") }
        )
        assertFalse("옛 문구는 사라졌다", notes.any { it.contains("소리는 기기 밖으로 보내지 않아요") })
        assertTrue("라이선스 안내는 남는다", notes.any { it.contains("Noto Emoji(CC-BY 4.0)") })
    }

    @Test
    fun privacyLinkSitsAtTheVeryBottomAndIsSmall() {
        val activity = launch()
        val link = activity.findViewById<TextView>(R.id.privacyLink)
        val version = activity.findViewById<TextView>(R.id.versionText)
        val setupContent = link.parent as LinearLayout

        assertEquals("개인정보처리방침", link.text.toString())
        assertTrue("작은 글씨(12sp 이하)", link.textSize <= TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 12f, activity.resources.displayMetrics
        ))
        assertTrue(
            "맨 아래(버전 표기보다 아래)",
            setupContent.indexOfChild(link) > setupContent.indexOfChild(version)
        )
        assertTrue(link.isClickable)
    }

    @Test
    fun privacyLinkNeedsTheGuardianGuguLockBeforeOpeningTheWebPage() {
        val activity = launch()
        val link = activity.findViewById<TextView>(R.id.privacyLink)

        link.performClick()
        idle(0)
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("링크를 누르면 부모 확인 잠금", dialog)
        assertTrue(dialog.isShowing)
        assertNotNull(dialog.findViewById<TextView>(R.id.guguQuestion))
        assertEquals("잠금 전에는 아무것도 열지 않는다", null, shadowOf(activity).nextStartedActivity)

        // 틀리면 열리지 않는다.
        dialog.findViewById<EditText>(R.id.guguInput)!!.setText("0")
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle(0)
        assertEquals("틀린 답으로는 열리지 않는다", null, shadowOf(activity).nextStartedActivity)

        // 맞히면 개인정보처리방침 웹 문서를 브라우저로 연다.
        link.performClick()
        idle(0)
        val again = ShadowDialog.getLatestDialog() as AlertDialog
        solveGugu(again)
        val intent = shadowOf(activity).nextStartedActivity
        assertNotNull("구구단을 통과하면 웹 문서를 연다", intent)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(
            "https://tttrobo090909.github.io/voicewordgame/privacy.html",
            intent.data.toString()
        )
    }

    @Test
    fun privacyFallbackDialogCarriesTheSummaryAndContact() {
        val activity = launch()
        activity.showPrivacyFallback()
        idle(0)

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        val labels = texts(dialog.window!!.decorView)
        assertTrue("제목: $labels", labels.contains("개인정보처리방침"))
        assertTrue("문의처: $labels", labels.any { it.contains("pppmaker09@gmail.com") })
        assertTrue("녹음하지 않는다는 안내", labels.any { it.contains("녹음·저장하지 않아요") })
        assertTrue(labels.contains("닫기"))
    }

    @Test
    fun privacyDocumentIsBilingualAndNamesTheContact() {
        val file = File("../docs/privacy.html")
        assertTrue("docs/privacy.html 이 있어야 한다: ${file.absolutePath}", file.exists())
        val html = file.readText()
        assertTrue("앱 이름", html.contains("말해봐요! (Say It!)"))
        assertTrue("시행일", html.contains("2026-11-23"))
        assertTrue("문의처", html.contains("pppmaker09@gmail.com"))
        assertTrue("마이크 권한", html.contains("RECORD_AUDIO"))
        assertTrue("음성인식 처리 안내", html.contains("Google 음성인식"))
        assertTrue("광고·제3자 없음", html.contains("제3자"))
        assertTrue("만 13세 미만 보호", html.contains("13세 미만"))
        assertTrue("영문 병기", html.contains("Privacy Policy") && html.contains("Effective date"))
        assertTrue("모바일 보기(viewpoint)", html.contains("name=\"viewport\""))
        assertTrue("이메일 링크", html.contains("mailto:pppmaker09@gmail.com"))
    }

    // -------------------------------------------------- 5) 관리자 설정 UI

    @Test
    fun adminShowsAndAdjustsTheTwoSettings() {
        val activity = admin()
        val countValue = activity.findViewById<TextView>(R.id.settingCountValue)
        val timeValue = activity.findViewById<TextView>(R.id.settingTimeValue)

        assertEquals("기본 15문제", "15문제", countValue.text.toString())
        assertEquals("기본 10초", "10초", timeValue.text.toString())

        val settingsCard = activity.findViewById<LinearLayout>(R.id.adminSettings)
        val search = activity.findViewById<EditText>(R.id.adminSearch)
        val root = settingsCard.parent as LinearLayout
        assertTrue("설정은 검색·새 문제 위쪽", root.indexOfChild(settingsCard) < root.indexOfChild(search.parent as View))

        activity.findViewById<Button>(R.id.settingCountPlus).performClick()
        assertEquals("문제 수 +", "20문제", countValue.text.toString())
        assertEquals(20, Prefs(activity).questionCount)

        activity.findViewById<Button>(R.id.settingTimePlus).performClick()
        assertEquals("제한시간 +", "11초", timeValue.text.toString())
        assertEquals(11, Prefs(activity).timeLimitSeconds)

        activity.findViewById<Button>(R.id.settingCountMinus).performClick()
        activity.findViewById<Button>(R.id.settingTimeMinus).performClick()
        assertEquals("15문제", countValue.text.toString())
        assertEquals("10초", timeValue.text.toString())
    }

    @Test
    fun adminSettingButtonsStopAtTheRangeEnds() {
        val activity = admin()
        val prefs = Prefs(activity)

        prefs.questionCount = Prefs.COUNT_MAX
        prefs.timeLimitSeconds = Prefs.TIME_MIN
        val reopened = admin()
        val plus = reopened.findViewById<Button>(R.id.settingCountPlus)
        val minus = reopened.findViewById<Button>(R.id.settingTimeMinus)
        assertFalse("40문제에서는 + 불가", plus.isEnabled)
        assertFalse("3초에서는 − 불가", minus.isEnabled)

        plus.performClick()
        minus.performClick()
        assertEquals("범위를 넘지 않는다", Prefs.COUNT_MAX, Prefs(reopened).questionCount)
        assertEquals(3, Prefs(reopened).timeLimitSeconds)
    }

    /** v2.6: `기본값 복원` 하나가 `문제 리셋`으로 바뀌었고, 확인창에서 먼저 묻는다. */
    @Test
    fun restoreDefaultsKeepsGameSettingsAndSaysSo() {
        val activity = admin()
        Prefs(activity).questionCount = 40
        Prefs(activity).timeLimitSeconds = 15

        activity.findViewById<Button>(R.id.resetProblems).performClick()
        idle(0)
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        val labels = texts(dialog.window!!.decorView)
        assertTrue(
            "설정은 그대로 남는다고 알려 준다: $labels",
            labels.any { it.contains("게임 설정(문제 수·제한시간)은 그대로 남아요") }
        )
    }

    @Test
    fun configuredTimeLimitDrivesTheChickWalk() {
        assertEquals("9초 → 8.7초 걷기", 8700L, ChickWalk.walkMs(9000L))
        assertEquals("3초 → 2.7초 걷기", 2700L, ChickWalk.walkMs(3000L))
        assertEquals("20초 → 19.7초 걷기", 19700L, ChickWalk.walkMs(20000L))
        assertEquals(0.5f, ChickWalk.timerProgress(4350L, 9000L), 0.001f)
    }
}
