package com.pipi.voicewordgame

import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.pipi.voicewordgame.admin.AdminActivity
import com.pipi.voicewordgame.game.WordStore
import org.junit.Assert.assertEquals
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

/** 관리자 화면: 검색·카테고리 필터·새 문제 추가·내장 그림 고르기가 실제로 동작하는지 확인한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AdminScreenTest {

    private lateinit var activity: AdminActivity

    @Before
    fun setUp() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        WordStore(context).restoreDefaults()
        activity = Robolectric.buildActivity(AdminActivity::class.java).setup().get()
    }

    private fun list(): RecyclerView = activity.findViewById(R.id.problemList)

    private fun search(text: String) {
        activity.findViewById<EditText>(R.id.adminSearch).setText(text)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun listsAllProblemsAndShowsCount() {
        assertEquals(420, list().adapter?.itemCount)
        assertEquals("문제 420개", activity.findViewById<android.widget.TextView>(R.id.adminCount).text.toString())
    }

    @Test
    fun searchFindsKoreanAndEnglish() {
        search("강아지")
        assertEquals(1, list().adapter?.itemCount)

        search("banana")
        assertEquals(1, list().adapter?.itemCount)

        search("없는단어")
        assertEquals(0, list().adapter?.itemCount)
        assertEquals(android.view.View.VISIBLE, activity.findViewById<android.view.View>(R.id.adminEmpty).visibility)
    }

    @Test
    fun categoryFilterLimitsList() {
        val filters = activity.findViewById<LinearLayout>(R.id.adminFilterRow)
        assertEquals(8, filters.childCount) // + 카테고리 + 전체 + 6개 카테고리

        (filters.getChildAt(4) as Button).performClick() // 탈것
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(37, list().adapter?.itemCount)
    }

    @Test
    fun addsNewProblemAndKeepsItInStore() {
        activity.findViewById<Button>(R.id.addProblem).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)

        dialog.findViewById<EditText>(R.id.editKo).setText("로봇청소기")
        dialog.findViewById<EditText>(R.id.editEn).setText("Robot vacuum")
        dialog.findViewById<EditText>(R.id.editHint).setText("청소")
        dialog.findViewById<EditText>(R.id.editSyn).setText("청소기, 로봇")
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(421, list().adapter?.itemCount)

        val store = WordStore(activity)
        store.load()
        val created = store.book.words.firstOrNull { it.ko == "로봇청소기" }
        assertNotNull(created)
        assertEquals("청소", created!!.hint)
        assertEquals(listOf("청소기", "로봇"), created.synKo)
    }

    @Test
    fun emptyAnswerIsRejectedWithoutSaving() {
        activity.findViewById<Button>(R.id.addProblem).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog()

        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(dialog.isShowing)
        assertEquals(android.view.View.VISIBLE, dialog.findViewById<android.view.View>(R.id.editError)!!.visibility)
        assertEquals(420, list().adapter?.itemCount)
    }

    @Test
    fun builtInArtPickerOffersAllDefaultPictures() {
        activity.findViewById<Button>(R.id.addProblem).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val editor = ShadowDialog.getLatestDialog()

        editor.findViewById<Button>(R.id.pickBuiltin).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val picker = ShadowDialog.getLatestDialog()
        assertNotNull(picker)
        val grid = findRecycler(picker!!)
        assertEquals(420, grid.adapter?.itemCount)
    }

    private fun findRecycler(root: android.app.Dialog): RecyclerView {
        fun walk(view: android.view.View): RecyclerView? {
            if (view is RecyclerView) return view
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))?.let { return it }
            }
            return null
        }
        return walk(root.window!!.decorView) ?: error("내장 그림 그리드를 찾지 못함")
    }
}
