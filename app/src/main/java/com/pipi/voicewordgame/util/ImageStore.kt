package com.pipi.voicewordgame.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

/** assets 안의 WebP 그림을 메모리 캐시와 함께 읽는다. */
object ImageStore {

    private val cache = object : LruCache<String, Bitmap>(20 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(context: Context, path: String, sample: Int = 1): Bitmap? {
        if (path.isBlank()) return null
        val key = "$path#$sample"
        cache.get(key)?.let { return it }
        return try {
            context.assets.open(path).use { stream ->
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample.coerceAtLeast(1)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bmp = BitmapFactory.decodeStream(stream, null, opts)
                if (bmp != null) cache.put(key, bmp)
                bmp
            }
        } catch (e: Exception) {
            null
        }
    }

    fun clear() {
        cache.evictAll()
    }
}
