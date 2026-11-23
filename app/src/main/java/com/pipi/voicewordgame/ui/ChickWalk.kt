package com.pipi.voicewordgame.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 병아리 걷기 애니메이션의 순수 계산(v2.3).
 *
 * 화면(View)에 값을 넣는 일은 MainActivity 가 하고, 여기서는 "진행도 → 자세"만 계산한다.
 * - 첫 화면: 좌우로 잔잔히 흔들리되 몸은 항상 정방향(scaleX = 1) — 명암이 뒤집히지 않는다.
 * - 게임 화면: 왼쪽 끝 → 오른쪽 끝으로 제한시간에 맞춰 걷는다(제한시간의 시각 표시).
 */
object ChickWalk {

    /** 설정 기본값(9초) — 제한시간 설정이 없을 때 쓰는 한 문제 제한 시간. */
    const val DEFAULT_LIMIT_MS = 9000L

    /** 첫 화면 좌우 살랑살랑 한 왕복에 들어가는 걸음 수. */
    const val SWAY_STEPS = 4f

    /** 게임 화면 타이머 병아리의 전체 걸음 수. */
    const val TIMER_STEPS = 24f

    /** 게임 화면 타이머 병아리가 걸을 때 최대 기울기(도). */
    const val TILT_MAX_DEG = 5f

    /** 첫 화면 병아리의 기울기(도) — v2.3은 훨씬 잔잔하게. */
    const val SWAY_TILT_MAX_DEG = 2.5f

    private const val TWO_PI = 2.0 * PI

    private fun progressOf(p: Float): Float = when {
        p.isNaN() -> 0f
        p < 0f -> 0f
        p > 1f -> 1f
        else -> p
    }

    /**
     * 제한시간 [limitMs] 안에서 병아리가 왼쪽 끝에서 오른쪽 끝까지 다 걷는 시간.
     * 제한시간의 0.3초 전(최소 90%)에 끝까지 걸어 시간 초과를 눈으로 알 수 있게 한다.
     */
    fun walkMs(limitMs: Long): Long {
        val limit = if (limitMs <= 0L) DEFAULT_LIMIT_MS else limitMs
        return (limit - 300L).coerceAtLeast((limit * 0.9f).toLong())
    }

    /** 첫 화면: 좌우로 ±[amplitudePx] 이동(사인 곡선). */
    fun swayX(progress: Float, amplitudePx: Float): Float {
        val p = progressOf(progress)
        return (sin(TWO_PI * p) * amplitudePx).toFloat()
    }

    /** 첫 화면 병아리는 항상 정방향이다(v2.3: 좌우 반전 없음). */
    fun swayScaleX(): Float = 1f

    /** 걷는 느낌의 위아래 바운스(0 ~ -[amplitudePx], 걸음마다 한 번 내려갔다 올라온다). */
    fun bounce(progress: Float, steps: Float, amplitudePx: Float): Float {
        val p = progressOf(progress)
        return (-abs(sin(PI * p * steps)) * amplitudePx).toFloat()
    }

    /** 걸음마다 살짝 기우는 각도(-[maxDeg] ~ +[maxDeg]). */
    fun tilt(progress: Float, steps: Float, maxDeg: Float): Float {
        val p = progressOf(progress)
        return (sin(TWO_PI * p * steps) * maxDeg).toFloat()
    }

    /** 타이머 진행도: 경과 시간 → 0..1 ([limitMs] 제한시간에 맞춘 걷기 시간에 오른쪽 끝). */
    fun timerProgress(elapsedMs: Long, limitMs: Long = DEFAULT_LIMIT_MS): Float =
        progressOf(elapsedMs.toFloat() / walkMs(limitMs).toFloat())

    /** 타이머 병아리의 왼쪽 끝 → 오른쪽 끝 위치. */
    fun walkX(progress: Float, travelPx: Float): Float = progressOf(progress) * travelPx
}
