package com.ccko.pikxplus.ux.albums
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.ccko.pikxplus.R
import com.ccko.pikxplus.shared.data.AlbumInfo
class AlbumsAdpt(
  private val onAlbumClick: (AlbumInfo) -> Unit,
  private val onAlbumLongClick: ((AlbumInfo) -> Unit)? = null,
  private val isGridMode: () -> Boolean = { false }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
  sealed class Item {
    data class Header(val title: String) : Item()
    data class Album(val albumInfo: AlbumInfo) : Item()
  }
  /**
   * Selection mode for the toolbar.
   * - [NONE]: normal browsing. Tapping an album opens it; long-press toggles bookmark.
   * - [SELECT]: per-item toggle UI. Both bookmark and visibility icons are always
   *   visible on every album and tappable. Hidden albums remain visible so they
   *   can be un-hidden. Tapping the album body itself is a no-op.
   */
  enum class SelectionMode { NONE, SELECT }
  // External state owned by AlbumsFrg.
  private var selectionMode: SelectionMode = SelectionMode.NONE
  // Per-item callbacks used in SELECT mode. Optional so the adapter still works
  // when the caller doesn't care about per-item toggles.
  var onBookmarkToggle: ((AlbumInfo) -> Unit)? = null
  var onHiddenToggle: ((AlbumInfo) -> Unit)? = null
  /** Optional visual feedback hook (e.g. a 120ms alpha fade) on the toggled icon. */
  var onIndicatorToggled: ((View) -> Unit)? = null
  private val items = mutableListOf<Item>()
  fun setSelectionMode(mode: SelectionMode) {
    if (selectionMode == mode) return
    selectionMode = mode
    notifyDataSetChanged()
  }
  fun getSelectionMode(): SelectionMode = selectionMode
  fun submitList(newItems: List<Item>) {
    items.clear()
    items.addAll(newItems)
    notifyDataSetChanged()
  }
  /** Read-only snapshot of the current item list. Useful for bulk operations. */
  fun currentItems(): List<Item> = items.toList()
  override fun getItemViewType(position: Int): Int =
  when (items[position]) {
    is Item.Header -> TYPE_HEADER
    is Item.Album -> TYPE_ALBUM
  }
  /** Header spans the full row in grid mode; items always span 1. */
  fun spanSize(position: Int, spanCount: Int): Int =
  if (isGridMode() && getItemViewType(position) == TYPE_HEADER) spanCount else 1
  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    return when (viewType) {
      TYPE_HEADER -> {
        val view = inflater.inflate(R.layout.item_album_header, parent, false)
        HeaderVH(view)
      }
      else -> {
        val view = inflater.inflate(R.layout.item_album, parent, false)
        AlbumVH(view)
      }
    }
  }
  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    when (val item = items[position]) {
      is Item.Header -> (holder as HeaderVH).bind(item)
      is Item.Album -> (holder as AlbumVH).bind(item.albumInfo)
    }
  }
  override fun getItemCount(): Int = items.size
  inner class HeaderVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val title: TextView = itemView.findViewById(R.id.albumHeaderTitle)
    fun bind(item: Item.Header) {
      title.text = item.title
    }
  }
  inner class AlbumVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val albumName: TextView = itemView.findViewById(R.id.albumName)
    private val bookmarkIndicator: ImageView = itemView.findViewById(R.id.bookmarkIndicator)
    private val visibilityIndicator: ImageView = itemView.findViewById(R.id.visibilityIndicator)
    private val photoCount: TextView = itemView.findViewById(R.id.photoCount)
    private val photoIcon: ImageView = itemView.findViewById(R.id.photoIcon)
    private val photoCountContainer: View = itemView.findViewById(R.id.photoCountContainer)
    private val videoCount: TextView = itemView.findViewById(R.id.videoCount)
    private val videoIcon: ImageView = itemView.findViewById(R.id.videoIcon)
    private val videoCountContainer: View = itemView.findViewById(R.id.videoCountContainer)
    private val storageIcon: ImageView = itemView.findViewById(R.id.storageIcon)
    private val albumThumbnail: ImageView = itemView.findViewById(R.id.albumThumbnail)
    private val albumContainer: View = itemView.findViewById(R.id.albumContainer)
    private var currentAlbum: AlbumInfo? = null
    init {
        // Move click listeners to init to prevent memory allocation on every scroll
        albumContainer.setOnClickListener {
            if (selectionMode == SelectionMode.NONE) {
                currentAlbum?.let { onAlbumClick(it) }
            }
        }
        albumContainer.setOnLongClickListener {
            if (selectionMode == SelectionMode.NONE) {
                currentAlbum?.let { onAlbumLongClick?.invoke(it) }
            }
            true
        }
    }
    @Suppress("DEPRECATION")
    fun bind(album: AlbumInfo) {
        currentAlbum = album
        val inSelect = selectionMode == SelectionMode.SELECT
        val isGrid = isGridMode()
        albumName.text = album.name
        
        bookmarkIndicator.visibility = if (inSelect || album.isBookmarked) View.VISIBLE else View.GONE
        visibilityIndicator.visibility = if (inSelect || album.isHidden) View.VISIBLE else View.GONE
        bookmarkIndicator.isSelected = album.isBookmarked
        visibilityIndicator.isSelected = album.isHidden
        bookmarkIndicator.isClickable = inSelect
        visibilityIndicator.isClickable = inSelect

        bookmarkIndicator.setOnClickListener {
            if (inSelect) {
                onBookmarkToggle?.invoke(album)
                onIndicatorToggled?.invoke(bookmarkIndicator)
            }
        }
        visibilityIndicator.setOnClickListener {
            if (inSelect) {
                onHiddenToggle?.invoke(album)
                onIndicatorToggled?.invoke(visibilityIndicator)
            }
        }
        // --- CLEANED UP VISIBILITY LOGIC ---
        val hasPhotos = album.photoCount > 0
        photoCount.text = album.photoCount.toString()
        setViewsVisibility(hasPhotos, photoCount, photoIcon, photoCountContainer)
        val hasVideos = album.videoCount > 0
        videoCount.text = album.videoCount.toString()
        setViewsVisibility(hasVideos, videoCount, videoIcon, videoCountContainer)
        // --- MODE SPECIFIC UI ---
        if (isGrid) {
            albumName.textSize = 12f
            albumName.maxLines = 1
            photoCount.textSize = 10f
            videoCount.textSize = 10f
            storageIcon.visibility = View.GONE
            Glide.with(albumThumbnail.context)
            .load(album.thumbnailUri)
            .frame(0)
            .dontAnimate()
            .override(120, 120)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .centerCrop()
            .thumbnail(0.1f)
            .encodeQuality(70)
            .placeholder(R.drawable.ic_broken_image)
            .into(albumThumbnail)
            
            val lp = albumContainer.layoutParams
            lp.width = lp.height 
            albumContainer.layoutParams = lp
        } else {
            storageIcon.visibility = View.VISIBLE
            storageIcon.setImageResource(if (album.isOnSdCard) R.drawable.ic_sd_card else R.drawable.ic_internal_storage)
            Glide.with(albumThumbnail.context)
            .load(album.thumbnailUri)
            .frame(0)
            .dontAnimate()
            .override(240, 240)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .centerCrop()
            .thumbnail(0.1f)
            .encodeQuality(70)
            .placeholder(R.drawable.ic_broken_image)
            .into(albumThumbnail)
            
            val lp = albumContainer.layoutParams
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            albumContainer.layoutParams = lp
        }
    }
}

// Helper function placed inside the Adapter class
private fun setViewsVisibility(isVisible: Boolean, vararg views: View) {
    val visibility = if (isVisible) View.VISIBLE else View.GONE
    views.forEach { it.visibility = visibility }
}


  /** Install a SpanSizeLookup so headers span the full row in grid mode. */
  fun installSpanSizeLookup(layoutManager: GridLayoutManager) {
    layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
      override fun getSpanSize(position: Int): Int =
      this@AlbumsAdpt.spanSize(position, layoutManager.spanCount)
    }
  }
  companion object {
    private const val TYPE_HEADER = 0
    private const val TYPE_ALBUM = 1
  }
}
