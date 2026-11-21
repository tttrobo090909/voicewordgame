package com.pipi.voicewordgame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** 실제 화면을 비트맵으로 그려 PNG 로 남긴다(디자인 확인용). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    @Test
    fun captureSetupAndGame() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val outDir = File("build/ui").apply { mkdirs() }

        capture(activity.window.decorView, File(outDir, "01_setup.png"))

        val bmp = com.pipi.voicewordgame.util.ImageStore.load(activity, "words/001.webp")
        println("word bitmap = ${bmp?.width}x${bmp?.height}")

        activity.findViewById<View>(R.id.startButton).performClick()
        activity.findViewById<View>(R.id.hintButton).performClick()
        // 다음 그림으로 넘어갈 때의 페이드인 애니메이션은 테스트에서 진행되지 않으므로 직접 1로 둔다.
        activity.findViewById<View>(R.id.wordImage).alpha = 1f
        capture(activity.window.decorView, File(outDir, "02_game.png"))

        val iv = activity.findViewById<android.widget.ImageView>(R.id.wordImage)
        println("imageView = ${iv.width}x${iv.height} drawable=${iv.drawable?.intrinsicWidth}x${iv.drawable?.intrinsicHeight} bounds=${iv.drawable?.bounds}")

        activity.findViewById<View>(R.id.answerButton).performClick()
        capture(activity.window.decorView, File(outDir, "03_answer.png"))
    }

    private fun capture(root: View, file: File) {
        val w = if (root.width > 0) root.width else 1080
        val h = if (root.height > 0) root.height else 2200
        root.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("saved ${file.absolutePath} ${bmp.width}x${bmp.height}")
    }
}
