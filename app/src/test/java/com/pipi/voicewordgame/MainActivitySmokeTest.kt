package com.pipi.voicewordgame

import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.chip.ChipGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 실제 액티비티를 띄워 화면 구성·게임 시작·점수 처리가 깨지지 않는지 확인한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivitySmokeTest {

    @Test
    fun setupScreenHasPlayersAndCategories() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val rows = activity.findViewById<LinearLayout>(R.id.playerContainer)
        assertEquals(2, rows.childCount)

        val chips = activity.findViewById<ChipGroup>(R.id.categoryGroup)
        assertEquals(11, chips.childCount)

        val version = activity.findViewById<TextView>(R.id.versionText)
        assertTrue(version.text.toString().startsWith("v1.1_"))
    }

    @Test
    fun startGameShowsPictureAndAnswersAwardPendingScore() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<Button>(R.id.startButton).performClick()

        val gameScreen = activity.findViewById<LinearLayout>(R.id.gameScreen)
        assertEquals(View.VISIBLE, gameScreen.visibility)

        val image = activity.findViewById<ImageView>(R.id.wordImage)
        assertNotNull(image.drawable)

        activity.findViewById<Button>(R.id.answerButton).performClick()
        val banner = activity.findViewById<TextView>(R.id.bannerText)
        assertTrue(banner.text.toString().contains("정답"))
        assertTrue(banner.text.toString().contains("점수"))
    }

    @Test
    fun languageToggleSwitchesToEnglish() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<TextView>(R.id.langEn).performClick()
        val activityAgain = activity
        assertTrue(activityAgain.findViewById<TextView>(R.id.langEn).text.toString() == "ENG")
        val chips = activityAgain.findViewById<ChipGroup>(R.id.categoryGroup)
        assertEquals(11, chips.childCount)
    }
}
