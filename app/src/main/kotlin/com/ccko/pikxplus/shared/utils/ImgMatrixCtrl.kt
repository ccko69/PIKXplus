package com.ccko.pikxplus.shared.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.ImageView
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class ImgMatrixCtrl(imageView: ImageView) {

    enum class DoubleTapBehavior { ZOOM_AT_POINT, CENTER_ON_POINT }
    enum class ScaleReference { DRAWABLE, VIEW }

    private val imageViewRef = WeakReference(imageView)
    private val currentMatrix = Matrix()
    
    private var currentScale = 1f
    private var fitScale = 1f
    private var fillScale = 1f
    
    private var minScale = 1f
    private var maxScale = 20f
    private var doubleTapExtraScale = 0f
    
    private var currentRotation = 0f
    private var horizontalMirror = false
    private var verticalMirror = false
    
    private var minScaleRef = ScaleReference.VIEW
    private var maxScaleRef = ScaleReference.VIEW
    
    private var gestureMinScale = -1f
    private var gestureMinScaleRef = ScaleReference.VIEW
    
    private var defaultAnimDuration = 800L
    private var defaultInterpolator: Interpolator = AccelerateDecelerateInterpolator()
    
    private var currentAnimator: ValueAnimator? = null
    private var matrixChangeListener: OnMatrixChangeListener? = null

    // --- Layout Readiness Queue (Fixes Orientation Bug) ---
    private var pendingAction: (() -> Unit)? = null
    private val layoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            val iv = imageViewRef.get() ?: return
            if (iv.width > 0 && iv.height > 0 && iv.drawable != null) {
                iv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                pendingAction?.invoke()
                pendingAction = null
            }
        }
    }

    init {
        imageView.scaleType = ImageView.ScaleType.MATRIX
    }

    private fun runWhenReady(action: () -> Unit) {
        val iv = imageViewRef.get() ?: return
        if (iv.width > 0 && iv.height > 0 && iv.drawable != null) {
            action()
        } else {
            pendingAction = action
            iv.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        }
    }
    // ── Public Configuration ───────────────────────────────────
    fun setMinScale(scale: Float, ref: ScaleReference = ScaleReference.VIEW) {
        minScale = scale; minScaleRef = ref; clampAndApply()
    }
    fun setMaxScale(scale: Float, ref: ScaleReference = ScaleReference.VIEW) {
        maxScale = scale; maxScaleRef = ref; clampAndApply()
    }
    fun setDefaultAnimDuration(ms: Long) { defaultAnimDuration = ms }
    fun setDefaultInterpolator(interp: Interpolator) { defaultInterpolator = interp }
    fun setGestureMinScale(scale: Float, ref: ScaleReference = ScaleReference.VIEW) {
        gestureMinScale = scale; gestureMinScaleRef = ref
    }
    fun clearGestureMinScale() { gestureMinScale = -1f }
    fun setOnMatrixChangeListener(listener: OnMatrixChangeListener?) { matrixChangeListener = listener }

    val isHorizontallyMirrored get() = horizontalMirror
    val isVerticallyMirrored get() = verticalMirror
    val scale get() = currentScale
    val fitScaleValue get() = fitScale
    val fillScaleValue get() = fillScale
    val rotation get() = currentRotation
    // ── Public Actions ───────────────────────────────────
    fun resetToFit(animate: Boolean = false) {
        resetToFit(animate, defaultAnimDuration, defaultInterpolator);
    }
    fun resetToFit(animate: Boolean = false, duration: Long = defaultAnimDuration, interpolator: Interpolator = defaultInterpolator) {
        runWhenReady {
            val targetMatrix = computeFitMatrix()
            clampMatrixForMatrix(targetMatrix)
            if (animate) animateTo(targetMatrix, fitScale, duration, interpolator)
            else { cancelRunningAnimation(); applyMatrix(targetMatrix, fitScale) }
        }
    }
    /**
     * Call this from onConfigurationChanged(). 
     * It waits for the new layout dimensions before recalculating the fit matrix.
     */
    fun onConfigurationChanged() {
        runWhenReady {
            val targetMatrix = computeFitMatrix()
            clampMatrixForMatrix(targetMatrix)
            cancelRunningAnimation()
            applyMatrix(targetMatrix, fitScale)
        }
    }
    fun zoomTo(scale: Float, focusX: Float, focusY: Float, animate: Boolean = false, duration: Long = defaultAnimDuration, interpolator: Interpolator = defaultInterpolator) {
        runWhenReady {
            val clampedScale = clampScale(scale)
            val target = Matrix(currentMatrix).apply {
                postScale(clampedScale / currentScale, clampedScale / currentScale, focusX, focusY)
            }
            clampMatrixForMatrix(target)
            if (animate) animateTo(target, clampedScale, duration, interpolator)
            else { cancelRunningAnimation(); applyMatrix(target, clampedScale) }
        }
    }
    fun panBy(dx: Float, dy: Float) {
        runWhenReady {
            currentMatrix.postTranslate(dx, dy)
            clampMatrixForMatrix(currentMatrix)
            val vals = FloatArray(9).also { currentMatrix.getValues(it) }
            currentScale = hypot(vals[Matrix.MSCALE_X], vals[Matrix.MSKEW_Y])
            applyMatrix(currentMatrix, currentScale)
        }
    }
    fun mirrorHorizontal(animate: Boolean = false) {
        mirrorHorizontal(animate, defaultAnimDuration, defaultInterpolator);
    }
    fun mirrorHorizontal(animate: Boolean = false, duration: Long = defaultAnimDuration, interpolator: Interpolator = defaultInterpolator) {
        runWhenReady {
            horizontalMirror = !horizontalMirror
            val targetMatrix = computeFitMatrix()
            clampMatrixForMatrix(targetMatrix)
            if (animate) animateTo(targetMatrix, fitScale, duration, interpolator)
            else { cancelRunningAnimation(); applyMatrix(targetMatrix, fitScale) }
        }
    }
    fun mirrorVertical(animate: Boolean = false) {
        mirrorVertical(animate, defaultAnimDuration, defaultInterpolator);
    }
    fun mirrorVertical(animate: Boolean = false, duration: Long = defaultAnimDuration, interpolator: Interpolator = defaultInterpolator) {
        runWhenReady {
            verticalMirror = !verticalMirror
            val targetMatrix = computeFitMatrix()
            clampMatrixForMatrix(targetMatrix)
            if (animate) animateTo(targetMatrix, fitScale, duration, interpolator)
            else { cancelRunningAnimation(); applyMatrix(targetMatrix, fitScale) }
        }
    }
    
    fun rotateBy(degrees: Float) {
        rotateBy(degrees, false);
    }
    
    fun rotateBy(degrees: Float, animate: Boolean = false) {
        rotateBy(degrees, animate, defaultAnimDuration, defaultInterpolator);
    }
    fun rotateBy(degrees: Float, animate: Boolean = false, duration: Long = defaultAnimDuration, interpolator: Interpolator = defaultInterpolator) {
        runWhenReady {
            currentRotation = (currentRotation + degrees) % 360f
            val targetMatrix = computeFitMatrix()
            val rect = getDisplayRect(targetMatrix) ?: return@runWhenReady
            targetMatrix.postRotate(degrees, rect.centerX(), rect.centerY())
            clampMatrixForMatrix(targetMatrix)
            
            if (animate) animateTo(targetMatrix, currentScale, duration, interpolator)
            else { cancelRunningAnimation(); applyMatrix(targetMatrix, currentScale) }
        }
    }
    fun onDoubleTap(tapX: Float, tapY: Float, behavior: DoubleTapBehavior) {
        runWhenReady {
            val threshold = 0.01f
            val targetScale = if (abs(currentScale - fitScale) < threshold) {
                val desired = fillScale + doubleTapExtraScale
                max(fitScale, min(desired, getEffectiveMaxScale()))
            } else {
                fitScale
            }

            val targetMatrix = computeZoomMatrix(targetScale, tapX, tapY, behavior)
            clampMatrixForMatrix(targetMatrix)
            animateTo(targetMatrix, targetScale, defaultAnimDuration, defaultInterpolator)
        }
    }

    fun getMatrix(): Matrix = Matrix(currentMatrix)

    fun release() {
        cancelRunningAnimation()
        imageViewRef.get()?.viewTreeObserver?.removeOnGlobalLayoutListener(layoutListener)
        pendingAction = null
        imageViewRef.clear()
    }
    // ── Private Helpers ───────────────────────────────────
    private fun computeFitMatrix(): Matrix {
        val iv = imageViewRef.get() ?: return Matrix()
        val d = iv.drawable ?: return Matrix()
        val dw = getDrawableWidth(d)
        val dh = getDrawableHeight(d)
        val vw = getViewContentWidth(iv)
        val vh = getViewContentHeight(iv)
        if (dw <= 0 || dh <= 0 || vw <= 0 || vh <= 0) return Matrix()

        val rotMatrix = Matrix().apply { setRotate(currentRotation, dw / 2f, dh / 2f) }
        val rotatedBounds = RectF(0f, 0f, dw.toFloat(), dh.toFloat())
        rotMatrix.mapRect(rotatedBounds)

        val scale = min(vw / rotatedBounds.width(), vh / rotatedBounds.height())
        fitScale = scale
        fillScale = max(vw / rotatedBounds.width(), vh / rotatedBounds.height())

        return Matrix().apply {
            setRotate(currentRotation, dw / 2f, dh / 2f)
            val hScale = if (horizontalMirror) -1f else 1f
            val vScale = if (verticalMirror) -1f else 1f
            // Fixed bug: original code passed (vScale, hScale) which swapped the axes!
            postScale(hScale, vScale, dw / 2f, dh / 2f) 
            postScale(scale, scale, dw / 2f, dh / 2f)
            val dx = vw / 2f + iv.paddingLeft - (dw / 2f) * scale
            val dy = vh / 2f + iv.paddingTop - (dh / 2f) * scale
            postTranslate(dx, dy)
        }
    }
    private fun computeZoomMatrix(targetScale: Float, focusX: Float, focusY: Float, behavior: DoubleTapBehavior): Matrix {
        val m = Matrix(currentMatrix)
        m.postScale(targetScale / currentScale, targetScale / currentScale, focusX, focusY)

        if (behavior == DoubleTapBehavior.CENTER_ON_POINT) {
            val iv = imageViewRef.get() ?: return m
            val pts = floatArrayOf(focusX, focusY)
            m.mapPoints(pts)
            val centerX = iv.width / 2f
            val centerY = iv.height / 2f
            m.postTranslate(centerX - pts[0], centerY - pts[1])
        }
        return m
    }
    private fun clampMatrixForMatrix(matrix: Matrix) {
        val iv = imageViewRef.get() ?: return
        val rect = getDisplayRect(matrix) ?: return
        val viewW = getViewContentWidth(iv)
        val viewH = getViewContentHeight(iv)
        if (viewW <= 0 || viewH <= 0) return

        var deltaX = 0f
        var deltaY = 0f

        if (rect.width() <= viewW) deltaX = (viewW - rect.width()) * 0.5f - rect.left
        else {
            if (rect.left > iv.paddingLeft) deltaX = iv.paddingLeft - rect.left
            else if (rect.right < viewW + iv.paddingLeft) deltaX = viewW + iv.paddingLeft - rect.right
        }

        if (rect.height() <= viewH) deltaY = (viewH - rect.height()) * 0.5f - rect.top
        else {
            if (rect.top > iv.paddingTop) deltaY = iv.paddingTop - rect.top
            else if (rect.bottom < viewH + iv.paddingTop) deltaY = viewH + iv.paddingTop - rect.bottom
        }

        if (abs(deltaX) > 0.5f || abs(deltaY) > 0.5f) matrix.postTranslate(deltaX, deltaY)
    }
    private fun getDisplayRect(matrix: Matrix): RectF? {
        val d = imageViewRef.get()?.drawable ?: return null
        val rect = RectF(0f, 0f, getDrawableWidth(d).toFloat(), getDrawableHeight(d).toFloat())
        matrix.mapRect(rect)
        return rect
    }

    private fun clampScale(scale: Float): Float = max(getEffectiveMinScale(), min(scale, getEffectiveMaxScale()))
    
    fun getEffectiveMinScale(): Float = if (gestureMinScale > 0) convertScaleReference(gestureMinScale, gestureMinScaleRef) else convertScaleReference(minScale, minScaleRef)
    fun getEffectiveMaxScale(): Float = convertScaleReference(maxScale, maxScaleRef)

    private fun convertScaleReference(value: Float, ref: ScaleReference): Float {
        val iv = imageViewRef.get() ?: return value
        val d = iv.drawable ?: return value
        val dw = getDrawableWidth(d)
        val dh = getDrawableHeight(d)
        val vw = getViewContentWidth(iv)
        val vh = getViewContentHeight(iv)
        if (dw <= 0 || dh <= 0 || vw <= 0 || vh <= 0) return value

        return if (ref == ScaleReference.DRAWABLE) value 
        else value * (min(vw, vh).toFloat() / min(dw, dh).toFloat())
    }

    private fun getViewContentWidth(iv: ImageView) = iv.width - iv.paddingLeft - iv.paddingRight
    private fun getViewContentHeight(iv: ImageView) = iv.height - iv.paddingTop - iv.paddingBottom
    private fun getDrawableWidth(d: Drawable) = if (d.intrinsicWidth > 0) d.intrinsicWidth else d.bounds.width()
    private fun getDrawableHeight(d: Drawable) = if (d.intrinsicHeight > 0) d.intrinsicHeight else d.bounds.height()

    private fun animateTo(targetMatrix: Matrix, targetScale: Float, duration: Long, interpolator: Interpolator) {
        cancelRunningAnimation()
        val startMatrix = Matrix(currentMatrix)
        val startScale = currentScale

        currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                val interpVals = FloatArray(9)
                val startVals = FloatArray(9).also { startMatrix.getValues(it) }
                val endVals = FloatArray(9).also { targetMatrix.getValues(it) }
                
                for (i in 0 until 9) {
                    interpVals[i] = startVals[i] + progress * (endVals[i] - startVals[i])
                }
                currentMatrix.setValues(interpVals)
                currentScale = startScale + progress * (targetScale - startScale)
                applyMatrix(currentMatrix, currentScale)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentAnimator = null
                    applyMatrix(targetMatrix, targetScale) // Snap to exact target to prevent float drift
                }
            })
            start()
        }
    }
    private fun cancelRunningAnimation() {
        currentAnimator?.cancel()
        currentAnimator = null
    }
    private fun applyMatrix(matrix: Matrix, scale: Float) {
        val iv = imageViewRef.get() ?: return
        iv.imageMatrix = matrix
        currentMatrix.set(matrix)
        currentScale = scale
        matrixChangeListener?.onMatrixChanged(currentMatrix, currentScale)
    }
    private fun clampAndApply() {
        runWhenReady {
            clampMatrixForMatrix(currentMatrix)
            applyMatrix(currentMatrix, currentScale)
        }
    }
    fun interface OnMatrixChangeListener {
        fun onMatrixChanged(matrix: Matrix, scale: Float)
    }
}