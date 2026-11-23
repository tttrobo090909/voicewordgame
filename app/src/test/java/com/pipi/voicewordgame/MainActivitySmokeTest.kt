package com.pipi.voicewordgame

import android.app.Dialog
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.time.Duration

/** v2.0 첫 화면·게임 화면·힌트 배지·관리자 잠금이 실제로 동작하는지 확인한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class MainActivitySmokeTest {

    private fun launch(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java).setup().get()

    @Test
    fun firstScreenHasRandomButtonAndSixCategories() {
        val activity = launch()

        val random = activity.findViewById<Button>(R.id.randomButton)
        assertEquals("전체 무작위 (425)", random.text.toString())

        val grid = activity.findViewById<LinearLayout>(R.id.categoryGrid)
        assertEquals(3, grid.childCount)
        var tiles = 0
        for (row in 0 until grid.childCount) {
            val rowView = grid.getChildAt(row) as LinearLayout
            assertEquals(2, rowView.childCount)
            tiles += rowView.childCount
        }
        assertEquals(6, tiles)

        val labels = (0 until grid.childCount).flatMap { row ->
            val rowView = grid.getChildAt(row) as LinearLayout
            (0 until rowView.childCount).map {
                rowView.getChildAt(it).findViewById<TextView>(R.id.tileLabel).text.toString()
            }
        }
        assertEquals(
            listOf("동물 (91)", "음식·과일 (91)", "탈것 (40)", "사물·집 (119)", "자연 (42)", "사람·몸 (42)"),
            labels
        )

        val version = activity.findViewById<TextView>(R.id.versionText)
        assertTrue(version.text.toString().startsWith("v2.6_"))

        // v1.x 의 가족/목소리/소음 UI 는 없어야 한다.
        val texts = HashSet<String>()
        collectTexts(activity.window.decorView, texts)
        assertTrue(texts.none { it.contains("가족") || it.contains("목소리") || it.contains("소음") })
    }

    private fun collectTexts(view: View, out: MutableSet<String>) {
        if (view is TextView) out.add(view.text?.toString().orEmpty())
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) collectTexts(view.getChildAt(i), out)
        }
    }

    @Test
    fun startGameShowsPictureAndCountsCorrectAnswer() {
        val activity = launch()
        activity.findViewById<Button>(R.id.startButton).performClick()

        assertEquals(View.VISIBLE, activity.findViewById<LinearLayout>(R.id.gameScreen).visibility)
        assertNotNull(activity.findViewById<ImageView>(R.id.wordImage).drawable)
        assertEquals("문제 1/15 · 정답 0", activity.findViewById<TextView>(R.id.scoreText).text.toString())

        activity.findViewById<Button>(R.id.answerButton).performClick()
        val reveal = activity.findViewById<TextView>(R.id.answerReveal).text.toString()
        assertTrue(reveal.startsWith("정답!"))
        assertEquals("문제 1/15 · 정답 1", activity.findViewById<TextView>(R.id.scoreText).text.toString())
    }

    @Test
    fun hintBadgeIsBigAndSitsAboveThePicture() {
        val activity = launch()
        activity.findViewById<Button>(R.id.startButton).performClick()
        activity.findViewById<Button>(R.id.hintButton).performClick()

        val badge = activity.findViewById<TextView>(R.id.hintBadge)
        assertEquals(View.VISIBLE, badge.visibility)
        assertTrue(badge.text.toString().startsWith("힌트: "))
        assertTrue(badge.text.toString().contains("글자)"))

        val minPx = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 24f, activity.resources.displayMetrics
        )
        assertTrue("힌트 글씨가 24sp 이상이어야 함", badge.textSize >= minPx)

        val params = badge.layoutParams as android.widget.FrameLayout.LayoutParams
        assertTrue("힌트는 그림 카드 위쪽에 있어야 함", params.gravity and android.view.Gravity.TOP != 0)
    }

    @Test
    fun afterSpeakingTwiceTheNextQuestionComes() {
        val activity = launch()
        activity.findViewById<Button>(R.id.startButton).performClick()
        val reveal = activity.findViewById<TextView>(R.id.answerReveal)

        activity.findViewById<Button>(R.id.answerButton).performClick()
        assertTrue(reveal.text.toString().startsWith("정답!"))

        // TTS 완료(또는 4초 안전 타이머) + 500ms 뒤 다음 문제로 넘어간다.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(4600))
        assertEquals("무엇일까요?", reveal.text.toString())
    }

    @Test
    fun skipAlsoSpeaksAndMovesOn() {
        val activity = launch()
        activity.findViewById<Button>(R.id.startButton).performClick()
        val reveal = activity.findViewById<TextView>(R.id.answerReveal)

        activity.findViewById<Button>(R.id.skipButton).performClick()
        assertTrue(reveal.text.toString().startsWith("정답은 "))

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(4600))
        assertEquals("무엇일까요?", reveal.text.toString())
    }

    @Test
    fun threeQuickTitleTapsOpenGuguLock() {
        val activity = launch()
        val title = activity.findViewById<TextView>(R.id.titleText)
        repeat(3) { title.performClick() }

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("구구단 잠금 다이얼로그가 떠야 함", dialog)
        assertTrue(dialog.isShowing)
        val question = dialog.findViewById<TextView>(R.id.guguQuestion)
        assertNotNull(question)
        assertTrue(question.text.contains("×"))
    }

    @Test
    fun wrongGuguAnswerClosesWithoutOpeningAdmin() {
        val activity = launch()
        repeat(3) { activity.findViewById<TextView>(R.id.titleText).performClick() }
        val dialog = ShadowDialog.getLatestDialog()
        // Dialog.show() 는 onShow 콜백을 메시지 큐로 보내므로, 버튼 리스너가 붙도록 한 번 돌린다.
        shadowOf(Looper.getMainLooper()).idle()
        val input = dialog.findViewById<EditText>(R.id.guguInput)
        input.setText("0")
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1200))
        assertFalse(dialog.isShowing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun correctGuguAnswerOpensAdminScreen() {
        val activity = launch()
        repeat(3) { activity.findViewById<TextView>(R.id.titleText).performClick() }
        val dialog = ShadowDialog.getLatestDialog()
        val question = dialog.findViewById<TextView>(R.id.guguQuestion).text.toString()
        shadowOf(Looper.getMainLooper()).idle()
        val match = Regex("(\\d+)\\s*×\\s*(\\d+)").find(question)!!
        val answer = match.groupValues[1].toInt() * match.groupValues[2].toInt()
        dialog.findViewById<EditText>(R.id.guguInput).setText(answer.toString())
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val intent = shadowOf(activity).nextStartedActivity
        assertNotNull(intent)
        assertEquals("com.pipi.voicewordgame.admin.AdminActivity", intent.component?.className)
    }
}
