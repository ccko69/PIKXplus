package com.ccko.pikxplus.viewers.img

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.ccko.pikxplus.viewers.img.ImgFrg
import com.ccko.pikxplus.viewers.img.VpAdpt
import com.ccko.pikxplus.viewers.img.ImgVM

import com.helper.ImageMatrixController

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import android.util.DisplayMetrics
import kotlin.ranges.coerceAtLeast

import android.widget.Toast


/**
 * Gesture handler for image viewer. Implements View.OnTouchListener.
 *
 * For SSIV pages: returns false → SSIV handles natively. GestureDetector still fires
 * for long-press and fling.
 * For ImageView pages: actively drives the attached ImageMatrixController via ZoomablePage.
 *
 * Unified through ZoomablePage interface.
 */
class ImgGstHandler(
    context: Context,
    private val host: HostCallback,
    activity: Activity?
) : View.OnTouchListener {

    interface HostCallback {
        fun onLongPress()
        fun onRequestClose()
    }

    private val topDeadzonePercent = 0.1f
    private val bottomDeadzonePercent = 0.1f
    private val ctx: Context = context
    private val uiHandler = Handler(Looper.getMainLooper())

    private val gestureDetector = GestureDetector(context, ImageGestureListener())
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    // Current page (set by ImgFrg on page change)
    private var currentPage: ZoomablePage? = null
    // Flag indicating whether the current page is SSIV (to decide touch consumption)
    private var isSsivPage = false

    @Volatile var isPanning = false
    @Volatile var isScaling = false

    fun setCurrentPage(page: ZoomablePage?, isSsiv: Boolean) {
        currentPage = page
        isSsivPage = isSsiv
    }
    // ===== View.OnTouchListener =====
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        // Always feed detectors
        val gestureHandled = gestureDetector.onTouchEvent(event)
        scaleGestureDetector.onTouchEvent(event)

        // For SSIV pages, we return false so SSIV receives all events natively.
        // For ImageView pages, we consume events that we handle (pinch/pan) so the view
        // doesn't try to scroll etc. We'll return true if gestureHandled or scaling.
        return if (isSsivPage) {
            false
        } else {
            // ImageView page: we are driving the controller.
            // Return true to prevent default behavior, but still let detectors work.
            gestureHandled || scaleGestureDetector.isInProgress
        }
    }
    // ===== Gesture Listeners =====
    private inner class ImageGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onLongPress(e: MotionEvent) {
            host.onLongPress()
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (isSsivPage) {
                // Let SSIV handle double-tap natively
                return false
            }
            val page = currentPage ?: return false
            if (page is ImageViewZoomablePage) {
                page.controller.onDoubleTap(
                    e.x, e.y, ImageMatrixController.DoubleTapBehavior.ZOOM_AT_POINT
                )
                return true
            }
            return false
        }
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            isPanning = true
            uiHandler.postDelayed({ isPanning = false }, 150)

            // For ImageView pages, we actively pan
            if (!isSsivPage) {
                currentPage?.panBy(distanceX, distanceY)
                return true
            }
            return false // SSIV handles pan natively
        }
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val e1Safe = e1 ?: return false
            if (e2.pointerCount > 1) return false

            val topIgnore = ctx.resources.displayMetrics.heightPixels * topDeadzonePercent
            val bottomIgnore = ctx.resources.displayMetrics.heightPixels * (1f - bottomDeadzonePercent)
            if (e1Safe.y <= topIgnore || e1Safe.y > bottomIgnore) return false

            val page = currentPage
            if (page != null && page.getScale() >= page.getMinScale() * 1.05f) return false
            if (isPanning) return false

            val diffX = e2.x - e1Safe.x
            val diffY = e2.y - e1Safe.y
            if (kotlin.math.abs(diffY) > kotlin.math.abs(diffX)) {
                if (diffY > 50f && kotlin.math.abs(velocityY) > 200f) {
                    host.onRequestClose()
                }
            }
            return false
        }
    }
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            // For ImageView pages, set temporary min scale during pinch
            if (!isSsivPage) {
                (currentPage as? ImageViewZoomablePage)?.controller?.setGestureMinScale(0.7f, ImageMatrixController.ScaleReference.VIEW)
            }
            return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!isSsivPage) {
                val page = currentPage ?: return false
                val newScale = page.getScale() * detector.scaleFactor
                page.zoomTo(newScale, detector.focusX, detector.focusY, false)
                return true
            }
            return false // SSIV handles natively
        }
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            if (!isSsivPage) {
                val page = currentPage as? ImageViewZoomablePage
                page?.controller?.clearGestureMinScale()
                // Snap back if below fit scale
                if (page?.getScale() ?: 1f < page?.getMinScale() ?: 1f) {
                    page?.resetToFit(true)
                }
            }
        }
    }
}