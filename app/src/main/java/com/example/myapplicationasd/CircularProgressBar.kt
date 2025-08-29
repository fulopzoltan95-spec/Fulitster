package com.example.compose

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class CircularProgressBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var progress: Float = 0f // 0..360f (szög)
        set(value) {
            field = value
            invalidate()
        }

    private var glowRadius = 38f

    private val neonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D0529C")    // #FF3EF6
        style = Paint.Style.STROKE
        strokeWidth = 24f
        setShadowLayer(glowRadius, 0f, 0f, Color.parseColor("#BBFF3EF6"))
        strokeCap = Paint.Cap.ROUND
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 24f
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, neonPaint)
        startGlowPulse()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = width.coerceAtMost(height).toFloat()
        val centerX = width / 2f
        val centerY = height / 2f

        // A legnagyobb glow sugarával beljebb húzzuk mindkét kört!
        val margin = 44f + neonPaint.strokeWidth / 2f + 4f // 44f = max glow sugár

        val radius = size / 2f - margin

        // Háttér körív (NEM pulzál)
        canvas.drawArc(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius,
            0f,
            360f,
            false,
            backgroundPaint
        )

        // Neon progress (CSAK ez pulzál, shadowLayerrel)
        canvas.drawArc(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius,
            -90f,
            progress,
            false,
            neonPaint
        )
    }

    // Pulzáló neon glow (csak a progress körre!)
    private fun startGlowPulse() {
        val animator = ValueAnimator.ofFloat(32f, 44f).apply {
            duration = 900
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                glowRadius = it.animatedValue as Float
                neonPaint.setShadowLayer(glowRadius, 0f, 0f, Color.parseColor("#BBFF3EF6"))
                invalidate()
            }
        }
        animator.start()
    }
}
