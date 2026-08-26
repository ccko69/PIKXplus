package com.ccko.pikxplus.viewers.vid

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration

class VidGstHandler(
    context: Context,
    private val host: HostCallback
) : View.OnTouchListener {

    private val gestureDetector: GestureDetector
    private val scaleGestureDetector: ScaleGestureDetector
    private val uiHandler: Handler
    private val ctx: Context

    @Volatile var isSeeking = false
    @Volatile var isAdjustingVolume = false
    @Volatile var isAdjustingBrightness = false
    @Volatile var isScaling = false

    private var gestureStartPos = 0L
    private var seekDeltaMs = 0L

    private val topDeadzonePercent = 0.1f
    private val bottomDeadzonePercent = 0.1f
    private var targetView: View? = null

    interface HostCallback {
        fun onSingleTap()
        fun onDoubleTap()
        fun onSeek(deltaMs: Long)
        fun onSeekPreview(previewPositionMs: Long)
        fun onVolumeChange(delta: Float)
        fun onBrightnessChange(delta: Float)
        fun onPinchZoom(scaleFactor: Float, focusX: Float, focusY: Float)
        fun onPan(dx: Float, dy: Float)
        fun getPlayerPosition(): Long
        fun getPlayerDuration(): Long
        fun pausePlayerProgressUpdates()
        fun resumePlayerProgressUpdates()
    }
    private enum class ActiveGesture { NONE, SCALE, SEEK, VOLUME, BRIGHTNESS }
    private var active = ActiveGesture.NONE
    init {
        this.ctx = context
        this.uiHandler = Handler(Looper.getMainLooper())
        this.gestureDetector = GestureDetector(context, VideoGestureListener())
        this.scaleGestureDetector = ScaleGestureDetector(context, VideoScaleListener())
    }
    private fun setActive(g: ActiveGesture) { active = g }
    private fun isActive(g: ActiveGesture): Boolean = active == g
    private fun clearActive() { active = ActiveGesture.NONE }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        targetView = v // Capture view for accurate dimensions
        
        val scaleHandled = scaleGestureDetector.onTouchEvent(event)
        var gestureHandled = false

        if (event.pointerCount < 2 && !scaleGestureDetector.isInProgress) {
            gestureHandled = gestureDetector.onTouchEvent(event)
        }
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            finalizeSeekIfNeeded()
            resetFlags()
            if (isScaling) {
                isScaling = false
                host.resumePlayerProgressUpdates()
            }
            clearActive()
        }
        return scaleHandled || gestureHandled
    }
    private fun finalizeSeekIfNeeded() {
        if (isSeeking) {
            host.onSeek(seekDeltaMs)
            host.resumePlayerProgressUpdates()
        }
    }
    private fun resetFlags() {
        isSeeking = false
        isAdjustingVolume = false
        isAdjustingBrightness = false
        seekDeltaMs = 0
    }
    fun cleanup() {
        uiHandler.removeCallbacksAndMessages(null)
    }
    private inner class VideoGestureListener : GestureDetector.SimpleOnGestureListener() {
        private val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop

        override fun onDown(e: MotionEvent): Boolean {
            gestureStartPos = host.getPlayerPosition()
            seekDeltaMs = 0
            return true
        }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            host.onSingleTap()
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val screenWidth = ctx.resources.displayMetrics.widthPixels
            val skipMs = 10_000L
            if (e.x < screenWidth / 2f) host.onSeek(-skipMs) else host.onSeek(skipMs)
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (e2.pointerCount > 1) return false
            if (e1 == null) return false
            // Use targetView height instead of screen height to account for system bars
            val viewHeight = targetView?.height?.toFloat() ?: return false
            val topIgnoreLimit = viewHeight * topDeadzonePercent
            val bottomIgnoreLimit = viewHeight * (1f - bottomDeadzonePercent)

            if (e1.y <= topIgnoreLimit || e1.y > bottomIgnoreLimit) return false

            if (active == ActiveGesture.NONE) {
                val totalDeltaX = e2.x - e1.x
                val totalDeltaY = e2.y - e1.y
                if (kotlin.math.abs(totalDeltaX) > touchSlop || kotlin.math.abs(totalDeltaY) > touchSlop) {
                    if (kotlin.math.abs(totalDeltaX) > kotlin.math.abs(totalDeltaY)) {
                        setActive(ActiveGesture.SEEK)
                        host.pausePlayerProgressUpdates()
                    } else {
                        val screenWidth = ctx.resources.displayMetrics.widthPixels
                        setActive(if (e1.x > screenWidth / 2) ActiveGesture.VOLUME else ActiveGesture.BRIGHTNESS)
                    }
                }
            }
            if (isActive(ActiveGesture.SEEK)) handleSeekGesture(-distanceX)
            else if (isActive(ActiveGesture.VOLUME)) handleVolumeGesture(-distanceY)
            else if (isActive(ActiveGesture.BRIGHTNESS)) handleBrightnessGesture(-distanceY)

            return true
        }
        private fun handleSeekGesture(deltaX: Float) {
            isSeeking = true
            val screenWidth = ctx.resources.displayMetrics.widthPixels
            var duration = host.getPlayerDuration()
            if (duration <= 0) duration = 300000L

            val seekRatio = deltaX / screenWidth
            val deltaMs = (seekRatio * duration * 0.1f).toLong()
            seekDeltaMs += deltaMs

            var previewPos = gestureStartPos + seekDeltaMs
            if (previewPos < 0) previewPos = 0
            if (duration > 0 && previewPos > duration) previewPos = duration
            host.onSeekPreview(previewPos)
        }
        private fun handleVolumeGesture(incrementalY: Float) {
            isAdjustingVolume = true
            host.onVolumeChange(-incrementalY * 0.013f)
        }
        private fun handleBrightnessGesture(incrementalY: Float) {
            isAdjustingBrightness = true
            host.onBrightnessChange(-incrementalY * 0.004f)
        }
    }
    private inner class VideoScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var prevFocusX = Float.NaN
        private var prevFocusY = Float.NaN
        // TWEAK THESE FOR VELOCITY / RESPONSIVENESS
        private val ZOOM_SPEED_MULTIPLIER = 1.0f // >1.0f = faster zoom, <1.0f = slower zoom
        private val PAN_SPEED_MULTIPLIER = 1.0f  // >1.0f = faster pan, <1.0f = slower pan

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            setActive(ActiveGesture.SCALE)
            host.pausePlayerProgressUpdates()
            prevFocusX = detector.focusX
            prevFocusY = detector.focusY
            return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val focusX = detector.focusX
            val focusY = detector.focusY

            if (!prevFocusX.isNaN() && !prevFocusY.isNaN()) {
                val dx = (focusX - prevFocusX) * PAN_SPEED_MULTIPLIER
                val dy = (focusY - prevFocusY) * PAN_SPEED_MULTIPLIER
                if (dx != 0f || dy != 0f) host.onPan(dx, dy)
            }
            prevFocusX = focusX
            prevFocusY = focusY

            if (detector.scaleFactor != 1.0f) {
                val adjustedScaleFactor = detector.scaleFactor * ZOOM_SPEED_MULTIPLIER
                host.onPinchZoom(adjustedScaleFactor, focusX, focusY)
            }
            return true
        }
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            host.resumePlayerProgressUpdates()
            prevFocusX = Float.NaN
            prevFocusY = Float.NaN
        }
    }
}