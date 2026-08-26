package com.ccko.pikxplus.shared.utils
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.annotation.NonNull
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import java.lang.ref.WeakReference
/**
 * Color adjustment controller for ImageView and SSIV. 
 * Maintains state so individual adjustments don't overwrite each other.
 */
class ImgClrAdjst public constructor(
  imageView: ImageView?,
  ssiv: SubsamplingScaleImageView?
) {
  private val imageViewRef = WeakReference(imageView)
  private val ssivRef = WeakReference(ssiv)
  var brightness = 0f; private set   // -1..1
  var contrast = 1f; private set     // 0..2
  var saturation = 1f; private set   // 0..2
  var listener: ((Float, Float, Float) -> Unit)? = null
  @Volatile
  private var currentAnimator: ValueAnimator? = null
  // Secondary constructors
  constructor(@NonNull imageView: ImageView) : this(imageView, null)
  constructor(@NonNull ssiv: SubsamplingScaleImageView) : this(null, ssiv)
  // ----- Individual Adjustments -------
  fun setBrightness(brightness: Float) {
    cancelAnimation()
    this.brightness = clamp(brightness, -1f, 1f)
    applyAdjustments()
    notifyListener()
  }
  fun setContrast(contrast: Float) {
    cancelAnimation()
    this.contrast = clamp(contrast, 0f, 2f)
    applyAdjustments()
    notifyListener()
  }
  fun setSaturation(saturation: Float) {
    cancelAnimation()
    this.saturation = clamp(saturation, 0f, 2f)
    applyAdjustments()
    notifyListener()
  }
  fun adjustBrightness(delta: Float) = setBrightness(brightness + delta)
  fun adjustContrast(delta: Float) = setContrast(contrast + delta)
  fun adjustSaturation(delta: Float) = setSaturation(saturation + delta)
  fun autoEnhance(durationMs: Long = 100) {
    cancelAnimation()
    val startBrightness = this.brightness
    val startContrast = this.contrast
    val startSaturation = this.saturation
    val targetBrightness = 0.03f
    val targetContrast = 1.25f
    val targetSaturation = 1.20f
    currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
      this.duration = durationMs
      interpolator = DecelerateInterpolator(1.5f)
      addUpdateListener { anim ->
        val t = anim.animatedFraction
        brightness = startBrightness + (targetBrightness - startBrightness) * t
        contrast = startContrast + (targetContrast - startContrast) * t
        saturation = startSaturation + (targetSaturation - startSaturation) * t
        applyAdjustments()
      }
      addListener(object : AnimatorListenerAdapter() {
          override fun onAnimationEnd(animation: Animator) {
            currentAnimator = null
            notifyListener()
          }
      })
      start()
    }
  }
  fun reset(durationMs: Long = 250) {
    cancelAnimation()
    if (!hasAdjustments()) {
      applyAdjustments()
      notifyListener()
      return
    }
    val startBrightness = this.brightness
    val startContrast = this.contrast
    val startSaturation = this.saturation
    currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
      this.duration = durationMs
      interpolator = DecelerateInterpolator(1.5f)
      addUpdateListener { anim ->
        val t = anim.animatedFraction
        brightness = startBrightness + (0f - startBrightness) * t
        contrast = startContrast + (1f - startContrast) * t
        saturation = startSaturation + (1f - startSaturation) * t
        applyAdjustments()
      }
      addListener(object : AnimatorListenerAdapter() {
          override fun onAnimationEnd(animation: Animator) {
            // hard-clear filter to avoid float drift
            imageViewRef.get()?.colorFilter = null
            ssivRef.get()?.setColorFilter(null)// = null
            currentAnimator = null
            notifyListener()
          }
      })
      start()
    }
  }
  private fun cancelAnimation() {
    currentAnimator?.cancel()
    currentAnimator = null
  }
  fun hasAdjustments(): Boolean = brightness != 0f || contrast != 1f || saturation != 1f
  private fun notifyListener() {
    listener?.invoke(brightness, contrast, saturation)
  }
  // ----- Private Implementation ----------------------------
  private fun applyAdjustments() {
    val iv = imageViewRef.get()
    val ssiv = ssivRef.get()
    if (iv == null && ssiv == null) return
    if (!hasAdjustments()) {
      iv?.colorFilter = null
      ssiv?.setColorFilter(null) //= null
      return
    }
    val cm = ColorMatrix().apply { setSaturation(saturation) }
    val b = brightness * 255f
    val brightnessMx = ColorMatrix(floatArrayOf(
        1f, 0f, 0f, 0f, b,
        0f, 1f, 0f, 0f, b,
        0f, 0f, 1f, 0f, b,
        0f, 0f, 0f, 1f, 0f
    ))
    cm.postConcat(brightnessMx)
    val scale = contrast
    val translate = (1f - contrast) * 127.5f
    val contrastMx = ColorMatrix(floatArrayOf(
        scale, 0f, 0f, 0f, translate,
        0f, scale, 0f, 0f, translate,
        0f, 0f, scale, 0f, translate,
        0f, 0f, 0f, 1f, 0f
    ))
    cm.postConcat(contrastMx)
    val filter = ColorMatrixColorFilter(cm)
    iv?.colorFilter = filter
    ssiv?.setColorFilter(filter) //= filter
  }
  private fun clamp(value: Float, min: Float, max: Float): Float = value.coerceIn(min, max)
  fun release() {
    cancelAnimation()
    imageViewRef.clear()
    ssivRef.clear()
    listener = null
  }
}
