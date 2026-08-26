package com.ccko.pikxplus.viewers.img
import android.app.Activity
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.viewpager2.widget.ViewPager2
import com.ccko.pikxplus.ux.settings.ImgSetDlg
import com.ccko.pikxplus.viewers.img.ImgVM
import com.ccko.pikxplus.viewers.img.VpAdpt
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
/**
 * Self-contained slideshow runner for the image viewer. Handles timing, transitions (fade, slide,
 * scale, combined), auto-rotation, and UI callbacks.
 */
class SlideShowCtrl(
  private val viewPager: ViewPager2,
  private val settings: ImgSetDlg,
  private val adapter: VpAdpt,
  private val viewModel: ImgVM,
  private val activity: Activity?,
  private val prefs: SharedPreferences,
  private val uiCallback: UiCallback
) {
  interface UiCallback {
    fun onSlideShowStarted()
    fun onSlideShowStopped()
  }
  private class SlideShow(private val cb: Callback, activity: Activity?) {
    interface Callback {
      fun onNextImage()
      fun onSlideShowStopped()
    }
    private val handler = Handler(Looper.getMainLooper())
    private val active = AtomicBoolean(false)
    private var intervalMs = 3000L
    private val activityRef: WeakReference<Activity>? = activity?.let { WeakReference(it) }
    private val tick =
    object : Runnable {
      override fun run() {
        if (!active.get()) return
        cb.onNextImage()
        handler.postDelayed(this, intervalMs)
      }
    }
    fun start(ms: Long) {
      intervalMs = kotlin.math.max(100L, ms)
      activityRef?.get()?.let { a ->
        a.runOnUiThread { a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
      }
      if (active.compareAndSet(false, true)) {
        handler.postDelayed(tick, intervalMs)
      }
    }
    fun stop() {
      if (active.compareAndSet(true, false)) {
        handler.removeCallbacks(tick)
        activityRef?.get()?.let { a ->
          a.runOnUiThread { a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        cb.onSlideShowStopped()
      }
    }
    fun isActive(): Boolean = active.get()
    companion object {
      /** 1. Fade transition - fade out, load next, fade in */
      fun fadeTransition(view: View, loadNext: Runnable, duration: Long) {
        view.animate().cancel() // Cancel any ongoing animations
        view.animate()
        .alpha(0f)
        .setDuration(600)
        .withEndAction {
          loadNext.run() // Switch item while invisible
          view.alpha = 0f
          // Wait for the next frame to ensure the new item is laid out
          view.post {
            view.animate()
            .alpha(1f)
            .setDuration(800)
            // .setDuration(duration / 2)
            .setInterpolator(AccelerateInterpolator())
            .start()
          }
        }
        .start()
      }
      /** 3. Slide transition - slide out left, load next, slide in from right */
      fun slideTransition(view: View, loadNext: Runnable, duration: Long) {
        view.animate().cancel()
        val slideDistance = view.width.toFloat()
        view.animate()
        .translationX(-slideDistance)
        .setDuration(duration / 2)
        .setInterpolator(AccelerateInterpolator())
        .withEndAction {
          loadNext.run() // Switch item while off-screen
          view.translationX = slideDistance // Move to right side instantly
          view.post {
            view.animate()
            .translationX(0f) // Slide back to center
            .setDuration(duration / 2)
            .setInterpolator(AccelerateInterpolator())
            .start()
          }
        }
        .start()
      }
      /** 2. Scale transition - fade out, switch, scale from 1f (fit) to 1.2f (fill) */
      fun scaleTransition(view: View, loadNext: Runnable, duration: Long) {
        view.animate().cancel()
        val targetFillScale = 1.4f // Adjust this value if you want more or less zoom
        // Fade out current item quickly (1/4 of duration)
        view.animate()
        .scaleX(targetFillScale)
        .scaleY(targetFillScale)
        .setInterpolator(DecelerateInterpolator())
        // .setDuration(300)
        .setDuration(duration - (duration))
        .withEndAction {
          loadNext.run() // Switch item
          // Set initial state: fully visible, fitScreen (1.0f)
          view.scaleX = 1.0f
          view.scaleY = 1.0f
          view.post {
            // Animate scale to fillScreen over the remaining duration
            view.animate()
            .scaleX(targetFillScale)
            .scaleY(targetFillScale)
            .setDuration(duration + (duration))
            // .setDuration(duration - (duration))
            .setInterpolator(AccelerateInterpolator())
            .start()
          }
        }
        .start()
      }
      /** 4. Combined transition - fade + scale */
      fun combinedTransition(view: View, loadNext: Runnable, duration: Long) {
        view.animate().cancel()
        val targetFillScale = 1.4f
        // val adapter = VpAdpt()
        // var targetFill = adapter.getFillScale()
        view.animate()
        .alpha(0f)
        .setDuration(300)
        // .setDuration(duration / 2)
        .setInterpolator(AccelerateInterpolator())
        .withEndAction {
          loadNext.run()
          // Set initial state: invisible and fitScreen (1.0f)
          view.alpha = 1f
          view.scaleX = 1.0f
          view.scaleY = 1.0f
          view.post {
            // Animate fade-in AND scale to fillScreen simultaneously
            view.animate()
            .alpha(1f)
            .scaleX(targetFillScale)
            .scaleY(targetFillScale)
            .setDuration(duration)
            .setInterpolator(AccelerateInterpolator())
            .start()
          }
        }
        .start()
      }
    }
  }
  enum class TransitionType {
    FADE,
    SLIDE,
    SCALE,
    COMBINED
  }
  private var currentTransition = TransitionType.FADE
  private val timer =
  SlideShow(
    object : SlideShow.Callback {
      override fun onNextImage() = showNextImageWithTransition()
      override fun onSlideShowStopped() = onTimerStopped()
    },
    activity
  )
  var isRunning: Boolean = false
  private set
  var isRotated: Boolean = false
  private set
  var isAutoRotateEnable: Boolean = true
  private set
  var isRotateSideRight: Boolean = true
  private set
  var isMirrorEnabled: Boolean = true
  private set
  fun setTransitionType(type: TransitionType) {
    currentTransition = type
  }
  fun setAutoRotateEnable(enable: Boolean) {
    isAutoRotateEnable = enable
  }
  fun setRotationDirection(rotateRight: Boolean) {
    isRotateSideRight = rotateRight
  }
  fun setRotationMirror(mirrorEnabled: Boolean) {
    isMirrorEnabled = mirrorEnabled
  }
  fun start(delaySeconds: Int) {
    if (isRunning) return
    isRunning = true
    uiCallback.onSlideShowStarted()
    timer.start((delaySeconds * 1000L).coerceAtLeast(500))
  }
  fun stop() {
    if (!isRunning) return
    isRunning = false
    timer.stop()
  }
  fun cleanup() {
    stop()
  }
  private fun onTimerStopped() {
    isRunning = false
    viewPager.apply {
      scaleX = 1f
      scaleY = 1f
      translationX = 0f
      translationY = 0f
      alpha = 1f
    }
    uiCallback.onSlideShowStopped()
  }
  private fun showNextImageWithTransition() {
    val totalCount = viewModel.mediaList.value.size ?: 0
    if (totalCount == 0) {
      stop()
      return
    }
    var nextPosition = viewPager.currentItem + 1
    if (nextPosition >= totalCount) {
      if (settings.slideshowLoopEnabled) {
        nextPosition = 0
      } else {
        stop()
        return
      }
    }
    viewPager.apply {
      scaleX = 1f
      scaleY = 1f
      translationX = 0f
      translationY = 0f
      alpha = 1f
    }
    // FIXED: Use "slideshow_delay" to match ImgFrg, and removed the 2000ms cap
    val savedDelay = prefs.getInt("slideshow_delay", 5)
    val animDuration = savedDelay * 1000L
    // val animDuration = (savedDelay * 1000L).coerceAtMost(4000L)
    val page = adapter.getZoomablePageAt(nextPosition)
    val item = viewModel.mediaList.value?.getOrNull(nextPosition)
    viewPager.post {
      val rotateSide = if (settings.slideshowAutoRotateDirection == "right") 90f else -90f
      val rotateDeg = page?.getRotationDegrees()
      if (page is ImageViewZoomablePage && item != null) {
        if (settings.slideshowAutoRotateEnabled && isRotated == false && item.width > item.height) {
          page.rotateBy(rotateSide)
          if (settings.slideshowAutoMirrorEnabled) { page.mirrorHorizontal(true); page.mirrorVertical(true) }
          page.resetToFit(false)
          isRotated = true
        } else {
          page.rotateBy(0f)
          if (settings.slideshowAutoMirrorEnabled) { page.mirrorHorizontal(true); page.mirrorVertical(true) }
          page.resetToFit(false)
          isRotated = false
        }
      }
    }
    val loadNextAndReset = Runnable {
      viewPager.currentItem = nextPosition
      if (isRotated) {
        isRotated = false
        page?.rotateBy(0f)
      }
    }
    // when (settings.slideshowAnimation) {
    when (currentTransition) {
      TransitionType.FADE -> SlideShow.fadeTransition(viewPager, loadNextAndReset, animDuration)
      TransitionType.SLIDE -> SlideShow.slideTransition(viewPager, loadNextAndReset, animDuration)
      TransitionType.SCALE -> SlideShow.scaleTransition(viewPager, loadNextAndReset, animDuration)
      TransitionType.COMBINED -> SlideShow.combinedTransition(viewPager, loadNextAndReset, animDuration)
    }
  }
}
