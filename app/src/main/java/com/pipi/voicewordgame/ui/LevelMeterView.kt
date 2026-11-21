package com.pipi.voicewordgame.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.pipi.voicewordgame.R

/**
 * 실시간 음량 막대. 기준 소음선(주변 소음)을 점선으로 보여 주고,
 * 그보다 크면 민트색, 작으면 회색으로 칠한다.
 */
class LevelMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cream_deep)
        style = Paint.Style.FILL
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.coral_deep)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val quietColor = ContextCompat.getColor(context, R.color.ink_soft)
    private val loudColor = ContextCompat.getColor(context, R.color.mint_deep)

    @Volatile
    private var levelDb: Float = -90f

    @Volatile
    private var baselineDb: Float? = null

    private val rect = RectF()
    private val minDb = -70f
    private val maxDb = -5f

    fun setLevel(db: Float) {
        levelDb = db
        postInvalidateOnAnimation()
    }

    fun setBaseline(db: Float?) {
        baselineDb = db
        postInvalidateOnAnimation()
    }

    fun currentLevel(): Float = levelDb

    private fun ratio(db: Float): Float =
        ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val w = width.toFloat()
        val r = h / 2f
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, bgPaint)

        val base = baselineDb
        val above = base != null && levelDb >= base + 3f
        fillPaint.color = if (base == null || above) loudColor else quietColor
        val level = ratio(levelDb)
        if (level > 0.01f) {
            rect.set(0f, 0f, w * level, h)
            canvas.drawRoundRect(rect, r, r, fillPaint)
        }

        if (base != null && base > minDb) {
            val x = w * ratio(base)
            canvas.drawLine(x, 1f, x, h - 1f, linePaint)
        }
    }
}
