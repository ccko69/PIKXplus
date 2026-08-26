package com.ccko.pikxplus.shared.utils
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.PopupWindow
import android.view.ViewPropertyAnimator
import android.animation.TimeInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
/** Lambda for setting up popup content */
typealias SetupCallback = (View) -> Unit
/**
 * Unified manager for all floating windows/popups in the app. Handles: speed control, playlist,
 * volume/brightness overlays, settings, etc.
 */
class FloatWin(private val context: Context, private val anchorView: View) {
  private var currentPopup: PopupWindow? = null
  private var onDismissCallback: Runnable? = null
  private val autoHideHandler = Handler(Looper.getMainLooper())
  private var autoHideRunnable: Runnable? = null
  private var exitAnimator: ViewPropertyAnimator? = null
  // ---------- animation configuration ----------
  data class AnimationConfig(
    val duration: Long = 300L,
    val interpolator: TimeInterpolator = AccelerateInterpolator(),
    val type: AnimationType = AnimationType.FADE
  )
  enum class AnimationType {
    FADE,
    SLIDE_UP,
    SLIDE_DOWN,
    SLIDE_LEFT,
    SLIDE_RIGHT,
    SCALE,
    NONE
  }
  // Global animation settings (overridable per call)
  var enterAnimation: AnimationConfig = AnimationConfig()
  var exitAnimation:  AnimationConfig = AnimationConfig()
  companion object {
    const val TYPE_SPEED = 0
    const val TYPE_PLAYLIST = 1
    const val TYPE_VOLUME = 2
    const val TYPE_BRIGHTNESS = 3
    const val TYPE_SETTINGS = 4
    const val TYPE_TIMER = 5
  }
  // ---------- typed presets ----------
  /** Show a floating window with preset type configurations */
  fun showTyped(type: Int, layoutResId: Int, setupCallback: SetupCallback) {
    val metrics = getDisplayMetrics()
    val margin16 = dpToPx(16)
    val margin0 = dpToPx(0)
    val seekHorizH = dpToPx(170)
    val seekHorizV = dpToPx(40)
    when (type) {
      TYPE_SPEED ->
      show(
        layoutResId,
        dpToPx(350),
        dpToPx(80),
        dpToPx(120),
        margin0,
        Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        setupCallback,
        false,
        animConfig = enterAnimation
      )
      TYPE_PLAYLIST -> {
        val playlistWidth = (metrics.widthPixels * 0.65f).toInt()
        val playlistHeight = (metrics.heightPixels * 0.80f).toInt()
        show(
          layoutResId,
          playlistWidth,
          playlistHeight,
          dpToPx(80),
          dpToPx(16),
          Gravity.TOP or Gravity.END,
          setupCallback,
          false,
          animConfig = enterAnimation
        )
      }
      TYPE_VOLUME -> {
        // Thin vertical bar on right side
        show(
          layoutResId,
          seekHorizV,
          seekHorizH,
          dpToPx(0),
          dpToPx(28),
          Gravity.START or Gravity.CENTER_VERTICAL,
          setupCallback,
          true,
          focusable = false,
          animConfig = enterAnimation
        )
      }
      TYPE_BRIGHTNESS -> {
        // Thin vertical bar on left side
        show(
          layoutResId,
          seekHorizV,
          seekHorizH,
          dpToPx(0),
          dpToPx(28),
          Gravity.END or Gravity.CENTER_VERTICAL,
          setupCallback,
          true,
          focusable = false,
          animConfig = enterAnimation
        )
      }
      TYPE_SETTINGS -> {
        // Large centered window
        // test ...
        // val settingsWidth = (metrics.widthPixels * 0.60f).toInt()
        // val settingsHeight = (metrics.heightPixels * 0.75f).toInt()
        show(
          layoutResId,
          // settingsWidth,
          // settingsHeight,
          dpToPx(300),
          dpToPx(250),
          dpToPx(120),
          margin0,
          Gravity.TOP or Gravity.CENTER_HORIZONTAL,
          setupCallback,
          false,
          animConfig = enterAnimation
        )
      }
      TYPE_TIMER -> {
        // Thin horizontal bar on Center
        val timerWidth = (metrics.widthPixels * 1f).toInt()
        show(layoutResId, timerWidth, dpToPx(40), dpToPx(0), 0, Gravity.CENTER, setupCallback, true, animConfig = enterAnimation)
      }
    }
  }
  /** Show with custom parameters AND optional animation override */
  fun show(
    layoutResId: Int,
    width: Int,
    height: Int,
    yOffset: Int,
    xOffset: Int,
    gravity: Int,
    setupCallback: SetupCallback,
    autoHide: Boolean,
    focusable: Boolean = true,
    animConfig: AnimationConfig? = null
  ) {
    // Cancel any ongoing exit animation
    exitAnimator?.cancel()
    dismissImmediate()  // dismiss without animation
    val content = LayoutInflater.from(context).inflate(layoutResId, null)
    content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    setupCallback(content)
    currentPopup = PopupWindow(content, width, height, true).apply {
      isFocusable = focusable
      isTouchable = true        // keep touch enabled
      isOutsideTouchable = true
      setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
      isClippingEnabled = false
      setOnDismissListener {
        cancelAutoHide()
        onDismissCallback?.run()
        currentPopup = null
      }
    }
    // Apply enter animation
    val config = animConfig ?: enterAnimation
    if (config.type != AnimationType.NONE) {
      // Set initial state
      applyPreShowState(content, config)
      // Show (invisible initially because alpha/translation hides it)
      currentPopup?.showAtLocation(anchorView.rootView, gravity, xOffset, yOffset)
      // Animate to final state
      content.animate()
      .setDuration(config.duration)
      .setInterpolator(config.interpolator)
      .withEndAction(null)
      .apply { resetToFinalState(this, config) }
      .start()
    } else {
      currentPopup?.showAtLocation(anchorView.rootView, gravity, xOffset, yOffset)
    }
    if (autoHide) {
      scheduleAutoHide(5000)
    }
  }
  /** Dismiss with the globally configured exit animation */
  fun dismiss() {
    if (exitAnimation.type != AnimationType.NONE) {
      dismissAnimated(exitAnimation)
    } else {
      dismissImmediate()
    }
  }
  /** Dismiss with a custom animation (or use default exit animation) */
  fun dismissAnimated(config: AnimationConfig = exitAnimation) {
    val popup = currentPopup ?: return
    val content = popup.contentView ?: return
    // If an exit animation is already running, let it finish
    //  if (exitAnimator?.isRunning == true) return
    exitAnimator = content.animate()
    .setDuration(config.duration)
    .setInterpolator(config.interpolator)
    .withEndAction { dismissImmediate() }
    .apply { applyExitState(this, config) }
    exitAnimator?.start()
  }
  /** Hard dismiss – no animation */
  private fun dismissImmediate() {
    cancelAutoHide()
    exitAnimator?.cancel()
    exitAnimator = null
    currentPopup?.let {
      if (it.isShowing) it.dismiss()
    }
    currentPopup = null
    autoHideHandler.removeCallbacksAndMessages(null)
  }
  /** Update existing popup content without dismissing */
  fun updateContent(setupCallback: SetupCallback) {
    val popup = currentPopup
    if (popup != null && popup.isShowing) {
      val content = popup.contentView
      if (content != null) {
        setupCallback(content)
        // Reset auto-hide timer on update
        if (autoHideRunnable != null) {
          scheduleAutoHide(5000)
        }
      }
    }
  }
  /** Show or update if already showing (useful for volume/brightness) */
  fun showOrUpdate(type: Int, layoutResId: Int, setupCallback: SetupCallback) {
    if (isShowing()) {
      updateContent(setupCallback)
    } else {
      showTyped(type, layoutResId, setupCallback)
    }
  }
  fun isShowing(): Boolean = currentPopup?.isShowing == true
  fun setOnDismissCallback(callback: Runnable) {
    this.onDismissCallback = callback
  }
  /** Reset auto-hide timer (call this when user interacts with overlay) */
  fun resetAutoHideTimer() {
    if (autoHideRunnable != null) {
      scheduleAutoHide(4000)
    }
  }
  // ---------- animation helpers ----------
  private fun applyPreShowState(view: View, config: AnimationConfig) {
    view.alpha = 0f
    view.scaleX = 1f
    view.scaleY = 1f
    view.translationX = 0f
    view.translationY = 0f
    when (config.type) {
      AnimationType.FADE -> {} // already alpha=0
      AnimationType.SLIDE_UP -> view.translationY = 100f  // slide from below
      AnimationType.SLIDE_DOWN -> view.translationY = -100f
      AnimationType.SLIDE_LEFT -> view.translationX = 100f
      AnimationType.SLIDE_RIGHT -> view.translationX = -100f
      AnimationType.SCALE -> {
        view.scaleX = 0.8f
        view.scaleY = 0.8f
      }
      AnimationType.NONE -> {}
    }
  }
  private fun resetToFinalState(animator: ViewPropertyAnimator, config: AnimationConfig) {
    animator.alpha(1f)
    .scaleX(1f)
    .scaleY(1f)
    .translationX(0f)
    .translationY(0f)
  }
  private fun applyExitState(animator: ViewPropertyAnimator, config: AnimationConfig) {
    when (config.type) {
      AnimationType.FADE -> animator.alpha(0f)
      AnimationType.SLIDE_UP -> animator.translationY(-100f).alpha(0f)
      AnimationType.SLIDE_DOWN -> animator.translationY(100f).alpha(0f)
      AnimationType.SLIDE_LEFT -> animator.translationX(-100f).alpha(0f)
      AnimationType.SLIDE_RIGHT -> animator.translationX(100f).alpha(0f)
      AnimationType.SCALE -> animator.scaleX(0.8f).scaleY(0.8f).alpha(0f)
      AnimationType.NONE -> {} // never called
    }
  }
  private fun scheduleAutoHide(delayMs: Long) {
    cancelAutoHide()
    autoHideRunnable = Runnable { dismiss() }  // now uses animated dismiss
    autoHideHandler.postDelayed(autoHideRunnable!!, delayMs)
  }
  private fun cancelAutoHide() {
    autoHideRunnable?.let { autoHideHandler.removeCallbacks(it) }
    autoHideRunnable = null
  }
  @Suppress("DEPRECATION")
  private fun getDisplayMetrics(): android.util.DisplayMetrics {
    val metrics = android.util.DisplayMetrics()
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    wm.defaultDisplay.getMetrics(metrics)
    return metrics
  }
  fun dpToPx(dp: Int): Int {
    val metrics = getDisplayMetrics()
    val density = metrics.density
    return kotlin.math.round(dp * density).toInt()
  }
}
