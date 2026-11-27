package com.pipi.voicewordgame.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.pipi.voicewordgame.R

/**
 * 정답·오답 효과음 (v2.10 — `들어봐요!` v1.2 의 효과음 4번).
 *
 * - `res/raw/sfx_correct.wav`(0.6초) · `res/raw/sfx_wrong.wav`(0.4초)를 [SoundPool] 에 한 번만 올려 두고 짧게 재생한다.
 * - SoundPool 은 MediaPlayer 와 달리 배경음·TTS 를 멈추지 않고 그 위에 그대로 겹쳐 난다.
 * - 소리를 올리는 데는 몇십 ms 걸린다. 아직 다 올라오지 않은 소리를 부탁받으면 기억해 두었다가
 *   다 올라오면 바로 한 번 재생한다(첫 정답이 조용히 지나가지 않게).
 * - 재생은 USAGE_GAME — 음성인식(DIRECT 마이크 입력)과는 겹치지 않는 출력 스트림이라
 *   마이크로 들어가는 소리와 충돌하지 않는다. 정답·오답·시간 초과는 음성인식을 멈춘 뒤에 울린다.
 * - 화면이 닫힐 때 [release] 로 정리한다.
 */
class SfxPlayer(context: Context) {

    enum class Sound { CORRECT, WRONG }

    companion object {
        /** 효과음 볼륨 — TTS·배경음 위에서도 또렷하게 들리는 크기. */
        const val VOLUME = 1.0f

        /** 동시에 낼 수 있는 효과음 수(정답음이 아직 날 때 다음 문제를 풀어도 겹쳐 난다). */
        const val MAX_STREAMS = 2

        /** 출력 용도(테스트가 읽는다) — 게임 효과음이라 음성인식 마이크 입력과 별개다. */
        const val USAGE = AudioAttributes.USAGE_GAME
    }

    private val appContext = context.applicationContext

    private var pool: SoundPool? = runCatching {
        SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(USAGE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }.getOrNull()

    private var correctId: Int = 0
    private var wrongId: Int = 0

    /** 다 올라온 소리(재생할 수 있는 id). */
    private val loaded = mutableSetOf<Int>()

    /** 아직 올라오지 않아 미뤄 둔 소리. */
    private var pending: Sound? = null

    /** 지금까지 부탁받은 소리(테스트가 읽는다). */
    internal val played: MutableList<Sound> = java.util.Collections.synchronizedList(mutableListOf<Sound>())

    val isReady: Boolean
        get() = pool != null

    init {
        val soundPool = pool
        if (soundPool != null) {
            soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    loaded.add(sampleId)
                    val waiting = pending
                    if (waiting != null && sampleId == idOf(waiting)) {
                        pending = null
                        start(waiting)
                    }
                }
            }
            correctId = runCatching { soundPool.load(appContext, R.raw.sfx_correct, 1) }.getOrDefault(0)
            wrongId = runCatching { soundPool.load(appContext, R.raw.sfx_wrong, 1) }.getOrDefault(0)
        }
    }

    /** 정답·오답 결과에 맞는 효과음을 낸다. */
    fun play(sound: Sound) {
        played.add(sound)
        if (start(sound)) return
        // 아직 올라오지 않았으면 올라온 뒤에 한 번 재생한다.
        pending = sound
    }

    fun playCorrect() = play(Sound.CORRECT)

    fun playWrong() = play(Sound.WRONG)

    private fun idOf(sound: Sound): Int =
        if (sound == Sound.CORRECT) correctId else wrongId

    /** 실제로 재생을 걸었으면 true. 소리가 아직 없거나 기기가 받지 않으면 false. */
    private fun start(sound: Sound): Boolean {
        val id = idOf(sound)
        if (id == 0) return false
        val stream = runCatching {
            pool?.play(id, VOLUME, VOLUME, 1, 0, 1f)
        }.getOrNull() ?: 0
        return stream != 0
    }

    fun release() {
        runCatching { pool?.release() }
        pool = null
        loaded.clear()
        pending = null
    }

    // ---------------------------------------------------------------- 테스트용 훅

    internal val poolForTest: SoundPool?
        get() = pool
}
