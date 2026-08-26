package com.ccko.pikxplus.viewers.img

import android.graphics.drawable.Animatable
import android.os.Build
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.load
import com.ccko.pikxplus.MainActivity
import com.ccko.pikxplus.R
import com.ccko.pikxplus.shared.data.MediaItems
import com.ccko.pikxplus.shared.utils.ImgClrAdjst
import com.ccko.pikxplus.shared.utils.PulseOrbView
import com.ccko.pikxplus.viewers.img.VpAdpt
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.helper.ImageMatrixController
import java.lang.ref.WeakReference
// import com.ccko.pikxplus.shared.utils.ImgMatrixCtrl

/**
 * ViewPager2 adapter for ImgFrg.
 *
 * View types:
 *   - TYPE_IMAGE_REGULAR (0): ImageView + ImageMatrixController (primary for normal images)
 *   - TYPE_IMAGE_LARGE   (1): SubsamplingScaleImageView (fallback for huge images >4096px)
 *   - TYPE_VIDEO         (2): Static thumbnail + play button overlay
 *
 * Gesture: The same ImgGstHandler touch listener is attached to both image view types.
 * For SSIV pages, it returns false → SSIV handles natively.
 * For ImageView pages, the handler drives the attached ImageMatrixController.
 */
class VpAdpt(
  private var mediaList: List<MediaItems>,
  private val imageTouchListener: View.OnTouchListener?,
  private val onImageReady: (Int) -> Unit
) : RecyclerView.Adapter<VpAdpt.ViewHolder>() {

  companion object {
    private const val TYPE_IMAGE_REGULAR = 0
    private const val TYPE_IMAGE_LARGE = 1
    private const val TYPE_VIDEO = 2
    private const val LARGE_IMAGE_THRESHOLD = 4096
  }
  // Map to retrieve the ZoomablePage wrapper for a given position (used by ImgFrg)
  private val zoomablePageMap = SparseArray<ZoomablePage>()
  private var currentPosition = 0
  private val activeVideoHolders = mutableSetOf<WeakReference<VideoViewHolder>>()
  
  // Load saved values
  // private val prefs = MainActivity.prefs
  // val prefBrightness = prefs.getFloat("img_adj_brightness", 0f)
  // val prefContrast   = prefs.getFloat("img_adj_contrast", 1f)
  // val prefSaturation = prefs.getFloat("img_adj_saturation", 1f)

  /** Returns the ZoomablePage currently bound at [position], or null. */
  fun getZoomablePageAt(position: Int): ZoomablePage? = zoomablePageMap[position]
  fun submitList(newList: List<MediaItems>) {
    mediaList = newList
    notifyDataSetChanged()
  }
  fun setCurrentPosition(position: Int) {
    currentPosition = position
    for (ref in activeVideoHolders) {
      val holder = ref.get()
      if (holder?.adapterPosition == position) {
        holder.startPlayback()
      } else {
        holder?.pausePlayback()
      }
    }
  }
  fun pauseAllVideos() {
    for (ref in activeVideoHolders) { ref.get()?.pausePlayback() }
  }
  fun releaseAllPlayers() {
    for (ref in activeVideoHolders) { ref.get()?.releasePlayer() }
    activeVideoHolders.clear()
  }

  override fun getItemCount(): Int = mediaList.size

  override fun getItemViewType(position: Int): Int {
    val item = mediaList[position]
    return when {
      item.isVideo() -> TYPE_VIDEO
      // Animated or WebP always use regular ImageView (SSIV decodes only first frame)
      item.isAnimated() || item.mimeType.equals("image/webp", ignoreCase = true) -> TYPE_IMAGE_REGULAR
      // Huge dimensions -> SSIV fallback
      item.width > LARGE_IMAGE_THRESHOLD || item.height > LARGE_IMAGE_THRESHOLD -> TYPE_IMAGE_LARGE
      else -> TYPE_IMAGE_REGULAR
    }
  }
  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    return when (viewType) {
      TYPE_IMAGE_REGULAR -> {
        val view = inflater.inflate(R.layout.item_viewer_image, parent, false)
        ImageViewHolderRegular(view)
      }
      TYPE_IMAGE_LARGE -> {
        val view = inflater.inflate(R.layout.item_viewer_page, parent, false)
        ImageViewHolderLarge(view)
      }
      TYPE_VIDEO -> {
        val view = inflater.inflate(R.layout.item_viewer_page, parent, false)
        VideoViewHolder(view)
      }
      else -> throw IllegalArgumentException("Unknown view type: $viewType")
    }
  }
  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bind(mediaList[position], position)
  }
  override fun onViewRecycled(holder: ViewHolder) {
    super.onViewRecycled(holder)
    zoomablePageMap.remove(holder.bindingAdapterPosition)
  }
  // ===== VIEW HOLDERS =====
  abstract class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(item: MediaItems, position: Int)
  }
  // ----- Regular Image (ImageView + ImageMatrixController) -----
  inner class ImageViewHolderRegular(itemView: View) : ViewHolder(itemView) {
    private val imageView: ImageView = itemView.findViewById(R.id.imageView)
    private val progress: PulseOrbView = itemView.findViewById(R.id.progress)

    private var matrixController: ImageMatrixController? = null

    override fun bind(item: MediaItems, position: Int) {
      // Create and register ZoomablePage wrapper
      val zoomable = ImageViewZoomablePage(imageView, itemView.context).also {
        matrixController = it.controller
      }
      zoomablePageMap.put(position, zoomable)

      // Attach touch listener (same for all types)
      imageView.setOnTouchListener(imageTouchListener)

      // Reset state
      imageView.visibility = View.GONE
      progress.visibility = View.VISIBLE
      progress.start()
      imageView.setImageDrawable(null)

      // Load with Coil (handles all formats, including animated)
      loadWithCoil(item, position)
    }
    private fun loadWithCoil(item: MediaItems, position: Int) {
      imageView.load(item.uri) {
        crossfade(true)
        placeholder(R.drawable.ic_broken_image)
        error(R.drawable.ic_broken_image)
        size(2048)

        if (item.isAnimated()) {
          if (Build.VERSION.SDK_INT >= 28) {
            decoderFactory(ImageDecoderDecoder.Factory())
          } else {
            decoderFactory(GifDecoder.Factory())
          }
          allowHardware(false)
        }

        listener(
          onSuccess = { _, result ->
            progress.visibility = View.GONE
            progress.stop()
            imageView.visibility = View.VISIBLE
            onImageReady(position)

            // Wait for layout, then fit to screen
            imageView.post {
              matrixController?.resetToFit(false)
            }

            (result.drawable as? Animatable)?.start()
          },
          onError = { _, _ ->

            progress.visibility = View.GONE
            imageView.setImageResource(R.drawable.ic_broken_image)
            imageView.visibility = View.VISIBLE
            progress.stop()
          }
        )
      }
    }
  }
  // ----- Large Image (SSIV fallback) -----
  inner class ImageViewHolderLarge(itemView: View) : ViewHolder(itemView) {
    private val ssImageView: SubsamplingScaleImageView = itemView.findViewById(R.id.scaleImageView)
    private val progress: PulseOrbView = itemView.findViewById(R.id.progress)

    override fun bind(item: MediaItems, position: Int) {
      val zoomable = SsivZoomablePage(ssImageView)
      zoomablePageMap.put(position, zoomable)

      ssImageView.setOnTouchListener(imageTouchListener)

      ssImageView.visibility = View.GONE
      progress.visibility = View.VISIBLE
      progress.start()
      ssImageView.recycle()

      try {
        ssImageView.setImage(ImageSource.uri(item.uri).tilingEnabled())
        ssImageView.visibility = View.VISIBLE
        progress.visibility = View.GONE
        progress.stop()
        onImageReady(position)
      } catch (e: Exception) {
        // Fallback to Coil if SSIV fails (should not happen often)
        fallbackToCoil(item, position)
      }
    }
    private fun fallbackToCoil(item: MediaItems, position: Int) {
      val imageView = ImageView(itemView.context)
      imageView.load(item.uri) {
        crossfade(true)
        size(2048)
        listener(onSuccess = { _, _ ->
            progress.visibility = View.GONE
            progress.stop()
            ssImageView.visibility = View.GONE
        })
      }
    }
  }
  // ----- Video -----
  inner class VideoViewHolder(itemView: View) : ViewHolder(itemView) {
    private val playerView: PlayerView = itemView.findViewById(R.id.playerView)
    private val progress: PulseOrbView = itemView.findViewById(R.id.progress)
    private var player: ExoPlayer? = null
    private var currentPosition = -1
    private var playerListener: Player.Listener? = null

    private val attachListener = object : View.OnAttachStateChangeListener {
      override fun onViewAttachedToWindow(v: View) {
        activeVideoHolders.add(WeakReference(this@VideoViewHolder))
        if (currentPosition == adapterPosition) {
          // if (currentPosition == currentPositionGlobal) {
          startPlayback()
        }
      }
      override fun onViewDetachedFromWindow(v: View) {
        activeVideoHolders.removeAll { it.get() == this@VideoViewHolder }
        pausePlayback()
      }

    }
    init {
      itemView.addOnAttachStateChangeListener(attachListener)
    }
    override fun bind(item: MediaItems, position: Int) {
      currentPosition = position
      zoomablePageMap.put(position, VideoZoomablePage()) // add dummy zoomable
      // Set touch listener for gestures (long press etc.)
      playerView.setOnTouchListener(imageTouchListener)

      val context = itemView.context
      // Create or reuse player
      if (player == null) {
        player = ExoPlayer.Builder(context).build().apply {
          repeatMode = Player.REPEAT_MODE_ONE
          volume = 0f
        }
        playerView.player = player
        playerView.setControllerShowTimeoutMs(0)
        // Custom controller layout (see section 2)
        // playerView.setControllerLayoutId(R.layout.exo_custom_controls) // optional
      }
      // Stop and clear previous media
      player?.stop()
      player?.clearMediaItems()
      // Set new media
      player?.setMediaItem(MediaItem.fromUri(item.uri))
      player?.prepare()
      // Show loading indicator
      progress.visibility = View.VISIBLE
      progress.start()
      playerView.visibility = View.GONE
      // Remove old listener to avoid duplicates
      playerListener?.let { player?.removeListener(it) }
      // Create new listener
      playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
          if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
            progress.visibility = View.GONE
            progress.stop()
            playerView.visibility = View.VISIBLE
          }
        }
      }
      player?.addListener(playerListener!!)
      // If this position is currently active, start playback
      if (position == adapterPosition) {
        startPlayback()
      }
    }
    fun startPlayback() {
      player?.play()
    }
    fun pausePlayback() {
      player?.pause()
    }
    fun releasePlayer() {
      playerListener?.let { player?.removeListener(it) }
      playerListener = null
      player?.release()
      player = null
      playerView.player = null
      itemView.removeOnAttachStateChangeListener(attachListener) // avoid leaks
    }
    fun onViewRecycled() {
      // Pause and release when recycled
      releasePlayer()
      // Remove from map
      zoomablePageMap.remove(currentPosition)
    }
  }
}
// ===== ZoomablePage Interface and Implementations =====
/**
     * Unified interface for zoom/pan operations across SSIV and ImageView+Controller.
     */
interface ZoomablePage {
  fun getScale(): Float
  fun getFillScale(): Float
  fun getMinScale(): Float
  fun getMaxScale(): Float
  fun zoomTo(scale: Float, focusX: Float, focusY: Float, animate: Boolean)
  fun panBy(dx: Float, dy: Float)
  fun resetToFit(animate: Boolean)
  fun setOnScaleChangedListener(listener: (Float) -> Unit)
  fun rotateBy(degrees: Float)          // incremental rotation (animated)
  fun getRotationDegrees(): Float       // current rotation angle in degrees (0..360)
  fun mirrorHorizontal(animate: Boolean)
  fun mirrorVertical(animate: Boolean)
  fun autoEnhance()
  fun reset()
}
class VideoZoomablePage : ZoomablePage {
  override fun getScale(): Float = 1f
  override fun getFillScale(): Float = 1f
  override fun getMinScale(): Float = 1f
  override fun getMaxScale(): Float = 1f
  override fun zoomTo(scale: Float, focusX: Float, focusY: Float, animate: Boolean) { /* no-op */ }
  override fun panBy(dx: Float, dy: Float) { /* no-op */ }
  override fun resetToFit(animate: Boolean) { /* no-op */ }
  override fun setOnScaleChangedListener(listener: (Float) -> Unit) { /* no-op */ }
  override fun rotateBy(degrees: Float) { /* no-op */ }
  override fun getRotationDegrees(): Float = 0f
  override fun mirrorHorizontal(animate: Boolean) { /* no-op */ }
  override fun mirrorVertical(animate: Boolean) { /* no-op */ }
  override fun autoEnhance() { /* no-op */ }
  override fun reset() { /* no-op */ }
}

/** Wrapper for SubsamplingScaleImageView. */
class SsivZoomablePage(private val ssiv: SubsamplingScaleImageView) : ZoomablePage {
  val adjuster: ImgClrAdjst = ImgClrAdjst(ssiv).apply {
  val prefs = MainActivity.prefs
    setBrightness(prefs.getFloat("img_adj_brightness", 0f))
    setContrast(prefs.getFloat("img_adj_contrast", 1f))
    setSaturation(prefs.getFloat("img_adj_saturation", 1f))
  }
  init {
    ssiv.setDoubleTapZoomScale(1.2f)
    ssiv.setDoubleTapZoomDpi(120)
    ssiv.setDoubleTapZoomDuration(500)
    // setQuickScaleEnabled(true)
  }
  override fun getScale():     Float = ssiv.scale
  override fun getFillScale(): Float = ssiv.scale
  override fun getMinScale():  Float = ssiv.minScale
  override fun getMaxScale():  Float = ssiv.maxScale
  override fun zoomTo(scale: Float, focusX: Float, focusY: Float, animate: Boolean) {
    val targetCenter = android.graphics.PointF(focusX, focusY)
    if (animate) {
      ssiv.animateScaleAndCenter(scale, targetCenter)?.start()
    } else {
      ssiv.setScaleAndCenter(scale, targetCenter)
    }
  }
  override fun panBy(dx: Float, dy: Float) {
    // SSIV handles pan internally via touch
  }
  override fun resetToFit(animate: Boolean) {
    if (animate) {
      ssiv.animateScale(ssiv.minScale)?.start()
    } else {
      ssiv.resetScaleAndCenter()
    }
  }
  override fun setOnScaleChangedListener(listener: (Float) -> Unit) {
    ssiv.setOnStateChangedListener(object : SubsamplingScaleImageView.OnStateChangedListener {
        override fun onScaleChanged(newScale: Float, origin: Int) {
          listener(newScale)
        }
        override fun onCenterChanged(newCenter: android.graphics.PointF?, origin: Int) {}
    })
  }
  override fun rotateBy(degrees: Float) {
    val newOri = (ssiv.orientation + degrees.toInt() + 360) % 360
    ssiv.setOrientation(newOri)
  }
  override fun getRotationDegrees(): Float = ssiv.orientation.toFloat()
  override fun mirrorHorizontal(animate: Boolean) {}
  override fun mirrorVertical(animate: Boolean) {}
  override fun autoEnhance() {adjuster.autoEnhance()}
  override fun reset() { adjuster.reset() }
}

/** Wrapper for ImageView + ImageMatrixController.java. */
class ImageViewZoomablePage(
  private val imageView: ImageView,
  context: android.content.Context
) : ZoomablePage {
  val adjuster: ImgClrAdjst = ImgClrAdjst(imageView).apply {
  val prefs = MainActivity.prefs
    setBrightness(prefs.getFloat("img_adj_brightness", 0f))
    setContrast(prefs.getFloat("img_adj_contrast", 1f))
    setSaturation(prefs.getFloat("img_adj_saturation", 1f))
  }
  // val controller: ImageMatrixController = ImageMatrixController(imageView).apply {
  val controller: ImageMatrixController = ImageMatrixController(imageView).apply {
    setMinScale(1f, ImageMatrixController.ScaleReference.VIEW)
    setMaxScale(20f, ImageMatrixController.ScaleReference.VIEW)
    setDefaultAnimDuration(500)
  }
  // override fun getFill(): Float = controller.getFillScale()
  override fun getScale(): Float = controller.scale
  override fun getFillScale(): Float = controller.getFillScale()
  override fun getMinScale(): Float = controller.getEffectiveMinScale()
  override fun getMaxScale(): Float = controller.getEffectiveMaxScale()
  override fun zoomTo(scale: Float, focusX: Float, focusY: Float, animate: Boolean) {
    controller.zoomTo(scale, focusX, focusY, animate)
  }
  override fun panBy(dx: Float, dy: Float) {
    if (getScale() > getMinScale() * 1.01f) {
      controller.panBy(-dx, -dy)
    }
  }
  override fun resetToFit(animate: Boolean) {
    controller.resetToFit(animate)
  }
  override fun setOnScaleChangedListener(listener: (Float) -> Unit) {
    controller.setOnMatrixChangeListener { _, scale -> listener(scale) }
  }
  override fun rotateBy(degrees: Float) {
    controller.rotateBy(degrees, true)
  }
  override fun getRotationDegrees(): Float {
    val m = controller.getMatrix()
    val vals = FloatArray(9)
    m.getValues(vals)
    val angleRad = Math.atan2(
      vals[android.graphics.Matrix.MSKEW_Y].toDouble(),
      vals[android.graphics.Matrix.MSCALE_X].toDouble()
    )
    return Math.toDegrees(angleRad).toFloat()
  }
  override fun mirrorHorizontal(animate: Boolean) {
    controller.mirrorHorizontal(animate)
  }
  override fun mirrorVertical(animate: Boolean) {
    controller.mirrorVertical(animate)
  }
  override fun autoEnhance() {adjuster.autoEnhance()}
  override fun reset() {adjuster.reset()}
}
