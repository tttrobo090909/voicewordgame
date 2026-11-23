package com.pipi.voicewordgame

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.pipi.voicewordgame.game.WordJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.security.MessageDigest

/**
 * v2.5 검증 — 그림만 보고 유추할 때 어긋나던 12개 단어의 3D 그림을 새로 그려 넣었다.
 *
 * 033 불가사리 · 050 반딧불이 · 116 유모차 · 174 머리카락 · 180 베개 · 204 지우개 ·
 * 216 블록 · 230 경찰서 · 232 공원 · 235 수족관 · 241 공항 · 244 간호사
 *
 * 새 그림은 Noto Emoji 3D(투명 배경 PNG)와 달리 앱에서 직접 그린 3D 일러스트라
 * 순백(#FFFFFF) 배경을 가진 불투명 WebP 다. 그 성질로 12장이 실제로 교체됐는지 확인한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V25Test {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun assetBook() = WordJson.parse(
        context.assets.open("words.json").bufferedReader().use { it.readText() }
    )

    private val newArt = linkedMapOf(
        33 to ("불가사리" to "Starfish"),
        50 to ("반딧불이" to "Firefly"),
        116 to ("유모차" to "Stroller"),
        174 to ("머리카락" to "Hair"),
        180 to ("베개" to "Pillow"),
        204 to ("지우개" to "Eraser"),
        216 to ("블록" to "Block"),
        230 to ("경찰서" to "Police station"),
        232 to ("공원" to "Park"),
        235 to ("수족관" to "Aquarium"),
        241 to ("공항" to "Airport"),
        244 to ("간호사" to "Nurse")
    )

    @Test
    fun assetBookIsVersion25() {
        assertEquals("2.6", assetBook().appVersion)
    }

    @Test
    fun twelvePicturesWereReplacedWithCustomArt() {
        val book = assetBook()
        newArt.forEach { (id, names) ->
            val word = book.words.firstOrNull { it.id == id }
            assertNotNull("$id 번 단어가 있어야 함", word)
            assertEquals("$id 번 한글", names.first, word!!.ko)
            assertEquals("$id 번 영문", names.second, word.en)
        }
    }

    @Test
    fun twelveNewPicturesAreOpaqueOnPureWhiteBackground() {
        newArt.keys.forEach { id ->
            val path = "words/%03d.webp".format(id)
            val bytes = context.assets.open(path).use { it.readBytes() }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertNotNull("$path 를 읽을 수 있어야 함", bitmap)
            assertEquals("$path 크기 512", 512, bitmap.width)
            assertEquals("$path 크기 512", 512, bitmap.height)
            // 순백 배경(RGB)이라 모든 픽셀이 불투명하고 네 귀퉁이가 정확히 흰색이다.
            assertEquals("$path 왼쪽 위 흰색", -0x1, bitmap.getPixel(0, 0))
            assertEquals("$path 오른쪽 아래 흰색", -0x1, bitmap.getPixel(511, 511))
            assertEquals("$path 왼쪽 가운데 흰색", -0x1, bitmap.getPixel(0, 256))
            assertEquals("$path 불투명", 255, (bitmap.getPixel(256, 256) ushr 24) and 0xFF)
        }
    }

    @Test
    fun twelveNewPicturesDifferFromEachOther() {
        val hashes = newArt.keys.map { id ->
            val path = "words/%03d.webp".format(id)
            val bytes = context.assets.open(path).use { it.readBytes() }
            MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
        }
        assertEquals("새 그림 12장이 모두 다른 파일", 12, hashes.distinct().size)
    }

    @Test
    fun starfishAndStarKeepSeparatePictures() {
        val book = assetBook()
        val starfish = book.words.first { it.ko == "불가사리" }
        val star = book.words.first { it.ko == "별" }
        assertEquals("Star", star.en)
        assertTrue("불가사리와 별은 다른 그림", starfish.image != star.image)
    }

    @Test
    fun nurseAndDoctorKeepSeparatePictures() {
        val book = assetBook()
        val nurse = book.words.first { it.ko == "간호사" }
        val doctor = book.words.first { it.ko == "의사" }
        assertTrue("간호사와 의사는 다른 그림", nurse.image != doctor.image)
    }
}
