package com.pipi.voicewordgame.audio

import com.pipi.voicewordgame.game.VoiceProfile
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 16kHz PCM 에서 화자 구분용 특징(음높이·스펙트럼 중심·13밴드 에너지)을 뽑는다.
 * 말소리가 거의 없으면 null.
 */
object VoiceFeatureExtractor {

    const val SAMPLE_RATE = 16000
    private const val FRAME = 512          // 32ms
    private const val HOP = 256            // 16ms
    private const val BAND_COUNT = 13
    private const val MIN_PITCH_HZ = 70.0
    private const val MAX_PITCH_HZ = 400.0

    private val window: DoubleArray by lazy {
        DoubleArray(FRAME) { i -> 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (FRAME - 1)) }
    }

    fun analyze(samples: ShortArray): VoiceProfile? {
        if (samples.size < FRAME * 2) return null
        val frameCount = (samples.size - FRAME) / HOP + 1
        if (frameCount <= 0) return null

        val dbfs = DoubleArray(frameCount)
        for (f in 0 until frameCount) {
            var sum = 0.0
            val off = f * HOP
            for (i in 0 until FRAME) {
                val v = samples[off + i] / 32768.0
                sum += v * v
            }
            dbfs[f] = 20.0 * Math.log10(max(1e-7, sqrt(sum / FRAME)))
        }

        var maxDb = -120.0
        for (v in dbfs) if (v > maxDb) maxDb = v
        val threshold = max(maxDb - 14.0, -56.0)
        val voiced = (0 until frameCount).filter { dbfs[it] >= threshold }
        if (voiced.size < 5) return null

        val pitches = ArrayList<Double>(voiced.size)
        var centroidSum = 0.0
        var centroidCount = 0
        val bands = DoubleArray(BAND_COUNT)

        val freqs = DoubleArray(FRAME / 2) { it * SAMPLE_RATE.toDouble() / FRAME }

        for (f in voiced) {
            val off = f * HOP
            val raw = DoubleArray(FRAME) { i -> samples[off + i] / 32768.0 }

            val mag = Fft.magnitude(raw, window)
            if (mag.isNotEmpty()) {
                var num = 0.0
                var den = 0.0
                for (k in 1 until mag.size) {
                    if (freqs[k] < 60.0 || freqs[k] > 4000.0) continue
                    num += freqs[k] * mag[k]
                    den += mag[k]
                }
                if (den > 1e-9) {
                    centroidSum += num / den
                    centroidCount += 1
                }
                for (k in 1 until mag.size) {
                    val f0 = freqs[k]
                    if (f0 < 100.0 || f0 > 5000.0) continue
                    val b = bandIndex(f0)
                    bands[b] += mag[k]
                }
            }

            val pitch = estimatePitch(raw)
            if (pitch > 0) pitches.add(pitch)
        }

        pitches.sort()
        val pitch = if (pitches.isEmpty()) 0.0 else pitches[pitches.size / 2]
        val centroid = if (centroidCount == 0) 0.0 else centroidSum / centroidCount

        // 밴드 에너지를 정규화한다(코사인 유사도로 비교).
        var norm = 0.0
        for (i in bands.indices) {
            bands[i] = ln(1.0 + bands[i])
            norm += bands[i] * bands[i]
        }
        norm = sqrt(norm)
        if (norm > 1e-9) {
            for (i in bands.indices) bands[i] /= norm
        } else {
            return null
        }

        val voicedDb = voiced.map { dbfs[it] }.average()
        return VoiceProfile(
            pitch = pitch.toFloat(),
            centroid = centroid.toFloat(),
            bands = FloatArray(BAND_COUNT) { bands[it].toFloat() },
            levelDb = voicedDb.toFloat(),
            seconds = samples.size.toFloat() / SAMPLE_RATE
        )
    }

    private fun bandIndex(freq: Double): Int {
        val lo = 100.0
        val hi = 5000.0
        val pos = ln(freq / lo) / ln(hi / lo)
        return min(BAND_COUNT - 1, max(0, (pos * BAND_COUNT).toInt()))
    }

    /** 자기상관(autocorrelation) 기반 음높이 추정. 신뢰도가 낮으면 0. */
    private fun estimatePitch(frame: DoubleArray): Double {
        var mean = 0.0
        for (v in frame) mean += v
        mean /= frame.size
        val x = DoubleArray(frame.size) { frame[it] - mean }

        var energy = 0.0
        for (v in x) energy += v * v
        if (energy <= 1e-9) return 0.0

        val minLag = (SAMPLE_RATE / MAX_PITCH_HZ).toInt().coerceAtLeast(2)
        val maxLag = min((SAMPLE_RATE / MIN_PITCH_HZ).toInt(), x.size / 2)
        if (maxLag <= minLag) return 0.0

        val corr = DoubleArray(maxLag + 1)
        for (lag in minLag..maxLag) {
            var sum = 0.0
            var e1 = 0.0
            var e2 = 0.0
            var i = 0
            while (i + lag < x.size) {
                sum += x[i] * x[i + lag]
                e1 += x[i] * x[i]
                e2 += x[i + lag] * x[i + lag]
                i += 1
            }
            val denom = sqrt(e1 * e2)
            corr[lag] = if (denom <= 1e-9) 0.0 else sum / denom
        }

        var bestLag = 0
        var bestVal = 0.0
        for (lag in minLag..maxLag) {
            if (corr[lag] > bestVal) {
                bestVal = corr[lag]
                bestLag = lag
            }
        }
        if (bestLag == 0 || bestVal < 0.32) return 0.0

        // 배음 때문에 2·3배 주기의 골이 더 클 수 있다. 최대값의 90% 이상이면 더 짧은 주기를 고른다.
        val threshold = bestVal * 0.90
        var chosen = bestLag
        for (lag in minLag until bestLag) {
            if (corr[lag] >= threshold &&
                corr[lag] >= corr[lag - 1] &&
                corr[lag] >= corr[lag + 1]
            ) {
                chosen = lag
                break
            }
        }

        // 포물선 보정으로 소수점 정밀도를 올린다.
        var refined = chosen.toDouble()
        if (chosen > minLag && chosen < maxLag) {
            val y0 = corr[chosen - 1]
            val y1 = corr[chosen]
            val y2 = corr[chosen + 1]
            val denom = 2 * (2 * y1 - y0 - y2)
            if (abs(denom) > 1e-9) {
                refined = chosen + (y2 - y0) / denom
            }
        }
        if (refined <= 0.0) return 0.0
        return SAMPLE_RATE.toDouble() / refined
    }
}
