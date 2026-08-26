package com.ccko.pikxplus.ux.photos

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ccko.pikxplus.R
import com.ccko.pikxplus.shared.data.MediaItems
import java.util.Locale
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

/**
 * RecyclerView Adapter for PhotosFragment.
 * View types:
 *   1 = Grid media item
 *   2 = List media item
 */
class PhotosAdpt(
    private val onMediaClick: (Int) -> Unit,
    private val detectWebP: (MediaItems) -> Unit = {},
    private val stripMode: Boolean = false
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TAG = "PhotosAdapter"
        private const val TYPE_GRID = 1
        private const val TYPE_LIST = 2
        private const val TYPE_STRIP = 3
    }
    private var mediaList: List<MediaItems> = emptyList()
    private var albumName: String? = null
    private var isGridView = true
    private var currentSpanCount =     6
    private var currentViewingIndex = -1 // Index of the item currently open in ImgFrg (-1 = none)
    // ===== PUBLIC API =====
    fun submitList(newList: List<MediaItems>, albumName: String? = null) {
        mediaList = newList
        // this.albumName = albumName
        notifyDataSetChanged()
    } 
    
    fun submitListAdpt(newList: List<MediaItems>, albumName: String? = null) {
        this.albumName = albumName
        submitList(newList) // ListAdapter's submitList
    }
    /**
     * Called by PhotosFrg whenever ImgFrg reports its current swipe position.
     * Highlights that item in the grid/list without scrolling.
     */
    fun setCurrentViewingIndex(index: Int) {
        val old = currentViewingIndex
        currentViewingIndex = index
        // Only rebind the two affected positions to avoid full list refresh
        if (old >= 0)   notifyItemChanged(old)
        if (index >= 0) notifyItemChanged(index)
    }
    fun updateSpanCount(spanCount: Int, isGrid: Boolean) {
        val modeChanged = isGrid != isGridView
        currentSpanCount = spanCount
        isGridView = isGrid
        if (modeChanged) {
            // Full rebind needed because ViewHolder type changes
            notifyDataSetChanged()
        } else if (itemCount > 1) {
            notifyItemRangeChanged(1, itemCount - 1)
        }
    }

    fun getIsGridView(): Boolean = isGridView
    // ===== RECYCLER CORE =====
    override fun getItemCount(): Int = if (mediaList.isEmpty()) 0 else mediaList.size
    /**
     * Returns TYPE_GRID or TYPE_LIST so RecyclerView maintains
     * separate recycled pools and never mixes ViewHolder types.
     */
    override fun getItemViewType(position: Int): Int = when {
        stripMode  -> TYPE_STRIP
        isGridView -> TYPE_GRID
        else       -> TYPE_LIST
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return try {
            when (viewType) {
                TYPE_GRID -> {
                    val view = inflater.inflate(R.layout.item_photo_grid, parent, false)
                    GridViewHolder(view)
                }
                TYPE_LIST -> {
                    val view = inflater.inflate(R.layout.item_photo_list, parent, false)
                    ListViewHolder(view)
                }
                TYPE_STRIP -> {
                    val view = inflater.inflate(R.layout.item_strip_thumb, parent, false)
                    StripViewHolder(view)
                }
                else -> throw IllegalArgumentException("Unknown view type: $viewType")
            }
        } catch (e: Exception) {
            Log.e(TAG, "onCreateViewHolder failed for viewType=$viewType", e)
            throw e
        }
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        try {
            when (holder) {
                is GridViewHolder   -> {
                    val mediaPos = position
                    if (mediaPos in mediaList.indices) {
                        holder.bind(mediaList[mediaPos], mediaPos, mediaPos == currentViewingIndex)
                    }
                }
                is ListViewHolder   -> {
                    val mediaPos = position
                    if (mediaPos in mediaList.indices) {
                        holder.bind(mediaList[mediaPos], mediaPos, mediaPos == currentViewingIndex)
                    }
                }
                is StripViewHolder  -> {
                    val mediaPos = position
                    if (mediaPos in mediaList.indices) {
                        holder.bind(mediaList[mediaPos], mediaPos == currentViewingIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onBindViewHolder failed at position=$position", e)
        }
    }
    // ===== VIEW HOLDERS =====
    inner class GridViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail:        ImageView = itemView.findViewById(R.id.photoThumbnail)
        private val formatIcon:       ImageView = itemView.findViewById(R.id.formatIcon)
        private val videoDuration:    TextView  = itemView.findViewById(R.id.videoDuration)
        private val container:        View      = itemView.findViewById(R.id.photoContainer)
        // private val highlightOverlay: View?     = itemView.findViewById(R.id.highlightOverlay)
        init {
            container.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onMediaClick(pos)
            }
        }
        @Suppress("DEPRECATION")
        fun bind(item: MediaItems, position: Int, isCurrentlyViewing: Boolean) {
            // Format icon
            when {
                item.isVideo()    -> {
                    formatIcon.setImageResource(R.drawable.ic_play_circle)
                    formatIcon.visibility = View.VISIBLE
                }
                item.isAnimated() -> {
                    formatIcon.setImageResource(R.drawable.ic_gif)
                    formatIcon.visibility = View.VISIBLE
                }
                else -> {
                     // formatIcon.setImageResource(R.drawable.ic_photo)
                     formatIcon.visibility = View.GONE
                }
            }
            // Request lazy detection for WebP items (adapter only asks; fragment handles detection)
            if (item.mimeType == "image/webp" && !item.isAnimated()) {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    detectWebP(item)
                }
            }
            // Video duration badge
            if (item.isVideo() && item.duration > 0) {
                videoDuration.visibility = View.VISIBLE
                videoDuration.text = item.getFormattedDuration()
            } else {
                videoDuration.visibility = View.GONE
            }
            // "Currently open in viewer" highlight
            // highlightOverlay?.visibility = if (isCurrentlyViewing) View.VISIBLE else View.GONE
            // Load thumbnail
            Glide.with(itemView.getContext())
            .load(item.uri)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .dontAnimate()
            .centerCrop()
            .optionalFitCenter()
            .optionalCenterInside()
            .useAnimationPool(true)
            .thumbnail(0.1f)    // Load a 10% tiny version first
            .override(200, 200) // Don't decode larger than needed for the grid
         // .encodeQuality(60)  // Cache only the resized version
            .into(thumbnail)
        }
    }
    inner class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail:        ImageView = itemView.findViewById(R.id.photoThumbnail)
        private val title:            TextView  = itemView.findViewById(R.id.photoTitle)
        private val details:          TextView  = itemView.findViewById(R.id.photoDetails)
        private val formatIcon:       ImageView = itemView.findViewById(R.id.formatIcon)
        private val container:        View      = itemView.findViewById(R.id.photoContainer)
        // private val highlightOverlay: View?     = itemView.findViewById(R.id.highlightOverlay)
        init {
            container.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onMediaClick(pos)
            }
        }
        @Suppress("DEPRECATION")
        fun bind(item: MediaItems, position: Int, isCurrentlyViewing: Boolean) {
            title.visibility   = View.VISIBLE
            title.text         = item.name
            details.visibility = View.VISIBLE
            details.text       = buildDetailsText(item)
            when {
                item.isVideo()    -> {
                    formatIcon.setImageResource(R.drawable.ic_play_circle)
                    formatIcon.visibility = View.VISIBLE
                }
                item.isAnimated() -> {
                    formatIcon.setImageResource(R.drawable.ic_gif)
                    formatIcon.visibility = View.VISIBLE
                }
                else -> {
                    formatIcon.setImageResource(R.drawable.ic_photo)
                    formatIcon.visibility = View.VISIBLE
                }
            }
            if (item.mimeType == "image/webp" && !item.isAnimated()) {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    detectWebP(item)
                }
            }
            // Duration text is already in details text for list view
            itemView.findViewById<TextView?>(R.id.videoDuration)?.visibility = View.GONE
            // highlightOverlay?.visibility = if (isCurrentlyViewing) View.VISIBLE else View.GONE
            // Load thumbnail
            Glide.with(itemView.getContext())
            .load(item.uri)
            .dontAnimate()
            .centerCrop()
            .thumbnail(0.1f)
            .override(120, 120)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .encodeQuality(30)
            .into(thumbnail)
        }
        private fun buildDetailsText(item: MediaItems): String {
            return if (item.isVideo()) {
                "${item.getFormattedDuration()} • ${formatSize(item.size)}"
            } else {
                "${item.getFormattedDimensions()} • ${formatSize(item.size)}"
            }
        }
        private fun formatSize(bytes: Long): String {
            return when {
                bytes < 1024L                -> "$bytes B"
                bytes < 1024L * 1024         -> "${bytes / 1024} KB"
                bytes < 1024L * 1024 * 1024  ->
                    String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))
                else ->
                    String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024))
            }
        }
    }
    /**
     * ViewHolder for the horizontal filmstrip in ImgFrg (stripMode = true).
     * Lightweight - just thumbnail.
     */
    inner class StripViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail:      ImageView = itemView.findViewById(R.id.stripThumb)
        private val selectedBorder: View      = itemView.findViewById(R.id.stripSelectedBorder)
        init {
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onMediaClick(pos)
            }
        }
        @Suppress("DEPRECATION")
        fun bind(item: MediaItems, isCurrentlyViewing: Boolean) {
            selectedBorder.visibility = if (isCurrentlyViewing)   View.VISIBLE else View.GONE

            Glide.with(itemView.context)
                .load(item.uri)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .dontAnimate()
                .thumbnail(0.1f)
                .centerCrop()
                .optionalFitCenter()
                .useAnimationPool(true)
                .override(120, 120)
                .encodeQuality(50)
                .into(thumbnail)
        }
    }
}
