package com.pipi.voicewordgame.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 병아리 걷기 애니메이션의 순수 계산(v2.2).
 *
 * 화면(View)에 값을 넣는 일은 MainActivity 가 하고, 여기서는 "진행도 → 자세"만 계산한다.
 * - 첫 화면: 좌우 ±amp 이동 + 이동 방향에 맞춘 좌우 반전(scaleX = ±1) + 바운스 + 기울기.
 * - 게임 화면: 왼쪽 끝 → 오른쪽 끝으로 7.7초 동안 걷는다(8초 제한의 시각 표시).
 */
object ChickWalk {

    /** 한 문제의 제한 시간. */
    const val LIMIT_MS = 8000L

    /** 병아리가 왼쪽 끝에서 오른쪽 끝까지 걷는 시간(제한 8초 중 7.7초). */
    const val WALK_MS = 7700L

    /** 첫 화면 좌우 살랑살랑 한 왕복에 들어가는 걸음 수. */
    const val SWAY_STEPS = 4f

    /** 게임 화면 타이머 병아리의 전체 걸음 수(약 0.32초에 한 걸음). */
    const val TIMER_STEPS = 24f

    /** 걸을 때 최대 기울기(도). */
    const val TILT_MAX_DEG = 5f

    private const val TWO_PI = 2.0 * PI

    private fun progressOf(p: Float): Float = when {
        p.isNaN() -> 0f
        p < 0f -> 0f
        p > 1f -> 1f
        else -> p
    }

    /** 첫 화면: 좌우로 ±[amplitudePx] 이동(사인 곡선). */
    fun swayX(progress: Float, amplitudePx: Float): Float {
        val p = progressOf(progress)
        return (sin(TWO_PI * p) * amplitudePx).toFloat()
    }

    /** 이동 방향: 오른쪽(+1) / 왼쪽(-1). scaleX 에 그대로 넣어 몸을 돌린다. */
    fun facingForSway(progress: Float): Float {
        val p = progressOf(progress)
        return if (cos(TWO_PI * p) >= 0.0) 1f else -1f
    }

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

    /** 타이머 진행도: 경과 시간 → 0..1 (7.7초에 오른쪽 끝). */
    fun timerProgress(elapsedMs: Long): Float =
        progressOf(elapsedMs.toFloat() / WALK_MS)

    /** 타이머 병아리의 왼쪽 끝 → 오른쪽 끝 위치. */
    fun walkX(progress: Float, travelPx: Float): Float = progressOf(progress) * travelPx
}
