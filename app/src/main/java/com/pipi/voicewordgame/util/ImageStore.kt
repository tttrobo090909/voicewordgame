package com.pipi.voicewordgame.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/**
 * 그림 읽기.
 * - `words/001.webp` 처럼 상대 경로면 assets 의 내장 3D 그림
 * - `file:images/custom_...webp` 면 관리자 화면에서 기기 갤러리로 넣은 사진(앱 내부 파일)
 */
object ImageStore {

    const val FILE_PREFIX = "file:"

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun isLocalFile(path: String): Boolean = path.startsWith(FILE_PREFIX)

    fun load(context: Context, path: String, sample: Int = 1): Bitmap? {
        if (path.isBlank()) return null
        val key = "$path#$sample"
        cache.get(key)?.let { return it }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = try {
            if (isLocalFile(path)) {
                val file = File(context.filesDir, path.removePrefix(FILE_PREFIX))
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath, opts) else null
            } else {
                context.assets.open(path).use { stream -> BitmapFactory.decodeStream(stream, null, opts) }
            }
        } catch (e: Exception) {
            null
        }
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    fun clear() {
        cache.evictAll()
    }
}
