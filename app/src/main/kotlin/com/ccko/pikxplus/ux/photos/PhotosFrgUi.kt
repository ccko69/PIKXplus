package com.ccko.pikxplus.ux.photos

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.ccko.pikxplus.R
import com.ccko.pikxplus.shared.data.MediaItems
import com.google.android.material.appbar.CollapsingToolbarLayout
import me.zhanghai.android.fastscroll.DefaultAnimationHelper
import me.zhanghai.android.fastscroll.FastScrollerBuilder

/** Sets up the RecyclerView and its LayoutManager. */
fun PhotosFrg.setupRecyclerViewUi(recyclerView: RecyclerView, currentSpanCount: Int) {
    val layoutManager = GridLayoutManager(recyclerView.context, currentSpanCount)
    layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int = 1
    }
    recyclerView.layoutManager = layoutManager
    recyclerView.setHasFixedSize(true)
    recyclerView.scrollToPosition(0)
}
/** Configures the draggable fast-scroll thumb and track. */
fun PhotosFrg.setupFastScrollerUi(recyclerView: RecyclerView) {
    val density = recyclerView.resources.displayMetrics.density
    
    val trackDrawable = GradientDrawable().apply {
        setColor(Color.TRANSPARENT) // Track — transparent
        setSize(0, 1)               // 0 width = invisible; height just needs to be >= 0
    }
    val animHelper = object : DefaultAnimationHelper(recyclerView) {
        override fun isScrollbarAutoHideEnabled() = true
        override fun getScrollbarAutoHideDelayMillis() = 5000 // Auto-hide delay - ms
    }
    val thumbDrawable = GradientDrawable().apply {
        // Note: Discarded ContextCompat.getDrawable call removed to prevent memory waste
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 4f * density
        setColor(Color.argb(160, 255, 255, 255)) // adjust alpha (0–255) for transparency
        setSize((20 * density).toInt(), (44 * density).toInt()) // 20dp wide, 44dp tall
    }
    FastScrollerBuilder(recyclerView)
        .useDefaultStyle()
        .setPadding(0, 0, 8, 0)
        .setTrackDrawable(trackDrawable)
        .setThumbDrawable(thumbDrawable)
        // .setAnimationHelper(animHelper) // Uncomment if custom animation helper is desired
        .build()
}
/** Shows/hides the scroll-to-top button with a smooth fade animation. */
fun PhotosFrg.updateScrollTopVisibilityUi(rv: RecyclerView, btnScrollTop: View) {
    val scrolledPastThreshold = rv.computeVerticalScrollOffset() > rv.height
    if (scrolledPastThreshold && btnScrollTop.visibility != View.VISIBLE) {
        btnScrollTop.visibility = View.VISIBLE
        btnScrollTop.alpha = 0f
        btnScrollTop.animate().alpha(1f).setDuration(200).start()
    } else if (!scrolledPastThreshold && btnScrollTop.visibility != View.GONE) {
        btnScrollTop.animate().alpha(0f).setDuration(200)
            .withEndAction { btnScrollTop.visibility = View.GONE }
            .start()
    }
}
/** Loads the header image using Glide, applying the requested transformations. */
fun PhotosFrg.initHeaderUi(
    context: Context,
    collapsingToolbar: CollapsingToolbarLayout,
    headerImage: android.widget.ImageView,
    name: String?,
    mediaList: List<MediaItems>
) {
    collapsingToolbar.title = name ?: "Album"
    if (mediaList.isNotEmpty()) {
        val firstItem = mediaList.first()
        Glide.with(context)
            .load(firstItem.uri)
            .frame(200)
            .centerCrop()
            .optionalFitCenter()
            .optionalCenterInside()
            .useAnimationPool(true)
            .dontAnimate()
            .override(800, 800)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(R.drawable.ic_broken_image)
            .encodeQuality(50)
            .into(headerImage)
    } else {
        headerImage.setImageResource(R.drawable.ic_broken_image)
    }
}
/** Updates the grid/list span count dynamically. */
fun PhotosFrg.updateGridSpanUi(recyclerView: RecyclerView, adapter: PhotosAdpt, span: Int) {
    val isGridView = span > 1
    val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
    
    lm.spanCount = span
    lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int = 1
    }
    
    adapter.updateSpanCount(span, isGridView)
}