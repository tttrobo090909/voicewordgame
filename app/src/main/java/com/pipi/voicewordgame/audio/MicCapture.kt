package com.pipi.voicewordgame.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 마이크 하나를 계속 열어 두고
 *  - 실시간 음량(dBFS)을 재고
 *  - 최근 3초 PCM을 돌려주며(화자 특징 계산용)
 *  - API 33+ 에서는 같은 소리를 파이프로 SpeechRecognizer 에게 먹여 준다.
 * 이렇게 하면 AudioRecord 와 SpeechRecognizer 가 마이크를 동시에 잡는 충돌을 피한다.
 */
class MicCapture(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 16000
        private const val RING_SECONDS = 3
        private const val CHUNK = 1024
    }

    private val ring = ShortArray(SAMPLE_RATE * RING_SECONDS)
    private val ringLock = Any()
    private var ringPos = 0
    private var ringFilled = 0

    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    var levelDb: Float = -120f
        private set

    @Volatile
    var peakDb: Float = -120f
        private set

    @Volatile
    var isCapturing: Boolean = false
        private set

    private var writer: PipeWriter? = null
    private var pipeReadEnd: ParcelFileDescriptor? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        if (!hasPermission()) return false
        val minBuf = try {
            AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        } catch (e: Exception) {
            -1
        }
        if (minBuf <= 0) return false
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf * 2, CHUNK * 4)
            )
        } catch (e: Exception) {
            null
        } ?: return false

        try {
            rec.startRecording()
        } catch (e: Exception) {
            rec.release()
            return false
        }
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            rec.release()
            return false
        }

        record = rec
        running = true
        isCapturing = true
        peakDb = -120f
        thread = Thread({ loop(rec) }, "mic-capture").also {
            it.isDaemon = true
            it.start()
        }
        return true
    }

    fun stop() {
        running = false
        isCapturing = false
        closePipeSource()
        val t = thread
        thread = null
        val rec = record
        record = null
        try {
            if (rec != null) {
                try {
                    rec.stop()
                } catch (e: Exception) {
                    // 이미 멈춘 경우 무시
                }
                rec.release()
            }
        } catch (e: Exception) {
            // release 실패는 무시
        }
        try {
            t?.join(300)
        } catch (e: Exception) {
            // ignore
        }
        levelDb = -120f
    }

    fun resetPeak() {
        peakDb = -120f
    }

    /** 최근 [seconds] 초 PCM(시간순). */
    fun recentPcm(seconds: Float): ShortArray {
        val want = min(ring.size, (SAMPLE_RATE * seconds).toInt())
        synchronized(ringLock) {
            val take = min(want, ringFilled)
            val out = ShortArray(take)
            val start = (ringPos - take + ring.size) % ring.size
            for (i in 0 until take) {
                out[i] = ring[(start + i) % ring.size]
            }
            return out
        }
    }

    /** SpeechRecognizer 에 먹일 파이프의 읽기 끝. API 33 미만이면 null. */
    fun openPipeSource(): ParcelFileDescriptor? {
        if (!running) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return try {
            closePipeSource()
            val pipe = ParcelFileDescriptor.createPipe()
            val readEnd = pipe[0]
            pipeReadEnd = readEnd
            val out = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
            val w = PipeWriter(out)
            writer = w
            w.start()
            readEnd
        } catch (e: Exception) {
            null
        }
    }

    fun closePipeSource() {
        val w = writer
        writer = null
        try {
            w?.close()
        } catch (e: Exception) {
            // ignore
        }
        val readEnd = pipeReadEnd
        pipeReadEnd = null
        try {
            readEnd?.close()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun loop(rec: AudioRecord) {
        val buf = ShortArray(CHUNK)
        while (running) {
            val n = try {
                rec.read(buf, 0, buf.size)
            } catch (e: Exception) {
                -1
            }
            if (n < 0) break
            if (n == 0) continue

            var sum = 0.0
            for (i in 0 until n) {
                val v = buf[i].toInt()
                sum += v.toDouble() * v
            }
            val rms = sqrt(sum / n)
            val db = (20.0 * log10(max(1.0, rms) / 32768.0)).toFloat()
            levelDb = db
            if (db > peakDb) peakDb = db

            synchronized(ringLock) {
                for (i in 0 until n) {
                    ring[ringPos] = buf[i]
                    ringPos = (ringPos + 1) % ring.size
                }
                ringFilled = min(ring.size, ringFilled + n)
            }

            val w = writer
            if (w != null) {
                val bytes = ByteArray(n * 2)
                var bi = 0
                for (i in 0 until n) {
                    val v = buf[i].toInt()
                    bytes[bi++] = (v and 0xFF).toByte()
                    bytes[bi++] = ((v shr 8) and 0xFF).toByte()
                }
                w.offer(bytes)
            }
        }
        isCapturing = false
    }

    /** 등록·소음 측정용 단발 녹음. 기존 캡처가 있으면 먼저 멈춘다. */
    @SuppressLint("MissingPermission")
    fun recordOnce(seconds: Float): ShortArray? {
        if (!hasPermission()) return null
        val wasRunning = running
        if (wasRunning) stop()
        val total = (SAMPLE_RATE * seconds).toInt()
        val minBuf = try {
            AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        } catch (e: Exception) {
            -1
        }
        if (minBuf <= 0) return null
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf * 2, CHUNK * 4)
            )
        } catch (e: Exception) {
            null
        } ?: return null

        val out = ShortArray(total)
        var filled = 0
        try {
            rec.startRecording()
            val buf = ShortArray(CHUNK)
            while (filled < total) {
                val n = rec.read(buf, 0, min(CHUNK, total - filled))
                if (n <= 0) break
                System.arraycopy(buf, 0, out, filled, n)
                filled += n
                var sum = 0.0
                for (i in 0 until n) {
                    val v = buf[i].toInt()
                    sum += v.toDouble() * v
                }
                val rms = sqrt(sum / n)
                levelDb = (20.0 * log10(max(1.0, rms) / 32768.0)).toFloat()
            }
        } catch (e: Exception) {
            return null
        } finally {
            try {
                rec.stop()
            } catch (e: Exception) {
                // ignore
            }
            rec.release()
        }
        return if (filled > SAMPLE_RATE / 4) out.copyOf(filled) else null
    }

    private class PipeWriter(private val out: OutputStream) {
        private val queue = ArrayBlockingQueue<ByteArray>(64)

        @Volatile
        private var open = true
        private var thread: Thread? = null

        fun start() {
            thread = Thread({
                while (open) {
                    val chunk = try {
                        queue.take()
                    } catch (e: InterruptedException) {
                        break
                    }
                    try {
                        out.write(chunk)
                        out.flush()
                    } catch (e: Exception) {
                        open = false
                        break
                    }
                }
                try {
                    out.close()
                } catch (e: Exception) {
                    // ignore
                }
            }, "mic-pipe").also {
                it.isDaemon = true
                it.start()
            }
        }

        fun offer(bytes: ByteArray) {
            if (!open) return
            if (!queue.offer(bytes)) {
                queue.poll()
                queue.offer(bytes)
            }
        }

        fun close() {
            open = false
            queue.clear()
            try {
                thread?.interrupt()
            } catch (e: Exception) {
                // ignore
            }
            // 쓰기 스레드가 파이프에 막혀 있으면 닫아서 풀어 준다.
            try {
                out.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
