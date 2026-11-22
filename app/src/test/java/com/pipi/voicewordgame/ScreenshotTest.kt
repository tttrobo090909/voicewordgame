package com.pipi.voicewordgame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pipi.voicewordgame.admin.AdminActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** 실제 화면을 비트맵으로 그려 PNG 로 남긴다(디자인 확인·보고용). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    @Test
    fun captureFirstGameAndAdminScreens() {
        val outDir = File("build/ui").apply { mkdirs() }

        // 01 첫 화면(한글) — 병아리는 오른쪽으로 걷는 중간 자세.
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        layout(main)
        main.applySwayPoseForTest(0.2f)
        capture(main, File(outDir, "01_first.png"))

        // 02 게임 화면 — 상단 병아리 타이머가 45%쯤 걸어간 상태.
        main.findViewById<View>(R.id.startButton).performClick()
        main.findViewById<View>(R.id.wordImage).alpha = 1f
        // 실기기(마이크 허용)에서 보이는 상태로 캡처한다.
        main.findViewById<TextView>(R.id.micStatus).setText(R.string.mic_listening)
        layout(main)
        main.applyTimerPoseForTest(0.45f)
        capture(main, File(outDir, "02_game.png"))

        // 03 게임 화면 + 힌트 배지(시간 초과 직전 자세).
        main.findViewById<View>(R.id.hintButton).performClick()
        main.findViewById<View>(R.id.hintBadge).scaleX = 1f
        main.findViewById<View>(R.id.hintBadge).scaleY = 1f
        main.applyTimerPoseForTest(0.92f)
        capture(main, File(outDir, "03_game_hint.png"))

        // 04 첫 화면(ENG) — 타일 이름만 영어, 나머지 문구는 한국어.
        val eng = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        eng.findViewById<View>(R.id.langEn).performClick()
        layout(eng)
        eng.applySwayPoseForTest(0.7f)
        capture(eng, File(outDir, "04_first_eng.png"))

        // 05 관리자 화면(한국어 유지).
        val admin = Robolectric.buildActivity(AdminActivity::class.java).setup().get()
        capture(admin, File(outDir, "05_admin.png"))

        println("screens: ${outDir.absolutePath}")
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

    private fun capture(activity: AppCompatActivity, file: File) {
        layout(activity)
        val root = activity.window.decorView
        val w = if (root.width > 0) root.width else 1233
        val h = if (root.height > 0) root.height else 2673
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("saved ${file.absolutePath} ${bitmap.width}x${bitmap.height}")
    }
}
