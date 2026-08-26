package com.ccko.pikxplus.ux.settings
import android.app.Activity
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ccko.pikxplus.MainActivity
import com.ccko.pikxplus.viewers.img.SlideShowCtrl
import com.ccko.pikxplus.viewers.vid.VidCtrl
import java.lang.ref.WeakReference
import kotlinx.coroutines.launch
// import com.ccko.pikxplus.viewers.img.ImgFrg
/**
 * Unified Settings Controller file.
 * 
 * Currently hosts [ImgSetDlg] for Image viewer settings.
 * Later, [VidSetDlg] and [GenSetDlg] can be added to this same file
 * to handle Video and General settings respectively.
 */
class ImgSetDlg(
  private val repo: SetRepo,
  private val lifecycleOwner: LifecycleOwner,
  activity: Activity
  // private val imgFrg: ImgFrg
) {
  // WeakReference to Activity to safely modify Window flags
  private val activityRef = WeakReference(activity)
  // WeakReferences to Views that need direct manipulation
  private var leftHotspotRef: WeakReference<View>? = null
  private var rightHotspotRef: WeakReference<View>? = null
  // WeakReference to SlideShowCtrl to apply transitions/rotations directly
  private var slideshowRef: WeakReference<SlideShowCtrl>? = null
  // ===== STATE (Read-only from outside) =====
  // var logText = "content inside: "
  var hotspotEnabled = true;               private set
  var maxBrightnessEnabled = false;        private set
  var keepScreenOnEnabled = true;          private set
  var slideshowLoopEnabled = false;        private set
  var slideshowAutoRotateEnabled = false;  private set
  var slideshowAutoMirrorEnabled = false;  private set
  var slideshowAnimation = "fade";         private set
  var slideshowAutoRotateDirection = "right";  private set
  var autoRotateMode = "disable"
  private var brightness = -1.0f
  fun setMirrorEnabled(mirrorEnabled: Boolean) {
    slideshowAutoMirrorEnabled = mirrorEnabled
    // Log.e(TAG, logText + slideshowAutoMirrorEnabled)
  }
  // ===== BINDERS =====
  /**
     * Call this to pass the Hotspot views. They will be updated immediately
     * based on current state, and automatically on subsequent setting changes.
     */
  fun bindHotspotViews(left: View, right: View) {
    leftHotspotRef = WeakReference(left)
    rightHotspotRef = WeakReference(right)
    applyHotspotVisibility(hotspotEnabled) // Apply initial state immediately
  }
  /**
     * Call this after initializing the Slideshow so it can receive
     * animation and rotation updates directly.
     */
  fun bindSlideshow(slideshow: SlideShowCtrl) {
    slideshowRef = WeakReference(slideshow)
    // Apply current states immediately
    slideshow.setTransitionType(mapAnimation(slideshowAnimation))
    slideshow.setAutoRotateEnable(slideshowAutoRotateEnabled)
    slideshow.setRotationDirection(slideshowAutoRotateDirection == "right")
    slideshow.setRotationMirror(slideshowAutoMirrorEnabled)
  }
  // ===== SEEDING (initial state from prefs, runs once before observe()) =====
  /**
     * Read current pref values synchronously and apply them to local state.
     * Call this AFTER bindHotspotViews/bindSlideshow but BEFORE observe().
     * Guarantees correct state on first frame even if the flow's initial
     * trySend races with binding setup.
     */
  fun seedFromPrefs() {
    val p = repo.sharedPrefs
    hotspotEnabled             = p.getBoolean(PrefKeys.IMG_HOTSPOT, true)
    slideshowLoopEnabled       = p.getBoolean(PrefKeys.IMG_LOOP, false)
    slideshowAutoRotateEnabled = p.getBoolean(PrefKeys.IMG_AUTO_ROTATION, false)
    slideshowAutoMirrorEnabled = p.getBoolean(PrefKeys.IMG_MIRROR, false)
    maxBrightnessEnabled       = p.getBoolean(PrefKeys.IMG_MAX_BRIGHTNESS, false)
    keepScreenOnEnabled        = p.getBoolean(PrefKeys.GEN_KEEP_SCREEN, true)
    slideshowAutoRotateDirection =
      p.getString(PrefKeys.IMG_AUTO_ROTATION_MODE, "disable") ?: "disable"
    slideshowAnimation         = p.getString(PrefKeys.IMG_ANIMATION, "fade") ?: "fade"
    // Push to views/slideshow immediately
    applyHotspotVisibility(hotspotEnabled)
    if (maxBrightnessEnabled) applyMaxBrightness(true)
    keepScreenOn(keepScreenOnEnabled)
    slideshowRef?.get()?.setTransitionType(mapAnimation(slideshowAnimation))
    slideshowRef?.get()?.setAutoRotateEnable(slideshowAutoRotateEnabled)
    slideshowRef?.get()?.setRotationDirection(slideshowAutoRotateDirection == "right")
    slideshowRef?.get()?.setRotationMirror(slideshowAutoMirrorEnabled)
  }
  // ===== OBSERVERS =====
  fun observe() {
    lifecycleOwner.lifecycleScope.launch {
      lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { repo.imgHotspotEnabled.collect {
            hotspotEnabled = it
            applyHotspotVisibility(it)
        }}
        launch { repo.imgLoopEnabled.collect {
          slideshowLoopEnabled = it
        } }
        launch { repo.imgAutoRotation.collect { enabled ->
            slideshowAutoRotateEnabled = enabled
            slideshowRef?.get()?.setAutoRotateEnable(enabled)
        }}
        launch { repo.imgAutoMirror.collect {
            slideshowAutoMirrorEnabled = it
            slideshowRef?.get()?.setRotationMirror(it)
        }}
        launch { repo.imgMaxBrightness.collect {
           applyMaxBrightness(it)
         }  }
        launch { repo.genKeepScreenOn.collect { keepScreenOn(it) } }
        launch { repo.imgAutoRotateDirection.collect { direction ->
            slideshowAutoRotateDirection = direction
            slideshowRef?.get()?.setRotationDirection(direction == "right")
        }}
        launch { repo.imgAnimation.collect { anim ->
            slideshowAnimation = anim
            slideshowRef?.get()?.setTransitionType(mapAnimation(anim))
        }}
      }
    }
  }
  // ===== ACTIONS =====
  private fun applyHotspotVisibility(enable: Boolean) {
    val visibility = if (enable) View.VISIBLE else View.GONE
    leftHotspotRef?.get()?.visibility = visibility
    rightHotspotRef?.get()?.visibility = visibility
  }
  fun applyMaxBrightness(enable: Boolean) {
    val a = activityRef.get() ?: return
    brightness = if (enable) 1.0f else -1.0f
    maxBrightnessEnabled = enable
    val lp = a.window.attributes
    lp.screenBrightness = brightness
    a.window.attributes = lp
  }
  fun keepScreenOn(enable: Boolean) {
    val a = activityRef.get() ?: return
    keepScreenOnEnabled = enable
    if (enable) {
      a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
      a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
  }
  private fun mapAnimation(anim: String): SlideShowCtrl.TransitionType {
    return when (anim) {
      "transition" -> SlideShowCtrl.TransitionType.SLIDE
      "zoom" -> SlideShowCtrl.TransitionType.SCALE
      "combined" -> SlideShowCtrl.TransitionType.COMBINED
      else -> SlideShowCtrl.TransitionType.FADE
    }
  }
}
// =====================================================================
// VideoPlayer's Settings.
// =====================================================================
class VidSetDlg(
  private val repo: SetRepo,
  private val lifecycleOwner: LifecycleOwner,
  activity: Activity
) {
  private val activityRef = WeakReference(activity)
  // Hold a strong reference to the casted MainActivity so toggleOri can
  // call toggleOrientation/toggleOrientationReset. Passed activity is always
  // a MainActivity in this app (VidFrg hands requireActivity() in).
  private val mainActivity: MainActivity? = activity as? MainActivity
  private var vidCtrlRef: WeakReference<VidCtrl>? = null
  private var playerRef: WeakReference<ExoPlayer>? = null
  // Video dimensions for "auto" mode — set by VidFrg when video loads
  private var currentVideoWidth = 0
  private var currentVideoHeight = 0
  // ===== STATE (Read-only from outside) =====
  var maxBrightnessEnabled = false; private set
  var isMuted = false; private set
  var isLooping = false; private set
  var autoHideDelay = 5; private set
  var forceLandscape = false; private set
  private var swipeBrightness = -1f
  // ===== BINDERS =====
  fun bindVidController(controller: VidCtrl) {
    vidCtrlRef = WeakReference(controller)
    controller.scheduleAutoHide(autoHideDelay)
    controller.setMuted(isMuted)
    controller.setLooping(isLooping)
  }
  fun bindPlayer(player: ExoPlayer) {
    playerRef = WeakReference(player)
    player.volume = if (isMuted) 0f else 1f
    player.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
  }
  /** Called from VidFrg when a new video starts playing — updates current dimensions for "auto" mode. */
  fun setVideoDimensions(width: Int, height: Int) {
    currentVideoWidth = width
    currentVideoHeight = height
    // If in auto mode, re-evaluate orientation immediately
    val mode = repo.sharedPrefs.getString(PrefKeys.VID_LANDSCAPE_MODE, "portrait") ?: "portrait"
    if (mode == "auto") {
      val isLandscape = width > height
      applyOrientation(isLandscape)
    }
  }
  // ===== OBSERVERS =====
  fun observe() {
    lifecycleOwner.lifecycleScope.launch {
      lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { repo.vidMaxBrightness.collect { applyMaxBrightnessVid(it) } }
        launch { repo.vidMute.collect {
            isMuted = it
            playerRef?.get()?.volume = if (it) 0f else 1f
            vidCtrlRef?.get()?.setMuted(it)
        }}
        launch { repo.vidLoop.collect {
            isLooping = it
            playerRef?.get()?.repeatMode = if (it) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            vidCtrlRef?.get()?.setLooping(it)
        }}
        launch { repo.vidAutohideDelay.collect {
            autoHideDelay = it
            vidCtrlRef?.get()?.scheduleAutoHide(it)
        }}
        // Observe the mode string directly and handle all three cases
        launch { repo.vidLandscapeMode.collect { mode ->
            when (mode) {
              "landscape" -> applyOrientation(true)
              "portrait" -> applyOrientation(false)
              "auto" -> {
                // Re-evaluate based on current video dimensions
                val isLandscape = currentVideoWidth > currentVideoHeight
                applyOrientation(isLandscape)
              }
            }
        }}
        // launch { repo.vidLandscape.collect { forceLandscape = it } }
      }
    }
  }
  // ===== ACTIONS =====
  private fun applyOrientation(enableLandscape: Boolean) {
    forceLandscape = enableLandscape
    val activity = mainActivity ?: return
    if (enableLandscape) activity.toggleOrientation() else activity.toggleOrientationReset()
  }
  private fun toggleOri(enable: Boolean) {
    val activity = mainActivity ?: return
    if (enable) activity.toggleOrientation() else activity.toggleOrientationReset()
  }
  private fun applyMaxBrightnessVid(enable: Boolean) {
    val a = activityRef.get() ?: return
    swipeBrightness = if (enable) 1.0f else -1.0f
    maxBrightnessEnabled = enable
    val lp = a.window.attributes
    lp.screenBrightness = swipeBrightness
    a.window.attributes = lp
  }
  /**
     * Called from VidFrg gesture handler to adjust brightness via swiping.
     * Returns the new brightness value (0.0 to 1.0) so the UI can update its overlay.
     */
  fun applySwipeBrightness(delta: Float): Float {
    val a = activityRef.get() ?: return -1f
    if (swipeBrightness < 0) {
      swipeBrightness = a.window.attributes.screenBrightness
      if (swipeBrightness < 0) swipeBrightness = 0.5f
    }
    swipeBrightness = (swipeBrightness + delta).coerceIn(0.01f, 1.0f)
    val lp = a.window.attributes
    lp.screenBrightness = swipeBrightness
    a.window.attributes = lp
    MainActivity.prefs.edit()
    .putFloat(PrefKeys.VID_SWIPE_BRIGHTNESS, swipeBrightness)
    .apply()
    return swipeBrightness
  }
  /**
     * Called from VidFrg onViewCreated to restore brightness from prefs.
     */
  fun restoreSavedSwipeBrightness() {
    val saved = MainActivity.prefs.getFloat(PrefKeys.VID_SWIPE_BRIGHTNESS, -1f)
    if (saved > 0f) {
      swipeBrightness = saved
      activityRef.get()?.let { a ->
        val lp = a.window.attributes
        lp.screenBrightness = swipeBrightness
        a.window.attributes = lp
      }
    }
  }
  /**
     * Called from VidFrg onDestroyView to reset brightness to system default.
     */
  fun resetSwipeBrightness() {
    swipeBrightness = -1f
    activityRef.get()?.let { a ->
      val lp = a.window.attributes
      lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
      a.window.attributes = lp
    }
  }
}
// =====================================================================
// Future implementations can go right below:
// =====================================================================
// class GenSetDlg(...) { ... }
