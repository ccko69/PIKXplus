package com.ccko.pikxplus.shared.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import kotlin.math.*

class FloatMenu(
    private val container: ViewGroup,
    private val parentButton: View,
    private val childButtons: List<View>
) {
    enum class Direction { START, END, TOP, BOTTOM }

    enum class ChildPosition {
        TOP, TOP_CENTER, CENTER, BOTTOM_CENTER, BOTTOM;

        fun getBaseVector(): FloatArray {
            val invSqrt2 = 1f / sqrt(2f) // Normalizes diagonals to true Euclidean distance
            return when (this) {
                TOP -> floatArrayOf(0f, -1f)
                BOTTOM -> floatArrayOf(0f, 1f)
                CENTER -> floatArrayOf(1f, 0f)
                TOP_CENTER -> floatArrayOf(invSqrt2, -invSqrt2)
                BOTTOM_CENTER -> floatArrayOf(invSqrt2, invSqrt2)
            }
        }
    }
    // --- State ---
    private var isExpanded = false
    private var isDragging = false
    
    private var dragEnabled = true
    private var autoDirection = true
    private var animationDuration = 300L
    
    private var direction = Direction.START
    private var childSpacing = dpToPx(40f)
    private var childPositions: List<ChildPosition>? = null
    private var childPositionRadius = dpToPx(100f)

    private var snapMargin = 0f

    private var anchoredEdge = Gravity.END
    private var screenWidth = 0
    private var screenHeight = 0

    // Base layout position of the parent button (before any translation)
    private var layoutX = 0f
    private var layoutY = 0f
    
    // Current drag offsets
    private var currentDragX = 0f
    private var currentDragY = 0f
    private var lastDragX = 0f
    private var lastDragY = 0f

    private val touchSlop = ViewConfiguration.get(container.context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout()

    private var downX = 0f
    private var downY = 0f

    private val mainHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    init {
        updateScreenDimensions()
        setupDragTouchListener()
        setupContainerTouchListener()
        cacheBasePosition()
    }
    private fun updateScreenDimensions() {
        val metrics = container.resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }
    // --- Public API ---
    fun toggle() {
        if (isExpanded) collapse() else expand()
    }
    fun expand() {
        if (isExpanded || childButtons.isEmpty()) return
        if (autoDirection) updateDirectionFromEdge()
        
        childButtons.forEachIndexed { index, child ->
            animateChildIn(child, index)
        }
        isExpanded = true
    }
    fun collapse() {
        if (!isExpanded) return
        isExpanded = false
        childButtons.forEach { animateChildOut(it) }
    }
    fun setVisible(visible: Boolean, alpha: Float = 1f) {
        if (visible) {
            container.visibility = View.VISIBLE
            container.animate().alpha(alpha).setDuration(150).start()
        } else {
            container.animate().alpha(0f).setDuration(500).withEndAction {
                container.visibility = View.GONE
            }.start()
        }
    }
    fun show() {
        if (container.visibility != View.VISIBLE) container.visibility = View.VISIBLE
    }
    fun snap() = snapToNearestEdge(animate = true)
    fun snapImmediate() = snapToNearestEdge(animate = false)
    
  /*  fun onConfigurationChanged() {
        updateScreenDimensions()
        container.post {
            cacheBasePosition()
        }
    }*/
    fun dismiss() {
        cancelLongPress()
        mainHandler.removeCallbacksAndMessages(null)
    }
    // ── Public configuration ───────────────────────────────────
    fun setDirection(direction: Direction) { this.direction = direction }
    fun setAutoDirection(auto: Boolean) { this.autoDirection = auto }
    fun setAnimationDuration(ms: Long) { animationDuration = ms }
    fun setChildSpacingDp(dp: Float) { childSpacing = dpToPx(dp) }
    fun setChildPositions(positions: List<ChildPosition>) { childPositions = positions }
    fun setChildPositionRadius(radiusDp: Float) { childPositionRadius = dpToPx(radiusDp) }
    fun setDragEnabled(enabled: Boolean) { dragEnabled = enabled }
    fun setSnapMarginDp(dp: Float) { snapMargin = dpToPx(dp) }
    val isDraggingNow: Boolean get() = isDragging
    val isExpandedNow: Boolean get() = isExpanded
    fun setExpanded(enabled: Boolean) { isExpanded = enabled }
    // --- Animations ---
    private fun animateChildIn(child: View, index: Int) {
        child.animate().cancel()
        
        child.visibility = View.VISIBLE
        child.isEnabled = true
        child.isClickable = true
        child.scaleX = 0.5f
        child.scaleY = 0.5f
        child.alpha = 0f

        val offset = getOffset(index)

        child.animate()
            .translationX(currentDragX + offset[0])
            .translationY(currentDragY + offset[1])
            .scaleX(1f)
            .scaleY(1f) 
            .alpha(1f)
            .setDuration(animationDuration)
            .setInterpolator(AccelerateInterpolator())
            .start()
    }
    private fun animateChildOut(child: View) {
        child.animate().cancel()
        
        child.animate()
            .translationX(currentDragX)
            .translationY(currentDragY)
            .scaleX(0.5f)
            .scaleY(0.5f)
            .alpha(0f)
            .setDuration(animationDuration)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!isExpanded) {
                      child.visibility = View.INVISIBLE
                      child.isEnabled = false
                      child.isClickable = false
                    }
                }
            })
            .start()
    }
    private fun getOffset(index: Int): FloatArray {
        return if (childPositions != null && index < childPositions!!.size) {
            getChildOffsetFromPosition(index) 
        } else {
            getTargetOffset(index)
        }
    }
    private fun getTargetOffset(index: Int): FloatArray {
        val dist = childSpacing * (index + 1)
        return when (direction) {
            Direction.END -> floatArrayOf(dist, 0f)
            Direction.START -> floatArrayOf(-dist, 0f)
            Direction.BOTTOM -> floatArrayOf(0f, dist)
            Direction.TOP -> floatArrayOf(0f, -dist)
        }
    }
    private fun getChildOffsetFromPosition(index: Int): FloatArray {
        val positions = childPositions ?: return floatArrayOf(0f, 0f)
        if (index >= positions.size) return floatArrayOf(0f, 0f)

        val pos = positions[index]
        // 1. Find all indices that share this ChildPosition to determine pipeline order
        val groupIndices = positions.mapIndexed { i, p -> i to p }
            .filter { it.second == pos }
            .map { it.first }
            
        val groupIndex = groupIndices.indexOf(index) // 0-based index in the pipeline
        // 2. Get normalized base direction vector
        val baseVec = pos.getBaseVector()
        // 3. Flip vector based on anchored edge
        when (anchoredEdge) {
            Gravity.START -> baseVec[0] = abs(baseVec[0])
            Gravity.END -> baseVec[0] = -abs(baseVec[0])
            Gravity.TOP -> baseVec[1] = abs(baseVec[1])
            Gravity.BOTTOM -> baseVec[1] = -abs(baseVec[1])
        }
        if (pos == ChildPosition.CENTER) baseVec[1] = 0f
        // 4. Calculate linear distance along the pipeline
        val distance = childPositionRadius + (groupIndex * childSpacing)
        
        val tx = distance * baseVec[0]
        val ty = distance * baseVec[1]

        return floatArrayOf(tx, ty)
    }
    // --- Drag & Snap ---
    private fun updateDirectionFromEdge() {
        direction = when (anchoredEdge) {
            Gravity.START -> Direction.END
            Gravity.END -> Direction.START
            Gravity.TOP -> Direction.BOTTOM
            Gravity.BOTTOM -> Direction.TOP
            else -> direction
        }
    }
    private fun setupContainerTouchListener() {
        container.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val parentRect = getViewRectOnScreen(parentButton)
                val isOnParent = parentRect.contains(event.rawX.toInt(), event.rawY.toInt())
                // If expanded and tapping background (not parent button), collapse
                if (isExpanded && !isOnParent) {
                    collapse()
                    return@setOnTouchListener true // Consume tap
                }
            }
            false // Pass through when collapsed or tapping parent button
        }
    }
    private fun getViewRectOnScreen(view: View): Rect {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        return Rect(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height)
    }
    private fun setupDragTouchListener() {
        parentButton.setOnTouchListener { v, event ->
            if (!dragEnabled) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    lastDragX = currentDragX
                    lastDragY = currentDragY

                    cancelLongPress()
                    longPressRunnable = Runnable { startDrag() }
                    v.postDelayed(longPressRunnable, longPressTimeout.toLong())
                    false // Allow click if no drag
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        currentDragX = lastDragX + dx
                        currentDragY = lastDragY + dy
                        
                        parentButton.translationX = currentDragX
                        parentButton.translationY = currentDragY
                        
                        if (isExpanded) {
                            childButtons.forEachIndexed { index, child ->
                                val offset = getOffset(index)
                                child.translationX = currentDragX + offset[0]
                                child.translationY = currentDragY + offset[1]
                            }
                        }
                        true
                    } else {
                        if (abs(event.rawX - downX) > touchSlop || abs(event.rawY - downY) > touchSlop) {
                            cancelLongPress()
                        }
                        false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        isDragging = false
                        snapToNearestEdge(true)
                        
                        if (autoDirection) updateDirectionFromEdge()
                        
                        true
                    } else {
                        cancelLongPress()
                        false // Let the click happen
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        isDragging = false
                        snapToNearestEdge(true)
                        
                        if (autoDirection) updateDirectionFromEdge()
                        
                    } else {
                        cancelLongPress()
                    }
                    false
                }
                else -> false
            }
        }
    }
    private fun startDrag() {
        if (!dragEnabled) return
        if (isExpanded) collapse()
        isDragging = true
    }
    private fun cancelLongPress() {
        longPressRunnable?.let { parentButton.removeCallbacks(it) }
    }
    private fun cacheBasePosition() {
        container.post {
            val loc = IntArray(2)
            parentButton.getLocationOnScreen(loc)
            layoutX = loc[0].toFloat()
            layoutY = loc[1].toFloat()
            snapImmediate()
        }
    }
    private fun getTargetDragOffsets(): FloatArray {
        val pw = parentButton.width.toFloat()
        val ph = parentButton.height.toFloat()
        val margin = snapMargin

        val currentScreenX = layoutX + currentDragX
        val currentScreenY = layoutY + currentDragY

        val targetScreenX = when (anchoredEdge) {
            Gravity.START -> margin
            Gravity.END -> screenWidth - margin - pw
            else -> currentScreenX
        }
        
        val targetScreenY = when (anchoredEdge) {
            Gravity.TOP -> margin
            Gravity.BOTTOM -> screenHeight - margin - ph
            else -> currentScreenY
        }

        return floatArrayOf(targetScreenX - layoutX, targetScreenY - layoutY)
    }
    private fun snapToNearestEdge(animate: Boolean) {
        val pw = parentButton.width.toFloat()
        val ph = parentButton.height.toFloat()
        
        val currentScreenX = layoutX + currentDragX
        val currentScreenY = layoutY + currentDragY

        val distLeft = currentScreenX
        val distRight = screenWidth - (currentScreenX + pw)
        val distTop = currentScreenY
        val distBottom = screenHeight - (currentScreenY + ph)

        val minDist = min(min(distLeft, distRight), min(distTop, distBottom))

        anchoredEdge = when (minDist) {
            distLeft -> Gravity.START
            distRight -> Gravity.END
            distTop -> Gravity.TOP
            else -> Gravity.BOTTOM
        }

        val targets = getTargetDragOffsets()
        val targetDragX = targets[0]
        val targetDragY = targets[1]

        if (animate) {
            parentButton.animate().translationX(targetDragX).translationY(targetDragY).setDuration(200).start()
            if (isExpanded) {
                childButtons.forEachIndexed { index, child ->
                    val offset = getOffset(index)
                    child.animate()
                        .translationX(targetDragX + offset[0])
                        .translationY(targetDragY + offset[1])
                        .setDuration(200)
                        .start()
                }
            }
        } else {
            parentButton.translationX = targetDragX
            parentButton.translationY = targetDragY
            if (isExpanded) {
                childButtons.forEachIndexed { index, child ->
                    val offset = getOffset(index)
                    child.translationX = targetDragX + offset[0]
                    child.translationY = targetDragY + offset[1]
                }
            }
        }
    }
    // Returns the absolute screen coordinates (top-left corner) of the parent button.
    fun getAbsolutePosition(): android.graphics.Point {
        val loc = IntArray(2)
        parentButton.getLocationOnScreen(loc)
        return android.graphics.Point(loc[0], loc[1])
    }
     // Restores the menu to a previously saved absolute screen position.
    fun restoreAbsolutePosition(x: Int, y: Int) {
        container.post {
            // 1. Temporarily remove translation to find the true base layout position
            val tempTx = parentButton.translationX
            val tempTy = parentButton.translationY
            parentButton.translationX = 0f
            parentButton.translationY = 0f
            
            val loc = IntArray(2)
            parentButton.getLocationOnScreen(loc)
            val baseX = loc[0]
            val baseY = loc[1]
            // 2. Restore temporary translation
            parentButton.translationX = tempTx
            parentButton.translationY = tempTy
            // 3. Calculate new drag offsets based on saved absolute position
            currentDragX = (x - baseX).toFloat()
            currentDragY = (y - baseY).toFloat()
            // 4. Apply to parent
            parentButton.translationX = currentDragX
            parentButton.translationY = currentDragY
            // 5. Apply to children if currently expanded
            if (isExpanded) {
                childButtons.forEachIndexed { index, child ->
                    val offset = getOffset(index)
                    child.translationX = currentDragX + offset[0]
                    child.translationY = currentDragY + offset[1]
                }
            }
            // 6. Snap to nearest edge to clamp within screen bounds and update anchoredEdge
            snapImmediate()
            if (autoDirection) updateDirectionFromEdge()
        }
    }
    // --- Utils ---
    private fun dpToPx(dp: Float): Float = dp * container.resources.displayMetrics.density
}