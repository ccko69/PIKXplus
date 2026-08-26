package com.ccko.pikxplus
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedCallback
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.ccko.pikxplus.shared.MainFrgAdpt
import com.ccko.pikxplus.shared.SharedVM
import com.ccko.pikxplus.ux.albums.AlbumsFrg
import com.ccko.pikxplus.viewers.img.ImgFrg
import com.ccko.pikxplus.viewers.vid.VidFrg
import com.ccko.pikxplus.ux.photos.PhotosFrg
import com.ccko.pikxplus.ux.settings.SetRepo
import com.ccko.pikxplus.shared.data.MediaItems
import com.ccko.pikxplus.shared.utils.Constants
import com.ccko.pikxplus.shared.data.MSRepo
import com.ccko.pikxplus.shared.utils.PermissionHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity :
AppCompatActivity(),
AlbumsFrg.OnAlbumSelectedListener,
ImgFrg.OnImageDeletedListener {
  private lateinit var viewPager: ViewPager2
  private lateinit var bottomNavigation: BottomNavigationView
  private lateinit var fragmentContainer: View
  private lateinit var uiOverlay: ViewGroup
  private lateinit var sharedViewModel: SharedVM
  private val msRepo by lazy { MSRepo(applicationContext) }
  val settingsRepo by lazy { SetRepo(applicationContext) }
  private var imgFragment: ImgFrg? = null
  private var currentOverlayView: View? = null
  private var isViewerActive = false
  private var isPortrait = true
  private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
  private val permissionLauncher =
  registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    handlePermissionResult()
  }
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    initPrefs(this)
    setContentView(R.layout.activity_main)
    viewPager = findViewById(R.id.viewPager)
    bottomNavigation = findViewById(R.id.bottomNavigation)
    fragmentContainer = findViewById(R.id.fragmentContainer)
    uiOverlay = findViewById(R.id.uiOverlay)
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    sharedViewModel = ViewModelProvider(this)[SharedVM::class.java]
    WindowCompat.setDecorFitsSystemWindows(window, false)
    applyWindowInsets()
    initializeCoil()
    setupNavigation()
    checkStoragePermission()
    observeViewerState()
    loadAlbumsIfPresent()
    handleIntent(intent)
    initBackPress()
  }
  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    if (intent != null) {
      handleIntent(intent)
    }
  }
  private fun handleIntent(intent: Intent) {
    when (intent.action) {
      Intent.ACTION_VIEW -> {
        val data = intent.data ?: return
        val mimeType = contentResolver.getType(data) ?: ""
        val isVideo = mimeType.startsWith("video/")
        viewPager.setCurrentItem(MainFrgAdpt.POSITION_PHOTOS, false)
        lifecycleScope.launch {
          openExternalMedia(data, isVideo)
        }
      }
    }
  }
  private suspend fun openExternalMedia(uri: Uri, isVideo: Boolean) {
    val mediaItem = withContext(Dispatchers.IO) { MediaItems.fromUri(uri, contentResolver) }
    if (mediaItem != null) {
      openSingleItemViewer(mediaItem, isVideo)
    } else {
      Toast.makeText(this, "Cannot open file", Toast.LENGTH_SHORT).show()
    }
  }
  private fun openSingleItemViewer(item: MediaItems, isVideo: Boolean) {
    val list = listOf(item)
    if (isVideo) {
      openVideoPlayer(list, 0)
    } else {
      val imgFrg = ImgFrg()
      imgFrg.arguments = Bundle().apply {
        putParcelableArrayList("media_items", ArrayList(list))
        putInt("current_index", 0)
        putBoolean("user_picked", true)
      }
      openViewer(imgFrg)
    }
  }
  fun openVideoPlayer(mediaList: List<MediaItems>, videoIndex: Int) {
    val vidFrg = VidFrg()
    vidFrg.arguments = Bundle().apply {
      putParcelableArrayList("media_items", ArrayList(mediaList))
      putInt("current_index", videoIndex)
    }
    openViewer(vidFrg)
  }
  private fun applyWindowInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(fragmentContainer) { _, insets ->
      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      bottomNavigation.updatePadding(bottom = systemBars.bottom)
      viewPager.updatePadding(top = systemBars.top)
      WindowInsetsCompat.CONSUMED
    }
  }
  private fun setupNavigation() {
    val pagerAdapter = MainFrgAdpt(this)
    viewPager.apply {
      adapter = pagerAdapter
      isUserInputEnabled = true
      setCurrentItem(MainFrgAdpt.POSITION_ALBUMS, false)
    }
    viewPager.registerOnPageChangeCallback(
      object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
          super.onPageSelected(position)
          syncBottomNavWithPosition(position)
        }
      }
    )
    bottomNavigation.setOnItemSelectedListener { item ->
      val position = getPositionForNavItem(item.itemId)
      if (position != -1) {
        viewPager.setCurrentItem(position, true)
      }
      true
    }
  }
  fun navigateToTab(position: Int) {
    viewPager.setCurrentItem(position, true)
  }
  private fun syncBottomNavWithPosition(position: Int) {
    val navId = when (position) {
      MainFrgAdpt.POSITION_ALBUMS -> R.id.nav_albums
      MainFrgAdpt.POSITION_PHOTOS -> R.id.nav_photos
      MainFrgAdpt.POSITION_SEARCH -> R.id.nav_search
      MainFrgAdpt.POSITION_CAMERA -> R.id.nav_camera
      MainFrgAdpt.POSITION_SETTINGS -> R.id.nav_settings
      else -> R.id.nav_albums
    }
    bottomNavigation.selectedItemId = navId
  }
  private fun getPositionForNavItem(itemId: Int): Int = when (itemId) {
    R.id.nav_albums -> MainFrgAdpt.POSITION_ALBUMS
    R.id.nav_photos -> MainFrgAdpt.POSITION_PHOTOS
    R.id.nav_search -> MainFrgAdpt.POSITION_SEARCH
    R.id.nav_camera -> MainFrgAdpt.POSITION_CAMERA
    R.id.nav_settings -> MainFrgAdpt.POSITION_SETTINGS
    else -> -1
  }
  private fun checkStoragePermission() {
    if (PermissionHelper.hasStoragePermission(this)) {
      loadAlbumsIfPresent()
    } else {
      PermissionHelper.showPermissionDialog(
        context = this,
        onGrantClicked = { launchPermissionSettings() },
        onExitClicked = { finish() }
      )
    }
  }
  private fun launchPermissionSettings() {
    PermissionHelper.launchPermissionSettings(this, permissionLauncher)
  }
  private fun handlePermissionResult() {
    if (PermissionHelper.verifyPermissionGranted(this)) {
      recreate()
    } else {
      checkStoragePermission()
    }
  }
  private fun loadAlbumsIfPresent() {
    val tag = "f${MainFrgAdpt.POSITION_ALBUMS}"
    val fragment = supportFragmentManager.findFragmentByTag(tag)
    if (fragment is AlbumsFrg) {
      fragment.loadAlbums()
    }
  }
  private fun observeViewerState() {
    lifecycleScope.launch {
      sharedViewModel.isViewerActive.collectLatest { isActive ->
        isViewerActive = isActive
      }
    }
  }
  fun isViewerMode(): Boolean = isViewerActive
  fun openViewer(fragment: Fragment) {
    sharedViewModel.setViewerActive(true)
    viewPager.isUserInputEnabled = false
    fragmentContainer.visibility = View.VISIBLE
    viewPager.visibility = View.GONE
    supportFragmentManager
    .beginTransaction()
    .replace(R.id.fragmentContainer, fragment)
    .addToBackStack(null)
    .commit()
    setViewerMode(true)
  }
  fun closeViewer() {
    sharedViewModel.setViewerActive(false)
    viewPager.isUserInputEnabled = true
    fragmentContainer.visibility = View.GONE
    viewPager.visibility = View.VISIBLE
    hideUiOverlay()
    supportFragmentManager.popBackStack()
    setViewerMode(false)
  }
  @Suppress("DEPRECATION")
  internal fun setViewerMode(enabled: Boolean) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (enabled) {
      WindowCompat.setDecorFitsSystemWindows(window, false)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val lp = WindowManager.LayoutParams().apply {
          copyFrom(window.attributes)
          layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.attributes = lp
      }
      window.statusBarColor = Color.TRANSPARENT
      window.navigationBarColor = Color.TRANSPARENT
      controller.hide(
        WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
      )
      controller.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      animateBottomNav(false)
      animateViewerOverlay(true)
    } else {
      WindowCompat.setDecorFitsSystemWindows(window, true)
      val primary = Color.parseColor("#FF000000")
      window.statusBarColor = primary
      window.navigationBarColor = primary
      controller.show(
        WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
      )
      animateBottomNav(true)
      animateViewerOverlay(false)
    }
  }
  private fun animateViewerOverlay(show: Boolean) {
    val overlay = fragmentContainer
    if (show) {
      overlay.alpha = 0f
      overlay.visibility = View.VISIBLE
      overlay.animate()
      .alpha(1f)
      .setDuration(0)
      .setInterpolator(DecelerateInterpolator())
      .start()
    } else {
      overlay.animate()
      .alpha(0f)
      .setDuration(500)
      .setInterpolator(AccelerateInterpolator())
      .withEndAction { overlay.visibility = View.GONE }
      .start()
    }
  }
  private fun animateBottomNav(show: Boolean) {
    bottomNavigation.animate().cancel()
    bottomNavigation.translationY = 0f
    if (show) {
      bottomNavigation.alpha = 0f
      bottomNavigation.visibility = View.VISIBLE
      bottomNavigation.isClickable = false
      bottomNavigation.animate()
      .alpha(1f)
      .setDuration(500)
      .setInterpolator(DecelerateInterpolator())
      .withEndAction { bottomNavigation.isClickable = true }
      .start()
    } else {
      bottomNavigation.isClickable = false
      bottomNavigation.animate()
      .alpha(0f)
      .setDuration(500)
      .setInterpolator(AccelerateInterpolator())
      .withEndAction {
        bottomNavigation.visibility = View.GONE
        bottomNavigation.isClickable = true
      }
      .start()
    }
  }
  fun showUiOverlay(overlayView: View) {
    hideUiOverlay()
    currentOverlayView = overlayView
    uiOverlay.addView(overlayView)
    uiOverlay.visibility = View.VISIBLE
  }
  fun hideUiOverlay() {
    uiOverlay.removeAllViews()
    uiOverlay.visibility = View.GONE
    currentOverlayView = null
  }
  fun clearUiOverlay() = hideUiOverlay()
  override fun onAlbumSelected(album: AlbumsFrg.Album) {
    try {
      prefs.edit()
      .putString(Constants.PREF_LAST_ALBUM_ID, album.id)
      .putString(Constants.PREF_LAST_ALBUM_NAME, album.name)
      .putString(Constants.PREF_LAST_ALBUM_RELATIVE_PATH, album.relativePath ?: "")
      .apply()
    } catch (e: Exception) { /* ignore */ }
    val tag = "f${MainFrgAdpt.POSITION_PHOTOS}"
    val fragment = supportFragmentManager.findFragmentByTag(tag)
    if (fragment is PhotosFrg) {
      fragment.setAlbumData(album.id, album.name, album.relativePath)
    }
    viewPager.setCurrentItem(MainFrgAdpt.POSITION_PHOTOS, true)
  }
  override fun onImageDeleted(deletedIndex: Int) {
    val tag = "f${MainFrgAdpt.POSITION_PHOTOS}"
    val fragment = supportFragmentManager.findFragmentByTag(tag)
    if (fragment is PhotosFrg) {
      fragment.loadAlbumPhotos()
    }
  }
  /** Called by AlbumsFrg when SwipeRefreshLayout is triggered. */
  fun refreshPhotosFragment() {
    val tag = "f${MainFrgAdpt.POSITION_PHOTOS}"
    val fragment = supportFragmentManager.findFragmentByTag(tag)
    if (fragment is PhotosFrg) {
      fragment.loadAlbumPhotos()
    }
  }
  fun toggleOrientation() {
    isPortrait = !isPortrait
    requestedOrientation = if (isPortrait) {
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    } else {
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
  }
  fun toggleOrientationReset() {
    isPortrait = true
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
  }
  fun initBackPress() {
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          when {
            isViewerActive -> {
              closeViewer()
            }
            viewPager.currentItem == MainFrgAdpt.POSITION_PHOTOS -> {
              viewPager.setCurrentItem(MainFrgAdpt.POSITION_ALBUMS, true)
              hideUiOverlay()
            }
            viewPager.currentItem == MainFrgAdpt.POSITION_SETTINGS -> {
              viewPager.setCurrentItem(MainFrgAdpt.POSITION_ALBUMS, true)
              hideUiOverlay()
            }
            viewPager.currentItem == MainFrgAdpt.POSITION_ALBUMS ||
            viewPager.currentItem == MainFrgAdpt.POSITION_CAMERA -> {
              hideUiOverlay()
              finish()
            }
            else -> {
              viewPager.setCurrentItem(MainFrgAdpt.POSITION_ALBUMS, true)
              hideUiOverlay()
            }
          }
        }
    })
  }
  private fun initializeCoil() {
    val coilImageLoader = ImageLoader.Builder(this)
    .memoryCache {
      MemoryCache.Builder(this)
      .maxSizePercent(0.50)
      .build()
    }
    .diskCache {
      DiskCache.Builder()
      .directory(cacheDir.resolve("coil_cache"))
      .maxSizeBytes(200L * 1024 * 1024)
      .build()
    }
    .components {
      add(GifDecoder.Factory())
      if (Build.VERSION.SDK_INT >= 28) {
        add(ImageDecoderDecoder.Factory())
      }
      add(VideoFrameDecoder.Factory())
    }
    .respectCacheHeaders(false)
    .build()
    Coil.setImageLoader(coilImageLoader)
  }
  companion object {
    lateinit var prefs: android.content.SharedPreferences
    private set
    fun initPrefs(context: android.content.Context) {
      prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
    }
  }
}
