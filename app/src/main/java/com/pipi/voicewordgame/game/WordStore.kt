package com.pipi.voicewordgame.game

import android.content.Context
import java.io.File

/**
 * 문제 데이터 저장소.
 *
 * - 첫 실행: `assets/words.json`(기본 250문제)을 앱 내부 파일로 복사해 쓴다.
 * - 관리자 화면에서 고친 내용은 내부 `filesDir/words.json` 에 즉시 저장되어
 *   다음 게임에 바로 반영된다. (SharedPreferences 대신 JSON 파일 한 개)
 * - 앱 업데이트로 기본 데이터 버전이 바뀌면 새 기본값으로 다시 시작한다.
 * - `restoreDefaults()` 는 내부 파일을 지우고 기본값으로 되돌린다.
 */
class WordStore(private val context: Context) {

    companion object {
        const val FILE_NAME = "words.json"
        const val IMAGE_DIR = "images"
        const val FILE_PREFIX = "file:"
    }

    var book: WordBook = WordBook(emptyList(), emptyList())
        private set

    private val file: File get() = File(context.filesDir, FILE_NAME)

    /** assets 기본 데이터(항상 앱에 들어 있는 원본). */
    fun defaults(): WordBook = runCatching {
        WordJson.parse(readAssetText())
    }.getOrElse { WordBook(emptyList(), emptyList()) }

    fun load() {
        val asset = defaults()
        val stored = if (file.exists()) {
            runCatching { WordJson.parse(file.readText()) }.getOrNull()
        } else {
            null
        }
        book = if (stored != null && stored.words.isNotEmpty() && stored.appVersion == asset.appVersion) {
            stored
        } else {
            val fresh = asset.copy(appVersion = asset.appVersion)
            write(fresh)
            fresh
        }
    }

    fun restoreDefaults() {
        val fresh = defaults()
        book = fresh
        write(fresh)
    }

    fun add(word: Word): Word {
        val newId = (book.words.maxOfOrNull { it.id } ?: 0) + 1
        val created = word.copy(id = newId)
        book = book.copyWithWords(book.words + created)
        write(book)
        return created
    }

    fun update(word: Word) {
        book = book.copyWithWords(book.words.map { if (it.id == word.id) word else it })
        write(book)
    }

    fun delete(id: Int) {
        book = book.copyWithWords(book.words.filterNot { it.id == id })
        write(book)
    }

    fun nextId(): Int = (book.words.maxOfOrNull { it.id } ?: 0) + 1

    /** 내장 3D 그림 목록(관리자 '그림 바꾸기'용, 중복 제거). */
    fun builtInImages(): List<String> =
        defaults().words.map { it.image }.distinct()

    /** 기기 갤러리에서 고른 사진을 앱 내부 파일로 복사하고 저장 경로 키를 돌려준다. */
    fun savePickedImage(bytes: ByteArray, extension: String): String? {
        return runCatching {
            val dir = File(context.filesDir, IMAGE_DIR).apply { mkdirs() }
            val name = "custom_${System.currentTimeMillis()}.${extension.ifBlank { "webp" }}"
            File(dir, name).writeBytes(bytes)
            "$FILE_PREFIX$IMAGE_DIR/$name"
        }.getOrNull()
    }

    private fun write(book: WordBook) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(WordJson.write(book))
        }
    }

    private fun readAssetText(): String =
        context.assets.open("words.json").bufferedReader().use { it.readText() }
}
