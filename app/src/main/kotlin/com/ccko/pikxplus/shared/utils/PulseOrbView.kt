package com.ccko.pikxplus.shared.utils
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.sin
/**
 * A calming, breathing orb loader that replaces the ProgressBar. Three concentric circles
 * pulse in a staggered wave, creating a gentle, meditative loading animation.
 *
 * Usage: <com.ccko.pikxplus.shared.PulseOrbView ... /> Call start() / stop() to control animation.
 */
class PulseOrbView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
View(context, attrs, defStyleAttr), Choreographer.FrameCallback {
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
  private val circles = mutableListOf<CirclePulse>()
  private var running = false
  private var lastFrameNanos = 0L
  private val choreographer = Choreographer.getInstance()
  // Animation constants
  private val baseRadius = 8f   // smallest circle radius in dp
  private val maxPulse = 12f  // how much the radius expands (dp)
  private val speed = 1.5f // oscillations per second
  private val stagger = 0.4f // phase offset between circles (0..1)
  init {
    setWillNotDraw(false)
    // Three circles with different phase offsets
    circles.add(CirclePulse(phase = 0.0f))
    circles.add(CirclePulse(phase = stagger))
    circles.add(CirclePulse(phase = stagger * 2))
  }
  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    // Recalculate the gradient for the largest circle when size changes
    updateGradient()
  }
  private fun updateGradient() {
    val maxR = dpToPx(baseRadius + maxPulse) * 2f
    if (width > 0 && height > 0) {
      paint.shader =
      RadialGradient(
        width / 2f,
        height / 2f,
        maxR,
        intArrayOf(Color.WHITE, Color.TRANSPARENT),
        null,
        Shader.TileMode.CLAMP
      )
    }
  }
  override fun onDraw(canvas: Canvas) {
    canvas.drawColor(Color.TRANSPARENT)
    val cx = width / 2f
    val cy = height / 2f
    val now = if (lastFrameNanos > 0) lastFrameNanos else System.nanoTime()
    val t = now / 1_000_000_000f
    for (circle in circles) {
      val phase = circle.phase.toFloat()
      // FIX: convert sin result to Float
      val scale = (sin((t * speed + phase) * Math.PI * 2).toFloat() + 1f) / 2f
      val radius = dpToPx(baseRadius + maxPulse * scale)
      val alpha = (0.3f + 0.7f * scale).coerceIn(0f, 1f)
      // val angle = t * 0.5f + circle.phase * 2f  // slow rotation
      // val shiftX = (kotlin.math.sin(angle) * baseRadius).toFloat()
      // val shiftY = (kotlin.math.cos(angle) * baseRadius).toFloat()
      paint.alpha = (alpha * 255).toInt()
      canvas.drawCircle(cx, cy, radius, paint)
      // canvas.drawCircle(cx + shiftX, cy + shiftY, radius, paint)
    }
  }
  fun start() {
    if (running) return
    running = true
    lastFrameNanos = 0L
    choreographer.postFrameCallback(this)
  }
  fun stop() {
    if (!running) return
    running = false
    choreographer.removeFrameCallback(this)
  }
  override fun doFrame(frameTimeNanos: Long) {
    if (!running) return
    lastFrameNanos = frameTimeNanos
    invalidate()
    choreographer.postFrameCallback(this)
  }
  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    stop()
  }
  private data class CirclePulse(val phase: Float)
  private fun dpToPx(dp: Float): Float {
    return dp * resources.displayMetrics.density
  }
}
