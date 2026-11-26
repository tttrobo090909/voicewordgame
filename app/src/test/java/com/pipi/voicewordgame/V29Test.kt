package com.pipi.voicewordgame

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import com.pipi.voicewordgame.admin.AdminActivity
import com.pipi.voicewordgame.game.Word
import com.pipi.voicewordgame.game.WordJson
import com.pipi.voicewordgame.game.WordStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File

/**
 * v2.9 확인 — `들어봐요!` v1.1(listengame)의 420단어·그림을 `말해봐요!` 에 그대로 옮긴 버전:
 * 1) 기본 단어 425 → 420 (잉어·모노레일·글라이더·전동휠체어·풀 삭제, 그림 파일도 삭제).
 * 2) 이름 변경 갈비찜 / 배(Ship) / 파도, 카테고리 이동 도시락·커피·크루아상 → 음식·과일.
 * 3) 카테고리별 90·94·37·116·41·42, `appVersion` 2.9 로 기존 설치 기기도 새 기본값으로 갱신.
 * 4) 화면 표기 `v2.9`, versionName `2.9_MMdd_HHmm`, versionCode MMddHHmm — v2.8 UI·기능은 그대로.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class V29Test {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WordStore(context).restoreDefaults()
    }

    private fun assetText(): String =
        context.assets.open("words.json").bufferedReader().use { it.readText() }

    private fun assetBook() = WordJson.parse(assetText())

    private fun mainActivity(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun adminActivity(): AdminActivity =
        Robolectric.buildActivity(AdminActivity::class.java).setup().get()

    // -------------------------------------------------- 1) 420단어 기본값

    @Test
    fun assetBookHas420WordsAndVersion29() {
        val book = assetBook()
        assertEquals(420, book.words.size)
        assertEquals("appVersion 2.9", "2.9", book.appVersion)
        assertTrue("wordCount 420", assetText().contains("\"wordCount\": 420"))
        assertEquals("카테고리 6개 그대로", 6, book.categories.size)
        assertEquals(
            listOf("동물", "음식·과일", "탈것", "사물·집", "자연", "사람·몸"),
            book.categories.map { it.ko }
        )
    }

    @Test
    fun categoryCountsAreNinetyNinetyFourThirtySevenAndSoon() {
        val counts = assetBook().words.groupingBy { it.categoryId }.eachCount()
        assertEquals(90, counts[1])
        assertEquals(94, counts[2])
        assertEquals(37, counts[3])
        assertEquals(116, counts[4])
        assertEquals(41, counts[5])
        assertEquals(42, counts[6])
        assertEquals(420, counts.values.sum())
    }

    @Test
    fun deletedWordsAndTheirPicturesAreGone() {
        val book = assetBook()
        listOf("잉어", "모노레일", "글라이더", "전동휠체어", "풀")
            .forEach { gone -> assertTrue("$gone 삭제", book.words.none { it.ko == gone }) }
        listOf(144, 278, 339, 340, 346).forEach { id ->
            val path = "words/%03d.webp".format(id)
            val stream = runCatching { context.assets.open(path) }.getOrNull()
            assertTrue("$path 삭제", stream == null)
            stream?.close()
        }
    }

    @Test
    fun renamedAndMovedWordsFollowListeningGame() {
        val book = assetBook()
        assertEquals("Galbijjim", book.words.first { it.ko == "갈비찜" }.en)
        assertEquals("Wave", book.words.first { it.ko == "파도" }.en)
        val ship = book.words.first { it.ko == "배" && it.en == "Ship" }
        assertEquals("탈것", book.categories.first { it.id == ship.categoryId }.ko)
        val pear = book.words.first { it.ko == "배" && it.en == "Pear" }
        assertEquals("음식·과일", book.categories.first { it.id == pear.categoryId }.ko)
        listOf("도시락", "커피", "크루아상").forEach { ko ->
            assertEquals("$ko 는 음식·과일", "음식·과일", book.categories.first { it.id == book.words.first { w -> w.ko == ko }.categoryId }.ko)
        }
        // 옛 이름은 남아 있지 않다.
        listOf("갈비", "보트", "바다").forEach { old ->
            assertTrue("옛 이름 $old 없음", book.words.none { it.ko == old })
        }
    }

    @Test
    fun everyWordPointsAtItsOwnExistingPicture() {
        assetBook().words.forEach { word ->
            assertEquals("id 와 그림 번호", "words/%03d.webp".format(word.id), word.image)
            val stream = runCatching { context.assets.open(word.image) }.getOrNull()
            assertTrue("그림 없음: ${word.image} (${word.ko})", stream != null)
            stream?.close()
        }
    }

    // -------------------------------------------------- 2) 기존 설치 기기도 새 기본값으로

    @Test
    fun installedOldBookIsReplacedByNewDefaults() {
        // v2.8 기기에 남아 있던 내부 words.json(appVersion 2.6, 425문제)을 흉내 낸다.
        val old = assetBook().copy(
            words = assetBook().words + Word(999, "임시", "Temp", 1, "words/001.webp"),
            appVersion = "2.6"
        )
        File(context.filesDir, WordStore.FILE_NAME).writeText(WordJson.write(old))

        val store = WordStore(context)
        store.load()
        assertEquals("새 기본값 420문제로 갱신", 420, store.book.words.size)
        assertEquals("2.9", store.book.appVersion)
        assertTrue("관리자 임시 단어는 사라진다", store.book.words.none { it.ko == "임시" })
    }

    // -------------------------------------------------- 3) 화면 표기·기능 유지

    @Test
    fun footerShowsV29Only() {
        val main = mainActivity()
        assertEquals("v2.9", main.findViewById<TextView>(R.id.versionText).text.toString())
        assertEquals("v2.9", BuildConfig.VERSION_SHORT)
        assertTrue("versionName 은 2.9_MMdd_HHmm: ${BuildConfig.VERSION_NAME}",
            Regex("""2\.9_\d{4}_\d{4}""").matches(BuildConfig.VERSION_NAME))
        assertEquals(
            "versionCode = 1125HHMM",
            BuildConfig.VERSION_NAME.substringAfter('_').replace("_", "").toInt(),
            BuildConfig.VERSION_CODE
        )
        assertTrue("v2.8(11240641)보다 커야 덮어쓰기 설치된다", BuildConfig.VERSION_CODE > 11240641)
    }

    @Test
    fun firstScreenTilesShowNewCountsAndKeepOneLineBanner() {
        val main = mainActivity()
        val grid = main.findViewById<LinearLayout>(R.id.categoryGrid)
        val labels = (0 until grid.childCount).flatMap { row ->
            val rowView = grid.getChildAt(row) as LinearLayout
            (0 until rowView.childCount).map {
                rowView.getChildAt(it).findViewById<TextView>(R.id.tileLabel).text.toString()
            }
        }
        assertEquals(
            listOf(
                "동물\n(90)", "음식·과일\n(94)", "탈것\n(37)",
                "사물·집\n(116)", "자연\n(41)", "사람·몸\n(42)"
            ),
            labels
        )
        // v2.8 첫 화면 배너(한 줄 20.7sp)가 그대로다.
        val banner = main.findViewById<TextView>(R.id.bannerText)
        assertEquals("그림을 보고 단어를 맞혀요!", banner.text.toString())
        assertEquals(1, banner.maxLines)
    }

    @Test
    fun adminShowsFourHundredTwentyProblemsAndNewCounts() {
        val admin = adminActivity()
        assertEquals("문제 420개", admin.findViewById<TextView>(R.id.adminCount).text.toString())
        val row = admin.findViewById<LinearLayout>(R.id.adminFilterRow)
        assertEquals("전체 (420)", (row.getChildAt(1) as Button).text.toString())
        assertEquals("동물 (90)", (row.getChildAt(2) as Button).text.toString())
        assertEquals("음식·과일 (94)", (row.getChildAt(3) as Button).text.toString())
        assertEquals("탈것 (37)", (row.getChildAt(4) as Button).text.toString())
        assertEquals("사물·집 (116)", (row.getChildAt(5) as Button).text.toString())
        assertEquals("자연 (41)", (row.getChildAt(6) as Button).text.toString())
        assertEquals("사람·몸 (42)", (row.getChildAt(7) as Button).text.toString())
    }

    @Test
    fun resetDialogMentions420() {
        val admin = adminActivity()
        admin.findViewById<Button>(R.id.resetProblems).performClick()
        val dialog = ShadowDialog.getLatestDialog()
        val texts = ArrayList<String>()
        fun walk(view: View) {
            if (view is TextView && view.text.isNotEmpty()) texts.add(view.text.toString())
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(dialog.window!!.decorView)
        assertTrue("리셋 안내: $texts", texts.any { it.contains("기본 420문제") })
        assertFalse("옛 425 표기 없음", texts.any { it.contains("425") })
    }
}
