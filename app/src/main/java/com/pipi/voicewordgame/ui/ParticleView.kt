package com.pipi.voicewordgame.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** 정답을 맞혔을 때 하트·별 폭죽이 터지는 오버레이. */
class ParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private enum class Kind { HEART, STAR, DOT }

    private class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var rot: Float,
        var vr: Float,
        var size: Float,
        var color: Int,
        var kind: Kind,
        var life: Float,
        var maxLife: Float
    )

    private val particles = ArrayList<Particle>(48)
    private val colors = intArrayOf(
        0xFFFFB4A2.toInt(), 0xFFFFE8A3.toInt(), 0xFFFFD3E0.toInt(),
        0xFFAEDFF7.toInt(), 0xFFA8E6CF.toInt(), 0xFFD5C7F7.toInt()
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heartPath = Path()
    private val starPath = Path()
    private var lastFrameNanos = 0L
    private val random = Random(System.currentTimeMillis())

    private val ticker = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            if (lastFrameNanos == 0L) lastFrameNanos = now
            val dt = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameNanos = now
            step(dt)
            invalidate()
            if (particles.isNotEmpty()) postOnAnimation(this)
        }
    }

    init {
        isClickable = false
        isFocusable = false
        buildShapes()
    }

    private fun buildShapes() {
        heartPath.reset()
        heartPath.moveTo(0f, 5f)
        heartPath.cubicTo(-8f, -7f, -17f, 4f, 0f, 15f)
        heartPath.cubicTo(17f, 4f, 8f, -7f, 0f, 5f)
        heartPath.close()

        starPath.reset()
        val outer = 7f
        val inner = 3f
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val angle = Math.toRadians(-90.0 + i * 36.0)
            val px = (cos(angle) * r).toFloat()
            val py = (sin(angle) * r).toFloat()
            if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
        }
        starPath.close()
    }

    fun celebrate(centerX: Float = width / 2f, centerY: Float = height * 0.40f) {
        if (width == 0 || height == 0) return
        val count = 30
        for (i in 0 until count) {
            val angle = -Math.PI / 2 + (random.nextFloat() - 0.5) * 2.4
            val speed = 380f + random.nextFloat() * 620f
            val kind = when (random.nextInt(3)) {
                0 -> Kind.HEART
                1 -> Kind.STAR
                else -> Kind.DOT
            }
            particles.add(
                Particle(
                    x = centerX + (random.nextFloat() - 0.5f) * width * 0.35f,
                    y = centerY + (random.nextFloat() - 0.5f) * 40f,
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed).toFloat() - 120f,
                    rot = random.nextFloat() * 360f,
                    vr = (random.nextFloat() - 0.5f) * 540f,
                    size = 10f + random.nextFloat() * 14f,
                    color = colors[random.nextInt(colors.size)],
                    kind = kind,
                    life = 1.5f + random.nextFloat() * 0.5f,
                    maxLife = 2.0f
                )
            )
        }
        lastFrameNanos = 0L
        postOnAnimation(ticker)
    }

    fun clear() {
        particles.clear()
        invalidate()
    }

    private fun step(dt: Float) {
        val gravity = 900f
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.vy += gravity * dt
            p.vx *= (1f - 0.6f * dt)
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rot += p.vr * dt
            p.life -= dt
            if (p.life <= 0f || p.y > height + 80f) it.remove()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (p in particles) {
            val alpha = (min(1f, p.life / 0.7f) * 255).toInt().coerceIn(0, 255)
            paint.color = p.color
            paint.alpha = alpha
            paint.style = Paint.Style.FILL
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.rot)
            val scale = p.size / 14f
            canvas.scale(scale, scale)
            when (p.kind) {
                Kind.HEART -> canvas.drawPath(heartPath, paint)
                Kind.STAR -> canvas.drawPath(starPath, paint)
                Kind.DOT -> canvas.drawCircle(0f, 0f, 5f, paint)
            }
            canvas.restore()
        }
    }
}
