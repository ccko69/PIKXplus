package com.ccko.pikxplus.ux.albums
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ccko.pikxplus.R
import com.ccko.pikxplus.MainActivity
import com.ccko.pikxplus.ux.photos.PhotosFrg
import com.ccko.pikxplus.shared.data.AlbumInfo
import kotlinx.coroutines.launch
class AlbumsFrg : Fragment() {
  companion object {
    private const val TAG = "AlbumsFrg"
  }
  // private var _binding: FrgAlbumsBinding? = null
  //  private val binding get() = _binding!!
  private var viewModel: AlbumsVM? = null
  private var adapter: AlbumsAdpt? = null
  private var listener: OnAlbumSelectedListener? = null
  private var photosFrg: PhotosFrg? = null
  // Toolbar menu state
  private var selectionMode: AlbumsAdpt.SelectionMode = AlbumsAdpt.SelectionMode.NONE
  private var isGridMode = false
  // Compatibility data class – used to bridge with MainActivity's listener.
  data class Album(
    val id: String,
    val name: String,
    val count: Int,
    val thumbnailUri: Uri?,
    val relativePath: String?
  )
  interface OnAlbumSelectedListener {
    fun onAlbumSelected(album: Album)
  }
  override fun onAttach(context: Context) {
    super.onAttach(context)
    listener = try {
      context as OnAlbumSelectedListener
    } catch (e: ClassCastException) {
      throw ClassCastException("${context.javaClass.name} must implement OnAlbumSelectedListener")
    }
  }
  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    // _binding = FrgAlbumsBinding.inflate(inflater, container, false)
    setHasOptionsMenu(true)
    return inflater.inflate(R.layout.frg_albums, container, false)
  }
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    // (activity as? AppCompatActivity)?.supportActionBar?.hide()
    setupToolbar()
    initializeViewModel()
    setupRecyclerView()
    setupSwipeRefresh()
    observeViewModel()
    loadAlbums()
  }
  private fun setupToolbar() {
    val activity = activity as? AppCompatActivity ?: return
    val toolbar = requireView().findViewById<Toolbar>(R.id.toolbarAlbums)
    activity.setSupportActionBar(toolbar)
    activity.supportActionBar?.setDisplayShowTitleEnabled(true)
    toolbar.setOnMenuItemClickListener { item ->
      when (item.itemId) {
        R.id.action_bookmark -> {
          if (selectionMode == AlbumsAdpt.SelectionMode.SELECT) {
            // In SELECT mode this slot means "Select All" — bulk-bookmark every visible album.
            selectAllAlbums()
          } else {
            toggleBookmarkSelectionMode()
          }
          true
        }
        R.id.action_manage -> {
          if (selectionMode == AlbumsAdpt.SelectionMode.SELECT) {
            // In SELECT mode this slot means "Done" — exit selection mode.
            exitSelectionMode()
          } else {
            toggleManageSelectionMode()
          }
          true
        }
        R.id.action_grid_list -> {
          toggleGridListMode()
          true
        }
        else -> false
      }
    }
  }
  /** In SELECT mode, bulk-bookmark every currently-visible album in one prefs write. */
  private fun selectAllAlbums() {
    val ids = adapter?.currentItems()
    ?.filterIsInstance<AlbumsAdpt.Item.Album>()
    ?.map { it.albumInfo.id }
    ?.toSet()
    .orEmpty()
    viewModel?.setBookmarked(ids, true)
  }
  /** Exposed to the adapter for fade animations when a per-item toggle changes state. */
  private fun fadeIndicator(view: View) {
    view.animate().cancel()
    view.alpha = 0.35f
    view.animate()
    .alpha(1f)
    .setDuration(120L)
    .start()
  }
  private fun toggleBookmarkSelectionMode() {
    // Both toolbar icons now enter/exit the unified SELECT mode.
    toggleSelectMode()
  }
  private fun toggleManageSelectionMode() {
    // Both toolbar icons now enter/exit the unified SELECT mode.
    toggleSelectMode()
  }
  private fun toggleSelectMode() {
    if (selectionMode == AlbumsAdpt.SelectionMode.SELECT) {
      exitSelectionMode()
    } else {
      enterSelectionMode()
    }
  }
  private fun enterSelectionMode() {
    selectionMode = AlbumsAdpt.SelectionMode.SELECT
    adapter?.setSelectionMode(selectionMode)
    viewModel?.setIncludeHidden(true)
    val toolbar = requireView().findViewById<Toolbar>(R.id.toolbarAlbums)
    // Repurpose the toolbar: primary slot = "Select All", secondary = "Done".
    toolbar.menu?.findItem(R.id.action_bookmark)
    ?.setIcon(R.drawable.ic_check_all)
    ?.setTitle(R.string.select_all)
    toolbar.menu?.findItem(R.id.action_manage)
    ?.setIcon(R.drawable.ic_check)
    ?.setTitle(R.string.done)
  }
  private fun exitSelectionMode() {
    selectionMode = AlbumsAdpt.SelectionMode.NONE
    adapter?.setSelectionMode(selectionMode)
    viewModel?.setIncludeHidden(false)
    // Restore default toolbar icons + titles.
    val toolbar = requireView().findViewById<Toolbar>(R.id.toolbarAlbums)
    toolbar.menu?.findItem(R.id.action_bookmark)
    ?.setIcon(R.drawable.ic_bookmark)
    ?.setTitle(R.string.bookmark)
    toolbar.menu?.findItem(R.id.action_manage)
    ?.setIcon(R.drawable.ic_visibility)
    ?.setTitle(R.string.manage_albums)
  }
  private fun toggleGridListMode() {
    isGridMode = !isGridMode
    val toolbar = requireView().findViewById<Toolbar>(R.id.toolbarAlbums)
    val ryclAlbums = requireView().findViewById<RecyclerView>(R.id.ryclAlbums)
    toolbar.menu?.findItem(R.id.action_grid_list)?.setIcon(
      if (isGridMode) R.drawable.ic_grid_album else R.drawable.ic_list_album
    )
    if (isGridMode) {
      val glm = androidx.recyclerview.widget.GridLayoutManager(context, 4)
      adapter?.installSpanSizeLookup(glm)
      ryclAlbums.layoutManager = glm
    } else {
      ryclAlbums.layoutManager = LinearLayoutManager(context)
    }
    adapter?.notifyDataSetChanged()
  }
  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    inflater.inflate(R.menu.menu_albums, menu)
    super.onCreateOptionsMenu(menu, inflater)
  }
  override fun onDestroyView() {
    super.onDestroyView()
    // _binding = null
    adapter = null
  }
  private fun initializeViewModel() {
    val activity = activity ?: return
    viewModel = ViewModelProvider(activity)[AlbumsVM::class.java]
  }
  private fun setupRecyclerView() {
    val ryclAlbums = requireView().findViewById<RecyclerView>(R.id.ryclAlbums)
    ryclAlbums.layoutManager = LinearLayoutManager(context)
    val newAdapter = AlbumsAdpt(
      onAlbumClick = { albumInfo ->
        listener?.onAlbumSelected(
          Album(
            id = albumInfo.id,
            name = albumInfo.name,
            count = albumInfo.totalCount,
            thumbnailUri = albumInfo.thumbnailUri,
            relativePath = albumInfo.relativePath
          )
        )
      },
      onAlbumLongClick = { _ ->
        // Long press enters selection mode (per user request), it does NOT bookmark.
        enterSelectionMode()
      },
      isGridMode = { isGridMode }
    )
    // Per-item callbacks for the unified SELECT mode.
    newAdapter.onBookmarkToggle = { albumInfo ->
      viewModel?.toggleBookmark(albumInfo.id)
    }
    newAdapter.onHiddenToggle = { albumInfo ->
      viewModel?.toggleHidden(albumInfo.id)
    }
    newAdapter.onIndicatorToggled = { view -> fadeIndicator(view) }
    adapter = newAdapter
    ryclAlbums.adapter = adapter
  }
  private fun setupSwipeRefresh() {
    val swipeRefreshAlbums = requireView().findViewById<SwipeRefreshLayout>(R.id.swipeRefreshAlbums)
    swipeRefreshAlbums.setOnRefreshListener {
      refreshAllFragments()
    }
    // Optional: set color scheme to match app theme
    swipeRefreshAlbums.setColorSchemeResources(
      R.color.clr_Prim,
      R.color.clr_Prim
    )
    // Disable SwipeRefreshLayout when toolbar is expanded/collapsed
    swipeRefreshAlbums.setEnabled(false)
  }
  private fun observeViewModel() {
    val vm = viewModel ?: return
    val swipeRefreshAlbums = requireView().findViewById<SwipeRefreshLayout>(R.id.swipeRefreshAlbums)
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
          vm.sectionedAlbums.collect { items ->
            adapter?.submitList(items)
          }
        }
        launch {
          vm.error.collect { error ->
            error?.let {
              Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
          }
        }
        launch {
          vm.isLoading.collect { loading ->
            swipeRefreshAlbums.isRefreshing = loading
            // Show/hide horizontal progress bar under toolbar
            val progressBarAlbums = view?.findViewById<ProgressBar>(R.id.progressBarAlbums)
            progressBarAlbums?.visibility = if (loading) View.VISIBLE else View.GONE
          }
        }
      }
    }
  }
  /**
   * Refresh both Albums and Photos fragments (Task 4).
   * Also kicks MediaScanner on common media roots so files added externally
   * (File Manager, another gallery, etc.) become visible in MediaStore queries.
   */
  fun refreshAllFragments() {
    triggerMediaScanOnCommonRoots()
    viewModel?.refreshAll()
    (activity as? MainActivity)?.let { act ->
      act.refreshPhotosFragment()
    }
  }
  /**
   * Walk a small set of well-known media roots and ask MediaScanner to re-index
   * them. This is what makes the refresh pick up files added by other apps.
   * Safe to call repeatedly; failures are logged but never thrown.
   */
  private fun triggerMediaScanOnCommonRoots() {
    // val roots2 = Environment.getStorageDirectory()
    val roots = listOf(
      Environment.DIRECTORY_DCIM,
      Environment.DIRECTORY_PICTURES,
      Environment.DIRECTORY_MOVIES,
      Environment.DIRECTORY_DOWNLOADS
    )
    val paths = roots.mapNotNull { dir ->
      val f = Environment.getExternalStoragePublicDirectory(dir)
      if (f.exists()) f.absolutePath else null
    }
    if (paths.isEmpty()) return
    try {
      MediaScannerConnection.scanFile(
        context?.applicationContext,
        paths.toTypedArray(),
        // Mime hints MUST line up 1:1 with `paths` order:
        // DCIM -> image/*, Pictures -> image/*, Movies -> video/*, Downloads -> null.
        arrayOf("image/*", "image/*", "video/*", null),
        null
      )
    } catch (e: Exception) {
      // MediaScanner can occasionally fail on permission-less contexts; the
      // MediaStore query itself will still run, so this is non-fatal.
      android.util.Log.w("AlbumsFrg", "MediaScanner scan failed", e)
    }
  }
  fun loadAlbums() {
    viewModel?.loadAlbums()
  }
}
