package com.pipi.voicewordgame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
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

        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        // 살랑살랑 애니메이션 중간 프레임이 아니라 가운데 위치로 캐퓨한다.
        main.findViewById<View>(R.id.chickImage).translationX = 0f
        capture(main, File(outDir, "01_first.png"))

        main.findViewById<View>(R.id.startButton).performClick()
        main.findViewById<View>(R.id.hintButton).performClick()
        // 애니메이션은 테스트에서 진행되지 않으므로 최종 상태로 맞춘다.
        main.findViewById<View>(R.id.wordImage).alpha = 1f
        main.findViewById<View>(R.id.hintBadge).scaleX = 1f
        main.findViewById<View>(R.id.hintBadge).scaleY = 1f
        // 실기기(마이크 허용)에서 보이는 상태로 캡처한다.
        main.findViewById<android.widget.TextView>(R.id.micStatus)
            .setText(R.string.mic_listening)
        capture(main, File(outDir, "02_game.png"))

        val admin = Robolectric.buildActivity(AdminActivity::class.java).setup().get()
        capture(admin, File(outDir, "03_admin.png"))

        println("screens: ${outDir.absolutePath}")
    }

    private fun capture(activity: AppCompatActivity, file: File) {
        val root = activity.window.decorView
        val w = if (root.width > 0) root.width else 1233
        val h = if (root.height > 0) root.height else 2673
        root.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, w, h)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("saved ${file.absolutePath} ${bitmap.width}x${bitmap.height}")
    }
}
