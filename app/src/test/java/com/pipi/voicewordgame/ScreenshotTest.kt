package com.pipi.voicewordgame

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import com.pipi.voicewordgame.admin.AdminActivity
import com.pipi.voicewordgame.game.WordStore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** 실제 화면을 비트맵으로 그려 PNG 로 남긴다(디자인 확인·보고용). v2.3: 첫 화면·통합 타이머 게임 화면·관리자 설정. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    @Test
    fun captureFirstGameAndAdminScreens() {
        val outDir = File("build/ui").apply { mkdirs() }
        val stamp = BuildConfig.VERSION_DISPLAY.removePrefix("v") // 2.6_1123_HHmm
        val downloads = File("../../../downloads").apply { mkdirs() }

        WordStore(ApplicationProvider.getApplicationContext()).restoreDefaults()

        // 01 첫 화면(한글) — 배너 글자가 커지고 오른쪽 정렬, 아래에 개인정보처리방침 링크.
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        layout(main)
        main.applySwayPoseForTest(0.2f)
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

        // 03 v2.6 신규 그림(참외 291)이 게임 카드에 놓인 모습.
        val newArt = BitmapFactory.decodeStream(main.assets.open("words/291.webp"))
        main.findViewById<ImageView>(R.id.wordImage).setImageBitmap(newArt)
        capture(main, File(outDir, "03_new_art.png"))
        capture(main, File(downloads, "malhaebwayo-v$stamp-3-새그림.png"))

        // 04 관리자 화면 — 게임 설정(10~40)·카테고리 관리·설정 리셋/문제 리셋.
        val admin = Robolectric.buildActivity(AdminActivity::class.java).setup().get()
        capture(admin, File(outDir, "04_admin.png"))
        capture(admin, File(downloads, "malhaebwayo-v$stamp-4-관리자설정.png"))

        // 05 관리자 화면 — 카테고리를 하나 추가한 모습(최대 10개).
        val store = WordStore(ApplicationProvider.getApplicationContext())
        store.load()
        store.addCategory("우리 가족", "My Family", "👨‍👩‍👧")
        val admin2 = Robolectric.buildActivity(AdminActivity::class.java).setup().get()
        capture(admin2, File(outDir, "05_admin_categories.png"))
        capture(admin2, File(downloads, "malhaebwayo-v$stamp-5-카테고리추가.png"))

        // 06 첫 화면 — 카테고리가 늘어도 `전체 무작위`는 그리드 아래·게임 시작 위.
        val main2 = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        main2.applySwayPoseForTest(0.2f)
        capture(main2, File(outDir, "06_first_with_category.png"))
        capture(main2, File(downloads, "malhaebwayo-v$stamp-6-카테고리추가_첫화면.png"))

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
