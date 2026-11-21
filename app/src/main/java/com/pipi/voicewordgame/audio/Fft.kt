package com.pipi.voicewordgame.audio

/** radix-2 FFT (입력 길이는 2의 거듭제곱). */
object Fft {

    fun magnitude(samples: DoubleArray, window: DoubleArray? = null): DoubleArray {
        val n = samples.size
        if (n < 2 || (n and (n - 1)) != 0) return DoubleArray(0)
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        for (i in 0 until n) {
            re[i] = if (window != null) samples[i] * window[i] else samples[i]
        }

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wRe = Math.cos(ang)
            val wIm = Math.sin(ang)
            var start = 0
            while (start < n) {
                var curRe = 1.0
                var curIm = 0.0
                val half = len / 2
                for (k in 0 until half) {
                    val aRe = re[start + k]
                    val aIm = im[start + k]
                    val bRe = re[start + k + half] * curRe - im[start + k + half] * curIm
                    val bIm = re[start + k + half] * curIm + im[start + k + half] * curRe
                    re[start + k] = aRe + bRe
                    im[start + k] = aIm + bIm
                    re[start + k + half] = aRe - bRe
                    im[start + k + half] = aIm - bIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                start += len
            }
            len = len shl 1
        }

        return DoubleArray(n / 2) { k -> Math.hypot(re[k], im[k]) }
    }
}
