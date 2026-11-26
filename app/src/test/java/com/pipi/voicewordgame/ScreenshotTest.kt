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
 * v2.9: 420단어(들어봐요! v1.1과 일치)·새 그림·`문제 420개`·카테고리 수 90/94/37/116/41/42.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    @Test
    fun captureFirstGameAndAdminScreens() {
        val outDir = File("build/ui").apply { mkdirs() }
        val stamp = BuildConfig.VERSION_DISPLAY.removePrefix("v") // 2.9_1125_HHmm
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

        // 06 관리자 — v2.9 새 그림(갈비찜 313)을 검색해 보여 준다(기본 6개 카테고리 상태).
        store.restoreDefaults()
        val admin3 = Robolectric.buildActivity(AdminActivity::class.java).setup().get()
        admin3.findViewById<android.widget.EditText>(R.id.adminSearch).setText("갈비찜")
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        capture(admin3, File(outDir, "06_admin_newword.png"))
        capture(admin3, File(downloads, "malhaebwayo-v$stamp-6-관리자_새그림.png"))

        // 원래대로 되돌려 둔다.
        store.restoreDefaults()

        println("screens: ${outDir.absolutePath} , ${downloads.absolutePath}")
    }

    /**
     * 플레이스토어 등록용 원본 캡처(1080x1920 규격으로 다듬기 전).
     * 첫 화면 · 게임 · 정답 · 관리자 네 장을 build/ui/store/ 에 남긴다.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h731dp-xxhdpi")
    fun captureStoreScreens() {
        val outDir = File("build/ui/store").apply { mkdirs() }
        val store = WordStore(ApplicationProvider.getApplicationContext())
        store.restoreDefaults()

        // 1) 첫 화면
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        layout(main)
        main.applySwayPoseForTest(0.3f)
        capture(main, File(outDir, "store_1_first.png"))

        // 2) 게임 화면 — 그림 카드·타이머·듣는 중 (그림은 캡처용으로 사과)
        main.findViewById<View>(R.id.startButton).performClick()
        val apple = store.book.words.first { it.ko == "사과" }
        main.showWordForTest(apple)
        main.findViewById<TextView>(R.id.micStatus).setText(R.string.mic_listening)
        layout(main)
        main.applyTimerPoseForTest(0.18f)
        capture(main, File(outDir, "store_2_game.png"))

        // 3) 정답 화면 — 축하 폭죽이 터진 직후
        main.findViewById<View>(R.id.answerButton).performClick()
        layout(main)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(420))
        capture(main, File(outDir, "store_3_correct.png"))

        // 4) 관리자 화면 — 문제 420개·카테고리 필터·제한시간
        val admin = Robolectric.buildActivity(AdminActivity::class.java).setup().get()
        capture(admin, File(outDir, "store_4_admin.png"))

        store.restoreDefaults()
        println("store screens: ${outDir.absolutePath}")
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
