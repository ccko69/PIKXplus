package com.ccko.pikxplus.ux.photos

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ccko.pikxplus.MainActivity
import com.ccko.pikxplus.R
import com.ccko.pikxplus.ux.photos.PhotosAdpt
import com.ccko.pikxplus.shared.data.MediaItems
import com.ccko.pikxplus.ux.photos.PhotosVM
import com.ccko.pikxplus.shared.SharedVM
import com.ccko.pikxplus.shared.utils.FloatWin
import com.ccko.pikxplus.viewers.img.ImgFrg
import com.ccko.pikxplus.viewers.img.ImgVM
import com.ccko.pikxplus.shared.data.MSRepo
import com.ccko.pikxplus.ux.search.SearchFrg
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.appcompat.widget.Toolbar
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import android.view.animation.AccelerateInterpolator
import android.content.Context
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.widget.ListAdapter
import kotlinx.coroutines.sync.Semaphore
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import me.zhanghai.android.fastscroll.DefaultAnimationHelper
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat

/**
 * PhotosFrg — Displays grid/list of media from the selected album.
 *
 * Key behaviours:
 *   - Always opens at the top of the list (position 0) on first load.
 *   - Tracks ImgFrg's current swipe position in real time via SharedVM
 *     and highlights that item in the grid — without scrolling.
 *   - Scrolls to the last-viewed item only when RETURNING from ImgFrg
 *     (i.e. when ImgFrg sends its fragment result on close).
 *
 * NOTE: ImgFrg should call sharedViewModel.setCurrentViewingIndex(currentIndex)
 *       every time the user swipes to a new item, and clearCurrentViewingIndex()
 *       in its onDestroyView. 
 */
class PhotosFrg : Fragment() {
    companion object {
        private const val TAG = "PhotosFragment"
        private const val PREF_LAST_ALBUM_ID  = "last_album_id"
        private const val PREF_LAST_ALBUM_NAME = "last_album_name"
        private const val PREF_LAST_ALBUM_REL  = "last_album_relative_path"
        private const val PREF_KEY_SORT_MODE  = "photos_sort_mode"
        private const val PREF_KEY_VIEW_MODE  = "photos_grid_span"
    }
    // ===== VIEWS =====
    private lateinit var recyclerView:    RecyclerView
    // ===== VIEW MODELS =====
    private lateinit var photosViewModel: PhotosVM
    private lateinit var sharedViewModel: SharedVM
    // ===== ADAPTER =====
    private lateinit var adapter:         PhotosAdpt
    // ===== HEADER =====
    private lateinit var toolbar:           Toolbar
    private lateinit var collapsingToolbar: CollapsingToolbarLayout
    private lateinit var headerImage:       ImageView
    private lateinit var sortButton:        ImageButton
    private lateinit var viewModeButton:    ImageButton
    private lateinit var btnScrollTop:      ImageButton
    // ===== STATE =====
    private var albumId:    String? = null
    private var albumName:  String? = null
    private var folderName: String? = null
    
    private var floatingWindow: FloatWin? = null
    private val nextBatch = 45
    private var currentSpanCount = 6
    private var isGridView = true
    
    // private val webpCachePrefKey = "webpanimcache" // base key
    private val detectionInProgress = mutableSetOf<String>()
    private val webpCache by lazy { requireContext().getSharedPreferences("webp_cache", 0) }
    private val msRepo by lazy { MSRepo(requireContext()) }
    // private val webpSemaphore = java.util.concurrent.Semaphore(1) // allow 3 concurrent detections
    private val webpSemaphore = kotlinx.coroutines.sync.Semaphore(3)
    private var hasScrolledToTopOnce = false

    // ===== LIFECYCLE =====
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
       // (activity as? AppCompatActivity)?.supportActionBar?.hide()
        arguments?.let {
            albumId    = it.getString("album_id")
            albumName  = it.getString("album_name")
            folderName = it.getString("folder_name")
        }
        // Restore grid span from prefs early (before RecyclerView is created)
        restoreGridSpan()
        // Restore album if no args were passed
        if (albumId.isNullOrEmpty() && albumName.isNullOrEmpty()) {
            restoreLastAlbum()
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.frg_photos, container, false)
        recyclerView      = view.findViewById(R.id.recyclerViewPhotos)
        toolbar           = view.findViewById(R.id.toolbar)
        collapsingToolbar = view.findViewById(R.id.collapsingToolbar)
        collapsingToolbar.title = albumName ?: "Photos"  // Set initial title
        return view
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerImage     = view.findViewById(R.id.headerImage)
        sortButton      = view.findViewById(R.id.sortButton)
        viewModeButton  = view.findViewById(R.id.viewModeButton)
        btnScrollTop    = view.findViewById(R.id.btnScrollTop)
        photosViewModel = ViewModelProvider(requireActivity())[PhotosVM::class.java]
        sharedViewModel = ViewModelProvider(requireActivity())[SharedVM::class.java]
        setupRecyclerView()
        setupAdapter()
        setupFastScroller()
        setupScrollTopButton()
        observeViewModel()
        observeSharedViewModel()
        restoreSortMode()
        // Restore sort mode AFTER ViewModel is initialized

        val hasAlbum = !albumId.isNullOrEmpty() || !folderName.isNullOrEmpty()
        if (hasAlbum) {
            photosViewModel.setAlbumData(albumId, albumName, folderName)
        } else {
            Toast.makeText(context, "No album selected", Toast.LENGTH_SHORT).show()
        }
        sortButton.setOnClickListener     { showSortPopup(sortButton) }
        viewModeButton.setOnClickListener { showViewModePopup(viewModeButton) }
    }
    override fun onResume() {
        super.onResume()
    }
    override fun onPause() {
        super.onPause()
        floatingWindow?.dismiss()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        floatingWindow = null
    }
    // ===== SETUP =====
    private fun setupRecyclerView() {
        val layoutManager = GridLayoutManager(context, currentSpanCount)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                // All items now span 1 (no header)
                return 1
            }
        }
        recyclerView.layoutManager = layoutManager
        recyclerView.setHasFixedSize(true)
        // Infinite scroll remains the same—itemCount is now pure media items
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as? GridLayoutManager ?: return
                if (lm.findLastVisibleItemPosition() >= lm.itemCount - nextBatch) {
                    photosViewModel.loadMore()
                }
                updateScrollTopVisibility(rv)
            }
        })
        // Scroll to top on first load
        recyclerView.scrollToPosition(0)
    }
    private fun setupAdapter() {
        adapter = PhotosAdpt(
            onMediaClick = { position -> openImgFrg(position) },
            detectWebP   = { item -> detectWebPForItem(item) }
            // detectWebP = { item, pos -> detectWebPForItem(item, pos) } // ❌ error on the "pos" in detectWebPForItem(item, pos).
        )
        recyclerView.adapter = adapter
    }
    /** Attaches the draggable fast-scroll thumb (me.zhanghai.android.fastscroll). */
    private fun setupFastScroller() {
        val density = resources.displayMetrics.density
        
        val trackDrawable = GradientDrawable().apply {
            setColor(Color.TRANSPARENT) // Track — transparent
            setSize(0, 1)               // 0 width = invisible; height just needs to be >= 0
        }
        val animHelper = object : DefaultAnimationHelper(recyclerView) {
            override fun isScrollbarAutoHideEnabled() = true
            override fun getScrollbarAutoHideDelayMillis() = 5000  // Auto-hide delay - ms
        }
        
        // Thumb — two options, pick one:
        // Option A: load from a PNG drawable (BitmapDrawable always has positive intrinsic size)
        // val thumbDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.seekbar_thumb)!!
    
        // Option B: programmatic shape (width/height controlled by setSize in pixels)
        val thumbDrawable = GradientDrawable().apply {
            ContextCompat.getDrawable(requireContext(), R.drawable.seekbar_thumb)!!
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 4f * density
            
            setColor(Color.argb(160, 255, 255, 255))   // adjust alpha (0–255) for transparency
            setSize((20 * density).toInt(), (44 * density).toInt())  // 5dp wide, 44dp tall
        }
        
        FastScrollerBuilder(recyclerView)
          .useDefaultStyle()
          .setPadding(0,0,8,0)
          .setTrackDrawable(trackDrawable)
          .setThumbDrawable(thumbDrawable)
          // .setAnimationHelper(animHelper)
          .build()
    }
    /** Wires the scroll-to-top button's click behavior. Visibility is handled in updateScrollTopVisibility(). */
    private fun setupScrollTopButton() {
        btnScrollTop.setOnClickListener {
            val lm = recyclerView.layoutManager as? GridLayoutManager ?: return@setOnClickListener
            val firstVisible = lm.findFirstVisibleItemPosition()
            if (firstVisible > 40) {
                // Long lists: jump close instantly, then smooth-scroll the last bit.
                // Avoids a slow item-by-item animation across hundreds of items.
                recyclerView.scrollToPosition(40)
                recyclerView.post { recyclerView.smoothScrollToPosition(0) }
            } else {
                recyclerView.smoothScrollToPosition(0)
            }
        }
    }
    /** Shows the button once scrolled past roughly one screen; hides near the top. */
    private fun updateScrollTopVisibility(rv: RecyclerView) {
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
    private fun initHeader(name: String?, mediaList: List<MediaItems>) {
        collapsingToolbar.title = name ?: "Album"
        
        if (mediaList.isNotEmpty()) {
            val firstItem = mediaList.first()
            Glide.with(headerImage.getContext())
            .load(firstItem.uri)
            .frame(200)
            .centerCrop()
            .optionalFitCenter()
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
    // ===== OBSERVERS =====
    private fun observeViewModel() {
        // Observe media list
        lifecycleScope.launch {
            photosViewModel.mediaList.collectLatest { mediaList ->
                val headerName = photosViewModel.getCurrentAlbumName() ?: albumName
                // Defer adapter updates to avoid IllegalStateException during layout/scroll
                if (!recyclerView.isComputingLayout) {
                    adapter.submitListAdpt(mediaList)
                    batchResolveCachedWebPs(mediaList)
                    initHeader(headerName, mediaList)
                } else {
                    recyclerView.post {
                        adapter.submitListAdpt(mediaList)
                        batchResolveCachedWebPs(mediaList)
                        initHeader(headerName, mediaList)
                    }
                }
                if (!hasScrolledToTopOnce && mediaList.isNotEmpty()) {
                    recyclerView.scrollToPosition(0)
                    hasScrolledToTopOnce = true
                }
                // if (mediaList.isNotEmpty() && adapter.itemCount == mediaList.size) {recyclerView.scrollToPosition(0)}
            }
        }
        lifecycleScope.launch {
            photosViewModel.isLoading.collectLatest { isLoading ->
                // Optional: Show/hide progress bar, e.g., via a ProgressBar in XML
                // recyclerView.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
            }
        }
    }
    private fun batchResolveCachedWebPs(list: List<MediaItems>) {
        val webpItems = list.filter { it.mimeType == "image/webp" && !it.isAnimated() }
        if (webpItems.isEmpty()) return
        lifecycleScope.launch(Dispatchers.IO) {
            val animatedIds = webpItems
                .filter { item ->
                    val key = "${item.id}:${item.dateModified}"
                    webpCache.contains(key) && webpCache.getBoolean(key, false)
                }
                .map { it.id }
                .toSet()
            if (animatedIds.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    photosViewModel.markItemsAnimated(animatedIds)
                }
            }
        }
    }
    private fun observeSharedViewModel() {
        // Filter from SearchFrg
        lifecycleScope.launch {
            sharedViewModel.filter.collectLatest { filter ->
                filter?.let {
                    photosViewModel.setFilter(it)
                    showFilterSnackbar(it)
                } ?: run {
                    photosViewModel.setFilter(null)
                }
            }
        }
        // Deletion notification from ImgFrg
        lifecycleScope.launch {
            sharedViewModel.deletedPosition.collectLatest { position ->
                position?.let {
                    sharedViewModel.clearDeletedPosition()
                    photosViewModel.refresh()
                }
            }
        }
        // Track ImgFrg's current swipe index in real time.
        // Highlights the corresponding thumbnail in the grid — without scrolling.
        // ImgFrg must call sharedViewModel.setCurrentViewingIndex(index) on each swipe.
        lifecycleScope.launch {
            sharedViewModel.currentViewingIndex.collectLatest { index ->
                adapter.setCurrentViewingIndex(index)
            }
        }
    }
    // ===== DATA =====
    /** Called externally (e.g. from MainActivity) to reload. */
    fun loadAlbumPhotos() {
        hasScrolledToTopOnce = false
        sharedViewModel.clearDeletedPosition()
        photosViewModel.refresh()
    }
    /** Called from AlbumsFrg when a new album is selected. */
    fun setAlbumData(albumId: String?, albumName: String?, folderName: String?) {
        this.albumId   = albumId
        this.albumName = albumName
        this.folderName = folderName
        hasScrolledToTopOnce = false
        photosViewModel.setAlbumData(albumId, albumName, folderName)
    }
    // ===== VIEWER =====
    private fun openImgFrg(position: Int) {
        val mediaList = photosViewModel.getCurrentMediaList()
        if (mediaList.isEmpty() || position !in mediaList.indices) return
    
        // Store the full list in the activity‑scoped ImgVM so ImgFrg can pick it up
        val imgVM = ViewModelProvider(requireActivity())[ImgVM::class.java]
        imgVM.setMediaList(mediaList, position)
    
        val imgFrg = ImgFrg()
        imgFrg.arguments = Bundle().apply {
            putInt("current_index", position)
        }
        (activity as? MainActivity)?.openViewer(imgFrg)
    }
    // ===== SORT POPUP =====
    private fun showSortPopup(anchor: View) {
        val ctx = context ?: return
        floatingWindow = FloatWin(ctx, anchor)
        val widthPx = (240 * resources.displayMetrics.density).toInt()

        floatingWindow?.show(
            R.layout.float_photos_so,
            widthPx,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            0,
            20,
            Gravity.START,
            { content ->
                setupSortRowLogic(content, R.id.btnSo_name, R.id.arrow_name,
                    PhotosVM.SortMode.NAME_ASC,  PhotosVM.SortMode.NAME_DESC)
                setupSortRowLogic(content, R.id.btnSo_date, R.id.arrow_date,
                    PhotosVM.SortMode.DATE_DESC, PhotosVM.SortMode.DATE_ASC)
                setupSortRowLogic(content, R.id.btnSo_size, R.id.arrow_size,
                    PhotosVM.SortMode.SIZE_ASC,  PhotosVM.SortMode.SIZE_DESC)
            },
            false,
            focusable = false,
            FloatWin.AnimationConfig( 
                duration = 600,
                interpolator = AccelerateInterpolator(),
                type = FloatWin.AnimationType.SLIDE_DOWN
            )
        )
    }
    private fun updateAllArrows(content: View) {
        val currentMode = photosViewModel.getCurrentSortMode()
    
        val arrows = listOf(
            Pair(R.id.arrow_name, currentMode == PhotosVM.SortMode.NAME_ASC || currentMode == PhotosVM.SortMode.NAME_DESC),
            Pair(R.id.arrow_date, currentMode == PhotosVM.SortMode.DATE_ASC || currentMode == PhotosVM.SortMode.DATE_DESC),
            Pair(R.id.arrow_size, currentMode == PhotosVM.SortMode.SIZE_ASC || currentMode == PhotosVM.SortMode.SIZE_DESC)
        )
    
        arrows.forEach { (id, isVisible) ->
            val arrow = content.findViewById<ImageView>(id)
            arrow.alpha = if (isVisible) 1.0f else 0f
        }
    }
    private fun setupSortRowLogic(
        content:  View,
        rowId:    Int,
        arrowId:  Int,
        ascMode:  PhotosVM.SortMode,
        descMode: PhotosVM.SortMode
    ) {
        val row   = content.findViewById<View>(rowId)
        val arrow = content.findViewById<ImageView>(arrowId)
        val currentMode = photosViewModel.getCurrentSortMode()
        val isCurrent   = currentMode == ascMode || currentMode == descMode

        arrow.rotation = if (currentMode == ascMode) 0f else 180f
        arrow.alpha    = if (isCurrent) 1.0f else 0f

        row.setOnClickListener {
            val newMode = if (photosViewModel.getCurrentSortMode() == ascMode) descMode else ascMode
            photosViewModel.setSortMode(newMode)
            saveSortMode(newMode)
            hasScrolledToTopOnce = false
            // Refresh popup UI in-place
            floatingWindow?.updateContent { newContent ->
                setupSortRowLogic(newContent, rowId, arrowId, ascMode, descMode)
                updateAllArrows(newContent)
            }
        }
    }
    // ===== VIEW MODE POPUP =====
    private fun showViewModePopup(anchor: View) {
        val ctx = context ?: return
        floatingWindow = FloatWin(ctx, anchor)
        val widthPx = (240 * resources.displayMetrics.density).toInt()

        floatingWindow?.show(
            R.layout.float_photos_vm,
            widthPx,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            0,
            20,
            Gravity.END,
            { content ->
                val countTxt = content.findViewById<TextView>(R.id.txt_grid_count)
                countTxt.text = currentSpanCount.toString()

                content.findViewById<View>(R.id.btn_grid_plus).setOnClickListener {
                    if (currentSpanCount < 6) {
                        currentSpanCount++
                        updateGridSpan(currentSpanCount)
                        countTxt.text = currentSpanCount.toString()
                        saveGridSpan()
                    }
                }
                content.findViewById<View>(R.id.btn_grid_minus).setOnClickListener {
                    if (currentSpanCount > 2) {
                        currentSpanCount--
                        updateGridSpan(currentSpanCount)
                        countTxt.text = currentSpanCount.toString()
                        saveGridSpan()
                    }
                }
                content.findViewById<View>(R.id.btn_view_list).setOnClickListener {
                    isGridView = false
                    currentSpanCount = 1
                    updateGridSpan(1)
                    floatingWindow?.dismiss()
                    saveGridSpan()
                }
            },
            false,
            focusable = false,
            FloatWin.AnimationConfig( 
                duration = 600,
                interpolator = AccelerateInterpolator(),
                type = FloatWin.AnimationType.SLIDE_DOWN
            )
        )
    }
    // ===== GRID/LIST =====
    private fun updateGridSpan(span: Int) {
        isGridView = span > 1
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return

        lm.spanCount = span
        lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (position == 0) 1 else 1
            }
        }
        adapter.updateSpanCount(span, span > 1)
    }
    // ===== SNACKBAR =====
    private fun showFilterSnackbar(filter: SearchFrg.Filter?) {
        if (filter == null || filter.type == SearchFrg.Filter.Type.ALL) return
        val label = when (filter.type) {
            SearchFrg.Filter.Type.PHOTOS   -> "Photos"
            SearchFrg.Filter.Type.VIDEOS   -> "Videos"
            SearchFrg.Filter.Type.ANIMATED -> "GIFs"
            SearchFrg.Filter.Type.ALL      -> "All"
        }
        Snackbar.make(recyclerView, "Showing: $label", Snackbar.LENGTH_INDEFINITE)
            .setAction("Clear") {
                sharedViewModel.setFilter(null)
                photosViewModel.setFilter(null)
            }
            .setActionTextColor(android.graphics.Color.parseColor("#FFB00020"))
            .show()
    }
    // ===== PREFERENCES =====
    private fun detectWebPForItem(item: MediaItems) {
        if (item.mimeType != "image/webp") return
        if (item.isAnimated()) return
    
        val cacheKey = "${item.id}:${item.dateModified}"
        
        if (webpCache.contains(cacheKey)) {
            if (webpCache.getBoolean(cacheKey, false)) {
                photosViewModel.markItemAnimated(item.id, item.dateModified)
            }
            return // ← exits for static WebP too
        }
        // if (webpCache.getBoolean(cacheKey, false)) { // If cached animated, update VM and return
            // photosViewModel.markItemAnimated(item.id, item.dateModified)
            // return
        // }
        
        synchronized(detectionInProgress) { // Avoid duplicate in-flight detection
            if (detectionInProgress.contains(cacheKey)) return
            detectionInProgress.add(cacheKey)
        }
        // Launch detection tied to view lifecycle
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                webpSemaphore.acquire()
                // withContext(Dispatchers.IO) { // limit concurrency to avoid I/O spikes
                    // webpSemaphore.acquire()
                // }
                val type = withContext(Dispatchers.IO) {
                    try {
                        msRepo.detectAnimationType(item.uri, item.mimeType)
                    } catch (e: Exception) {
                        MediaItems.MediaType.IMAGE
                    }
                }
                val isAnimated = type == MediaItems.MediaType.ANIMATED
                webpCache.edit().putBoolean(cacheKey, isAnimated).apply()
                if (isAnimated) {
                    photosViewModel.markItemAnimated(item.id, item.dateModified)
                }
               /* if (type == MediaItems.MediaType.ANIMATED) {
                    photosViewModel.markItemAnimated(item.id, item.dateModified)
                    try {
                        webpCache.edit().putBoolean(cacheKey, true).apply()
                    } catch (_: Exception) { /* ignore */ }
                }*/
            } finally {
                synchronized(detectionInProgress) { detectionInProgress.remove(cacheKey) }
                // withContext(Dispatchers.IO) { webpSemaphore.release() } // release semaphore on IO thread
                webpSemaphore.release()
            }
        }
    }
    // the parsed SortMode passed directly to the ViewModel after it's initialized.
    private fun restoreSortMode() {
        try {
            MainActivity.prefs.getString(PREF_KEY_SORT_MODE, null)?.let { saved ->
                val mode = PhotosVM.SortMode.valueOf(saved)
                photosViewModel.setSortMode(mode)
            }
        } catch (e: Exception) { /* Ignore invalid/missing pref — default sort stays */ }
    }
    private fun saveSortMode(mode: PhotosVM.SortMode) {
        try {
            MainActivity.prefs.edit()?.putString(PREF_KEY_SORT_MODE, mode.name)?.apply()
        } catch (e: Exception) { /* ignore */ }
    }
    private fun restoreGridSpan() {
        try {
            val saved = MainActivity.prefs.getInt(PREF_KEY_VIEW_MODE, 0) ?: 0
            if (saved > 0) currentSpanCount = saved
            isGridView = currentSpanCount > 1
        } catch (e: Exception) { /* ignore */ }
    }
    private fun saveGridSpan() {
        try {
            MainActivity.prefs.edit()?.putInt(PREF_KEY_VIEW_MODE, currentSpanCount)?.apply()
        } catch (e: Exception) { /* ignore */ }
    }
    private fun restoreLastAlbum() {
        try {
            MainActivity.prefs.let { prefs ->
                val savedId   = prefs.getString(PREF_LAST_ALBUM_ID, null)
                val savedName = prefs.getString(PREF_LAST_ALBUM_NAME, null)
                val savedRel  = prefs.getString(PREF_LAST_ALBUM_REL, null)
                if (!savedId.isNullOrEmpty())   albumId    = savedId
                if (!savedName.isNullOrEmpty())  albumName  = savedName
                if (!savedRel.isNullOrEmpty())   folderName = savedRel
            }
        } catch (e: Exception) { /* ignore */ }
    }
}
