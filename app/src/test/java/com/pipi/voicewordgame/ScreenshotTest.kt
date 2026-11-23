package com.pipi.voicewordgame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import com.pipi.voicewordgame.admin.AdminActivity
import com.pipi.voicewordgame.game.Word
import com.pipi.voicewordgame.game.WordStore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * 실제 화면을 비트맵으로 그려 PNG 로 남긴다(디자인 확인·보고용).
 * v2.7: 배너 한 줄·두 줄 카테고리·다중 선택·홀수개 타일 폭·관리자 통합 필터 줄(10초).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    @Test
    fun captureFirstGameAndAdminScreens() {
        val outDir = File("build/ui").apply { mkdirs() }
        val stamp = BuildConfig.VERSION_DISPLAY.removePrefix("v") // 2.7_1124_HHmm
        val downloads = File("../../../downloads").apply { mkdirs() }

        WordStore(ApplicationProvider.getApplicationContext()).restoreDefaults()

        // 01 첫 화면 — 배너 한 줄(오른쪽 정렬)·병아리는 글자 왼쪽 여백, 두 줄 카테고리, 모두 선택됨.
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        layout(main)
        main.applySwayPoseForTest(0.25f)
        capture(main, File(outDir, "01_first.png"))
        capture(main, File(downloads, "malhaebwayo-v$stamp-1-첫화면.png"))

        // 02 게임 화면 — 그림 카드·타이머 바.
        main.findViewById<View>(R.id.startButton).performClick()
        main.findViewById<View>(R.id.wordImage).alpha = 1f
        main.findViewById<TextView>(R.id.micStatus).setText(R.string.mic_listening)
        layout(main)
        main.applyTimerPoseForTest(0.45f)
        capture(main, File(outDir, "02_game.png"))
        capture(main, File(downloads, "malhaebwayo-v$stamp-2-게임화면.png"))

        // 03 관리자 화면 — 카테고리 박스 없이 `+ 카테고리` + 문제 수가 필터 줄에, 제한시간 10초.
        val admin = Robolectric.buildActivity(AdminActivity::class.java).setup().get()
        capture(admin, File(outDir, "03_admin.png"))
        capture(admin, File(downloads, "malhaebwayo-v$stamp-3-관리자.png"))

        // 04 첫 화면 — 카테고리를 하나 더해 7개(홀수)여도 타일은 모두 절반 너비.
        val store = WordStore(ApplicationProvider.getApplicationContext())
        store.load()
        val family = store.addCategory("우리 가족", "My Family", "👨‍👩‍👧")!!
        store.add(Word(0, "엄마", "Mom", family.id, store.book.words.first().image))
        val main2 = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        layout(main2)
        main2.applySwayPoseForTest(0.6f)
        capture(main2, File(outDir, "04_first_odd.png"))
        capture(main2, File(downloads, "malhaebwayo-v$stamp-4-카테고리추가_첫화면.png"))

        // 05 관리자 — 필터 줄을 끝까지 밀면 추가한 카테고리가 문제 수와 함께 보인다.
        val admin2 = Robolectric.buildActivity(AdminActivity::class.java).setup().get()
        capture(admin2, File(outDir, "05_admin_categories.png"), beforeDraw = {
            admin2.findViewById<HorizontalScrollView>(R.id.adminFilterScroll)
                .fullScroll(View.FOCUS_RIGHT)
        })
        capture(
            admin2,
            File(downloads, "malhaebwayo-v$stamp-5-관리자_카테고리.png"),
            beforeDraw = {
                admin2.findViewById<HorizontalScrollView>(R.id.adminFilterScroll)
                    .fullScroll(View.FOCUS_RIGHT)
            }
        )

        // 원래대로 되돌려 둔다.
        store.restoreDefaults()

        println("screens: ${outDir.absolutePath} , ${downloads.absolutePath}")
    }

    private fun layout(activity: AppCompatActivity) {
        val root = activity.window.decorView
        val w = if (root.width > 0) root.width else 1233
        val h = if (root.height > 0) root.height else 2673
        root.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, w, h)
    }

    private fun capture(activity: AppCompatActivity, file: File, beforeDraw: (() -> Unit)? = null) {
        layout(activity)
        beforeDraw?.invoke()
        val root = activity.window.decorView
        val w = if (root.width > 0) root.width else 1233
        val h = if (root.height > 0) root.height else 2673
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("saved ${file.absolutePath} ${bitmap.width}x${bitmap.height}")
    }
}
