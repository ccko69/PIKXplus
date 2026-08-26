package com.ccko.pikxplus.viewers.img
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.ccko.pikxplus.MainActivity
import com.ccko.pikxplus.R
import com.ccko.pikxplus.shared.SharedVM
import com.ccko.pikxplus.shared.utils.FloatMenu
import com.ccko.pikxplus.shared.utils.FloatWin
import com.ccko.pikxplus.shared.utils.MediaDeletionHelper
import com.ccko.pikxplus.shared.utils.MediaItemz
import com.ccko.pikxplus.ux.photos.PhotosAdpt
import com.ccko.pikxplus.ux.settings.ImgSetDlg
import com.ccko.pikxplus.ux.settings.PrefKeys
import com.ccko.pikxplus.ux.settings.SetRepo
import com.ccko.pikxplus.viewers.img.ImgGstHandler
import com.ccko.pikxplus.viewers.img.ImgVM
import com.ccko.pikxplus.viewers.img.SlideShowCtrl
import com.ccko.pikxplus.viewers.img.VpAdpt
import com.ccko.pikxplus.viewers.vid.VidFrg
import com.google.android.material.button.MaterialButton
import com.ccko.pikxplus.shared.utils.ImgClrAdjst
import com.helper.ImageMatrixController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
/**
 * Image Viewer Fragment (ImgFrg).
 * Full-screen pager for images and videos. Uses ViewPager2 + SubsamplingScaleImageView.
 *
 * FIX: Adapter was recreated from scratch inside the mediaList flow observer, which resets
 *      ViewPager2 to position 0 on every emission. Adapter is now created ONCE in
 *      loadMediaFromArguments(). The flow observer calls adapter.submitList() on deletion.
 */
class ImgFrg : Fragment() {
  companion object {
    private const val TAG = "ImgFrg"
    private const val PREF_KEY_SLIDESHOW_DELAY = "slideshow_delay"
    private const val PREF_KEY_AUTO_ENHANCE = "slideshow_enhance"
    private const val PREF_KEY_AUTO_MIRROR = "slideshow_mirror"
  }
  interface OnImageDeletedListener {
    fun onImageDeleted(deletedIndex: Int)
  }
  // ===== VIEWS =====
  private lateinit var viewPagerImg:   ViewPager2
  private lateinit var topBar:         View
  private lateinit var bottomBar:      View
  private lateinit var imageIndexText: TextView
  private lateinit var imageNameText:  TextView
  private lateinit var backButton:     ImageButton
  private lateinit var moreButton:     ImageButton
  private lateinit var vidPlayButton:  ImageButton
  private lateinit var leftHotspot:    View
  private lateinit var rightHotspot:   View
  private lateinit var btnParent:      ImageButton
  private lateinit var btnRotateR:     ImageButton
  private lateinit var btnMirrorH:     ImageButton
  private lateinit var btnMirrorV:     ImageButton
  private lateinit var btnOrientation: ImageButton
  private lateinit var btnEnhance:     ImageButton
  private lateinit var btnRotateL:     ImageButton
  private lateinit var stripRecycler:  RecyclerView
  private var floatWin:       FloatWin? = null
  private var adapter:        VpAdpt? = null
  private var stripAdapter:   PhotosAdpt? = null
  private var gestureHandler: ImgGstHandler? = null
  private var matrixCtrl: ImageMatrixController? = null
  private var colorAdjuster: ImgClrAdjst? = null
  private var playOverlay:           View? = null
  private var playButtonOverlayView: View? = null
  // ===== VIEW MODELS, SHARED VM, ADAPTER, GESTURE, SLIDESHOW =====
  private lateinit var viewerViewModel:    ImgVM
  private lateinit var sharedViewModel:    SharedVM
  private lateinit var repo:               SetRepo
  private lateinit var imgSetDlg:          ImgSetDlg
  // private lateinit var activityRef:        MainActivity
  private lateinit var fabMenu:            FloatMenu
  private lateinit var deletionHelper:     MediaDeletionHelper
  private lateinit var treePickerLauncher: ActivityResultLauncher<Uri?>
  private lateinit var slideshow:          SlideShowCtrl
  // ===== STATE =====
  private var isUiVisible = true
  private var isInfoVisible = false
  private var isEnhanced = false 
  // var isMaxBrightness: Boolean = true; private set
  var isSlideshowAutoRotateDirection = "right"; // private set
  var brightness = -1.0f
  // ===== LIFECYCLE =====
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Register the tree picker launcher
    treePickerLauncher = registerForActivityResult(
      ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
      deletionHelper.handleTreePickerResult(uri)
    }
    //  userPickedImage = arguments?.getBoolean("user_picked", false) ?: false
  }
  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    return inflater.inflate(R.layout.frg_img, container, false)
  }
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    viewerViewModel = ViewModelProvider(requireActivity())[ImgVM::class.java]
    sharedViewModel = ViewModelProvider(requireActivity())[SharedVM::class.java]
    repo = (activity as? MainActivity)?.settingsRepo ?: throw IllegalStateException("Repo not found")
    deletionHelper = MediaDeletionHelper(
      context = requireContext(),
      treePickerLauncher = treePickerLauncher,
      onAllDeletionsComplete = { deletedCount, failedCount ->
        // Update UI accordingly
        if (deletedCount > 0) {
          val index = viewerViewModel.currentIndex.value
          viewerViewModel.deleteCurrentItem()   // adjust list
          sharedViewModel.notifyItemDeleted(index)
        }
        if (failedCount > 0) {
          Toast.makeText(context, "Failed to delete $failedCount file(s)", Toast.LENGTH_SHORT).show()
        } else if (deletedCount > 0) {
          Toast.makeText(context, "Deleted successfully", Toast.LENGTH_SHORT).show()
        }
      }
    )
    initializeViews(view)
    // viewerViewModel = ViewModelProvider(requireActivity())[ImgVM::class.java]
    // sharedViewModel = ViewModelProvider(requireActivity())[SharedVM::class.java]
    // repo = (activity as? MainActivity)?.settingsRepo ?: throw IllegalStateException("Repo not found")
    
    imgSetDlg = ImgSetDlg(repo, viewLifecycleOwner, requireActivity())
    imgSetDlg.bindHotspotViews(leftHotspot, rightHotspot)
    imgSetDlg.observe()
    
    setupGestureHandler()
    setupViewPager()
    setupStrip()
    setupUiListeners()
    loadMediaFromArguments()
    observeViewModel()
    initFabMenu(view)
    hideUiElements()
    initSlideShow()
  }
  override fun onResume() {
    super.onResume()
    hideUiElements()
  }
  override fun onPause() {
    (activity as? MainActivity)?.toggleOrientationReset()
    super.onPause()
    imgSetDlg.applyMaxBrightness(false)
    stopSlideShow()
    adapter?.pauseAllVideos()
    viewerViewModel.saveState()
    floatWin?.dismiss()
    // SAVE FloatMenu in Prefs
    fabMenu.let {
      val pos = it.getAbsolutePosition()
      MainActivity.prefs.edit()
      .putInt("fab_x", pos.x)
      .putInt("fab_y", pos.y)
      .apply()
    }
  }
  override fun onDestroyView() {
    (activity as? MainActivity)?.toggleOrientationReset()
    super.onDestroyView()
    sharedViewModel.clearViewingPosition()
    floatWin?.dismiss()
    fabMenu.dismiss()
    imgSetDlg.applyMaxBrightness(false)
    adapter?.releaseAllPlayers()
    stopSlideShow()
    gestureHandler = null
    adapter = null
    stripAdapter = null
  }
  // ===== SETUP =====
  private fun initializeViews(view: View) {
    viewPagerImg = view.findViewById(R.id.vP2)
    topBar = view.findViewById(R.id.topBar)
    bottomBar = view.findViewById(R.id.botBar)
    imageIndexText = view.findViewById(R.id.imgIndex)
    imageNameText = view.findViewById(R.id.imgName)
    backButton = view.findViewById(R.id.backBtn)
    moreButton = view.findViewById(R.id.moreBtn)
    vidPlayButton = view.findViewById(R.id.vidPlayBtn)
    leftHotspot = view.findViewById(R.id.hotspotL)
    rightHotspot = view.findViewById(R.id.hotspotR)
    btnParent = view.findViewById(R.id.btnParent)
    btnRotateR = view.findViewById(R.id.btnRotateR)
    btnRotateL = view.findViewById(R.id.btnRotateL)
    btnMirrorH = view.findViewById(R.id.btnMirrorH)
    btnMirrorV = view.findViewById(R.id.btnMirrorV)
    btnOrientation = view.findViewById(R.id.btnOrientation)
    btnEnhance = view.findViewById(R.id.btnEnhance)
    stripRecycler = view.findViewById(R.id.stripRecycler)
  }
  private fun setupViewPager() {
    viewPagerImg.offscreenPageLimit = 2
    viewPagerImg.setPageTransformer(MarginPageTransformer(10))
    viewPagerImg.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
          super.onPageSelected(position)
          updateVP(position)
        }
        override fun onPageScrollStateChanged(state: Int) {
          super.onPageScrollStateChanged(state)
          if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
            viewPagerImg.isUserInputEnabled = true
          }
        }
    })
    viewPagerImg.post {
      updateVP(viewPagerImg.currentItem)
    }
    viewPagerImg.apply {
      scaleX = 1f
      scaleY = 1f
      translationX = 0f
      translationY = 0f
      alpha = 1f
    }
  }
  private fun updateVP(position: Int) {
    val page = adapter?.getZoomablePageAt(position)
    val isSsiv = page is SsivZoomablePage
    page?.resetToFit(false)
    gestureHandler?.setCurrentPage(page, isSsiv)
    viewerViewModel.navigateToIndex(position)
    sharedViewModel.updateViewingPosition(
      index = position,
      uri = viewerViewModel.getCurrentMedia()?.uri?.toString()
    )
    adapter?.setCurrentPosition(position)
    stripAdapter?.setCurrentViewingIndex(position)
    scrollStripToCenter(position, animate = true)
    page?.setOnScaleChangedListener { scale ->
      val atMin = scale <= page.getMinScale() * 1.01f
      viewPagerImg.isUserInputEnabled = atMin
    }
    if (isEnhanced) {
      page?.autoEnhance()
    } else {
      page?.reset()
    }
    updateUiForCurrentItem()
  }
  private fun setupStrip() {
    stripAdapter = PhotosAdpt(
      onMediaClick = { pos -> viewPagerImg.setCurrentItem(pos, true) },
      stripMode = true
    )
    stripRecycler.layoutManager = LinearLayoutManager(
      requireContext(), LinearLayoutManager.HORIZONTAL, false
    )
    stripRecycler.adapter = stripAdapter
  }
  private fun setupGestureHandler() {
    gestureHandler = ImgGstHandler(
      context = requireContext(),
      host = createGestureCallback(),
      activity = activity
    )
  }
  private fun createGestureCallback(): ImgGstHandler.HostCallback {
    return object : ImgGstHandler.HostCallback {
      override fun onLongPress() {
        toggleUiVisibility()
        if (slideshow.isRunning) stopSlideShow()
      }
      override fun onRequestClose() {
        animateCloseViewer()
     
      }
    }
  }
  private fun setupUiListeners() {
    backButton.setOnClickListener {
      (activity as? MainActivity)?.onBackPressedDispatcher?.onBackPressed() // (activity as? MainActivity)?.onBackPressed()

    }
    leftHotspot.setOnClickListener { prevImage() }
    rightHotspot.setOnClickListener { nextImage() }
    moreButton.setOnClickListener { showMoreMenu() }
    vidPlayButton.setOnClickListener { showVidPlay() }
  }
  // ===== OBSERVERS =====
  private fun observeViewModel() {
    lifecycleScope.launch {
      viewerViewModel.mediaList.collectLatest { mediaList ->
        adapter?.submitList(mediaList)
        stripAdapter?.submitListAdpt(mediaList)
        if (mediaList.isEmpty()) {
          (activity as? MainActivity)?.onBackPressedDispatcher?.onBackPressed()
        }
      }
    }
    lifecycleScope.launch {
      viewerViewModel.currentIndex.collectLatest { _ ->
        updateIndexDisplay()
        viewerViewModel.saveState()
      }
    }
    // lifecycleScope.launch {
    // viewerViewModel.isVideoMode.collectLatest { isVideo ->
    // if (isVideo) showPlayButtonOverlay() else hidePlayButtonOverlay()
    // }
    // }
  }
  // ===== DATA LOADING =====
  @Suppress("DEPRECATION")
  private fun loadMediaFromArguments() {
    val initialIndex = arguments?.getInt("current_index", 0) ?: 0
    val mediaList = viewerViewModel.mediaList.value
    if (mediaList.isEmpty()) {
      Toast.makeText(context, "No media to display", Toast.LENGTH_SHORT).show()
      (activity as? MainActivity)?.onBackPressedDispatcher?.onBackPressed()
      return
    }
    // The list is already inside viewerViewModel; just ensure the correct index
    viewerViewModel.navigateToIndex(initialIndex)
    adapter = VpAdpt(
      mediaList = mediaList,
      imageTouchListener = gestureHandler,
      onImageReady = { /* optional: if you need it */ }
    )
    viewPagerImg.adapter = adapter
    viewPagerImg.setCurrentItem(initialIndex, false)
    sharedViewModel.setCurrentViewingIndex(initialIndex)
    sharedViewModel.updateViewingPosition(
      index = initialIndex,
      uri = mediaList.getOrNull(initialIndex)?.uri?.toString()
    )
    // Initial filmstrip state — list + highlight, no animation on first show
    stripAdapter?.submitListAdpt(mediaList)
    stripAdapter?.setCurrentViewingIndex(initialIndex)
    // stripRecycler.post { stripRecycler.scrollToPosition(initialIndex) }
    stripRecycler.post { scrollStripToCenter(initialIndex, animate = false) }
    updateUiForCurrentItem()
    updateIndexDisplay()
  }
  // ===== UI UPDATE =====
  private fun updateUiForCurrentItem() {
    val currentMedia = viewerViewModel.getCurrentMedia() ?: return
    imageNameText.text = currentMedia.name
    vidPlayButton.visibility = if (currentMedia.isVideo()) View.VISIBLE else View.GONE
    viewerViewModel.setVideoMode(currentMedia.isVideo())
    viewerViewModel.preloadNearbyImages()
  }
  private fun updateIndexDisplay() {
    val total = viewerViewModel.mediaList.value.size
    val current = viewerViewModel.currentIndex.value + 1
    imageIndexText.text = "$current / $total"
  }
  // ===== UI VISIBILITY =====
  private fun toggleAutoEnhance() {
    val adjuster = adapter?.getZoomablePageAt(viewPagerImg.currentItem)
    // isEnhanced = MainActivity.prefs.getBoolean(PREF_KEY_AUTO_ENHANCE, false)
    isEnhanced = !isEnhanced
    if (isEnhanced) {
      adjuster?.autoEnhance()
    } else {
      adjuster?.reset()
    }
    // MainActivity.prefs.edit()
    // .putBoolean(PREF_KEY_AUTO_ENHANCE, isEnhanced)
    // .apply()
  }
  
  // fun setMaxBrightness(enable: Boolean){
  //   isMaxBrightness = enable
  // }
  private fun toggleUiVisibility() {
    if (slideshow.isRunning) return
    if (isUiVisible) {
      hideUiElements()
      (activity as? MainActivity)?.setViewerMode(true)
      if (fabMenu.isExpandedNow) {
        fabMenu.toggle()
      } else {
        fabMenu.setVisible(true, 0.4f)
      }
    } else {
      showUiElements()
      fabMenu.setVisible(true, 0.4f)
    }
  }
  private fun hideUiElements() {
    topBar.animate()
    .alpha(0f)
    .translationY(-topBar.height.toFloat())
    .setInterpolator(AccelerateInterpolator())
    .setDuration(600)
    .start()
    bottomBar.animate()
    .alpha(0f)
    .translationY(bottomBar.height.toFloat())
    .setInterpolator(AccelerateInterpolator())
    .setDuration(600)
    .withEndAction {
      topBar.isVisible = false
      bottomBar.isVisible = false
      isUiVisible = false
    }
    .start()
  }
  private fun showUiElements() {
    topBar.animate()
    .alpha(1f)
    .translationY(0f)
    .setInterpolator(AccelerateInterpolator())
    .setDuration(600)
    .start()
    bottomBar.animate()
    .alpha(1f)
    .translationY(0f)
    .setInterpolator(AccelerateInterpolator())
    .setDuration(600)
    .withEndAction {
      topBar.isVisible = true
      bottomBar.isVisible = true
      isUiVisible = true
    }
    .start()
  }
  private fun animateCloseViewer() {
    if (!::viewPagerImg.isInitialized) {
      (activity as? MainActivity)?.onBackPressedDispatcher?.onBackPressed()
      return
    }
    viewPagerImg.animate()
    .translationY(viewPagerImg.height.toFloat())
    .setDuration(500)
    .withEndAction { (activity as? MainActivity)?.onBackPressedDispatcher?.onBackPressed() }
    .start()
  }
  // ===== floating button's Items =====
  private fun initFabMenu(root: View) {
    val container = root.findViewById<FrameLayout>(R.id.fabContainer)
    val children = listOf(// Child buttons (order matters)
      btnMirrorV,
      btnRotateR,
      btnOrientation,
      btnEnhance,
      btnRotateL,
      btnMirrorH
    )
    // Create FloatMenu
    fabMenu = FloatMenu(container, btnParent, children).apply {
      setDragEnabled(true)
      setAutoDirection(true)
      setAnimationDuration(500)
      setVisible(true, 0.4f)
      val positions = listOf(
        FloatMenu.ChildPosition.TOP,
        FloatMenu.ChildPosition.TOP_CENTER,
        FloatMenu.ChildPosition.CENTER,
        FloatMenu.ChildPosition.CENTER,
        FloatMenu.ChildPosition.BOTTOM_CENTER,
        FloatMenu.ChildPosition.BOTTOM
      )
      setChildPositions(positions)
      setChildPositionRadius(80f) // Base distance for the 1st item.
      setChildSpacingDp(48f)      // Gap between 1st and 2nd item in the same pipeline
    }
    // --- RESTORE from preference ---
    val savedX = MainActivity.prefs.getInt("fab_x", -1)
    val savedY = MainActivity.prefs.getInt("fab_y", -1)
    if (savedX != -1 && savedY != -1) {
      fabMenu.restoreAbsolutePosition(savedX, savedY)
    } else {
      container.post { fabMenu.snapImmediate() }
    }
    // Parent click -> toggle
    btnParent.setOnClickListener {
      fabMenu.toggle()
      val alpha = if (fabMenu.isExpandedNow) 0.4f else 0.4f
      fabMenu.setVisible(true, alpha)
    }
    btnParent.setOnLongClickListener {
      true // consumed – drag will be handled by FloatMenu's touch listener
    }
    // Child listeners
    btnOrientation.setOnClickListener {
      (activity as? MainActivity)?.toggleOrientation()
      val page = adapter?.getZoomablePageAt(viewPagerImg.currentItem)
      page?.resetToFit(true)
    }
    btnEnhance.setOnClickListener { toggleAutoEnhance() }
    btnMirrorH.setOnClickListener {
      val page = adapter?.getZoomablePageAt(viewPagerImg.currentItem)
      page?.mirrorHorizontal(true)
      page?.resetToFit(true)
    }
    btnMirrorV.setOnClickListener {
      val page = adapter?.getZoomablePageAt(viewPagerImg.currentItem)
      page?.mirrorVertical(true)
      page?.resetToFit(true)
    }
    btnRotateR.setOnClickListener {
      val page = adapter?.getZoomablePageAt(viewPagerImg.currentItem)
      page?.rotateBy(90f)
      page?.resetToFit(true)
    }
    btnRotateL.setOnClickListener {
      val page = adapter?.getZoomablePageAt(viewPagerImg.currentItem)
      page?.rotateBy(-90f)
      page?.resetToFit(true)
    }
  }
  // ===== SLIDESHOW =====
  private fun showMoreMenu() {
    val ctx = context ?: return
    val anchor = moreButton
    floatWin = FloatWin(ctx, anchor)
    // val widthPx = (250 * resources.displayMetrics.density).toInt()
    floatWin?.show(
      R.layout.float_img_more,
      ViewGroup.LayoutParams.WRAP_CONTENT,
      ViewGroup.LayoutParams.WRAP_CONTENT,
      180,
      30,
      Gravity.TOP or Gravity.END,
      { content ->
        content.findViewById<View>(R.id.btn_more_orientation).setOnClickListener {
          (activity as? MainActivity)?.toggleOrientation()
          val page = adapter?.getZoomablePageAt(viewPagerImg.currentItem)
          page?.resetToFit(true)
        }
        content.findViewById<View>(R.id.btn_more_delete).setOnClickListener {
          confirmDeleteImage()
          floatWin?.dismiss()
        }
        content.findViewById<View>(R.id.btn_more_slideshow).setOnClickListener {
          askForSlideShowInterval()
          floatWin?.dismiss()
        }
        content.findViewById<View>(R.id.btn_more_enhance).setOnClickListener {
          toggleAutoEnhance()
        }
        content.findViewById<View>(R.id.btn_more_info).setOnClickListener {
          showImageInfo()
        }
      },
      true,
      focusable = false,
      FloatWin.AnimationConfig(
        duration = 600,
        interpolator = AccelerateInterpolator(),
        type = FloatWin.AnimationType.SLIDE_DOWN
      )
    )
  }
  private fun initSlideShow() {
    slideshow = SlideShowCtrl(
      viewPager = viewPagerImg,
      settings = imgSetDlg,
      // frg = this,
      adapter = adapter!!,
      viewModel = viewerViewModel,
      activity = activity,
      prefs = MainActivity.prefs,
      uiCallback = object : SlideShowCtrl.UiCallback {
        override fun onSlideShowStarted() {
          fabMenu.setVisible(true, 0.4f)
          hideUiElements()
        }
        override fun onSlideShowStopped() {
          viewPagerImg.scaleX = 1f
          viewPagerImg.scaleY = 1f
          showUiElements()
          fabMenu.setVisible(true, 0.4f)
        }
    })
    imgSetDlg.bindSlideshow(slideshow)
    imgSetDlg.seedFromPrefs()  // apply current prefs before observe() fires
  }
  // dialog positive button (unchanged)
  private fun performSlideshowStart(input: EditText, lastSec: Int) {
    val txt = input.text.toString().trim()
    val sec = txt.ifEmpty { lastSec.toString() }.toIntOrNull() ?: lastSec
    MainActivity.prefs.edit().putInt(PREF_KEY_SLIDESHOW_DELAY, sec).apply()
    slideshow.start(sec)
  }
  // manual stop
  private fun stopSlideShow() {
    slideshow.stop()
  }
  private fun askForSlideShowInterval() {
    val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dlg_bg, null)
    val titleView: TextView = dialogView.findViewById(R.id.dlg_title)
    val input: EditText = dialogView.findViewById(R.id.dlg_input)
    val negBtn: MaterialButton = dialogView.findViewById(R.id.dlg_neg)
    val posBtn: MaterialButton = dialogView.findViewById(R.id.dlg_pos)
    // Advanced Section
    val advancedToggle: View = dialogView.findViewById(R.id.dlg_advanced_toggle)
    val advancedArrow: ImageView = dialogView.findViewById(R.id.dlg_advanced_arrow)
    val advancedLayout: View = dialogView.findViewById(R.id.dlg_advance)
    // New 3-state RadioGroup (Disable, Right, Left)
    val rotateGRadio: RadioGroup = dialogView.findViewById(R.id.dlg_rotation_group)
    val rotateDisableRadio: RadioButton = dialogView.findViewById(R.id.dlg_rotate_disable)
    val rotateRRadio: RadioButton = dialogView.findViewById(R.id.dlg_rotate_right)
    val rotateLRadio: RadioButton = dialogView.findViewById(R.id.dlg_rotate_left)
    val mirrorCheck: CheckBox = dialogView.findViewById(R.id.dlg_mirror)
    // --- Expandable Logic ---
    advancedToggle.setOnClickListener {
      if (advancedLayout.isVisible) {
        advancedLayout.visibility = View.GONE
        advancedArrow.setImageResource(R.drawable.ic_expand_more)
      } else {
        advancedLayout.visibility = View.VISIBLE
        advancedArrow.setImageResource(R.drawable.ic_expand_less)
      }
    }
    // --- Initial State ---
    mirrorCheck.isChecked = imgSetDlg.slideshowAutoMirrorEnabled
    // Set initial radio selection based on the current mode
    val currentMode = MainActivity.prefs.getString(PrefKeys.IMG_AUTO_ROTATION_MODE, "disable") ?: "disable"
    when (currentMode) {
      "right" -> rotateRRadio.isChecked = true
      "left" -> rotateLRadio.isChecked = true
      else -> rotateDisableRadio.isChecked = true
    }
    // Enable/disable mirror based on selection (Mirror is disabled if mode is "disable")
    fun updateMirrorState() {
      val isEnabled = !rotateDisableRadio.isChecked
      mirrorCheck.isEnabled = isEnabled
    }
    updateMirrorState()
    rotateGRadio.setOnCheckedChangeListener { _, _ -> updateMirrorState() }
    titleView.text = "Seconds per Slide"
    val lastSec = MainActivity.prefs.getInt(PREF_KEY_SLIDESHOW_DELAY, 5)
    input.setText(lastSec.toString())
    val dialog = AlertDialog.Builder(requireContext())
    .setView(dialogView)
    .create()
    negBtn.setOnClickListener { dialog.dismiss() }
    posBtn.setOnClickListener {
      val interval = input.text.toString().toIntOrNull() ?: 5
      // Determine selected mode
      val selectedMode = when {
        rotateRRadio.isChecked -> "right"
        rotateLRadio.isChecked -> "left"
        else -> "disable"
      }
      
      // Update controller
        // imgSetDlg.setAutoRotateEnabled(selectedMode != "disable")
        // imgSetDlg.setAutoRotateDirection(selectedMode) 
        imgSetDlg.setMirrorEnabled(mirrorCheck.isChecked)
      // Save to prefs
      MainActivity.prefs.edit()
      .putInt(PREF_KEY_SLIDESHOW_DELAY, interval)
      .putString(PrefKeys.IMG_AUTO_ROTATION_MODE, selectedMode)
      // .putString(PrefKeys.IMG_AUTO_ROTATION_DIRECTION, direction)
      .putBoolean(PrefKeys.IMG_MIRROR, mirrorCheck.isChecked)
      .apply()
      performSlideshowStart(input, lastSec)
      dialog.dismiss()
    }
    input.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
        posBtn.performClick()
        true
      } else false
    }
    dialog.window?.apply {
      setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
      dialog.show()
      val width = (resources.displayMetrics.widthPixels * 0.7).toInt()
      setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
      setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
    }
    input.requestFocus()
    input.selectAll()
  }
  // ===== left/right hotspots =====
  private fun nextImage() {
    if (!imgSetDlg.hotspotEnabled) return
    val page = adapter?.getZoomablePageAt(viewPagerImg.currentItem)
    val isScaledNearOne = page?.let { scale ->
      val scale = page.getScale()
      scale <= page.getMinScale() * 1.05f
    } ?: false
    if (isScaledNearOne && viewerViewModel.hasNext()) {
      viewPagerImg.animate()
      .alpha(1f)
      .setDuration(50)
      .withEndAction {
        viewPagerImg.currentItem++
        viewPagerImg.alpha = 1f
        viewPagerImg.animate().alpha(1f).setDuration(500).start()
      }
      .start()
    }
  }
  private fun prevImage() {
    if (!imgSetDlg.hotspotEnabled) return
    val page = adapter?.getZoomablePageAt(viewPagerImg.currentItem)
    val isScaledNearOne = page?.let { scale ->
      val scale = page.getScale()
      scale <= page.getMinScale() * 1.05f
    } ?: false
    if (isScaledNearOne && viewerViewModel.hasPrevious()) {
      viewPagerImg.animate()
      .alpha(1f)
      .setDuration(50)
      .withEndAction {
        viewPagerImg.currentItem--
        viewPagerImg.alpha = 1f
        viewPagerImg.animate().alpha(1f).setDuration(500).start()
      }
      .start()
    }
  }
  // ===== DELETE =====
  private fun confirmDeleteImage() {
    val media = viewerViewModel.getCurrentMedia() ?: return
    val deleteMsg = buildString {
      append("${if (media.isVideo()) "Video" else "Image"} ")        // img or vid
      append("Name: ${media.name}\n")                                // file name
      append("${formatSize(media.size)} - ")                         // Size
      if (media.isVideo()) append("${media.getFormattedDuration()} ")   // Duration if it's Vid
      append("${media.width}x${media.height}\n")                     // dimensions
      append("(This cannot be undone.)")                             // msg
    }
    AlertDialog.Builder(requireContext())
    .setTitle("Delete Permanently?")
    .setMessage(deleteMsg)
    .setPositiveButton("Delete") { _, _ -> deleteCurrentImage() }
    .setNegativeButton("Cancel", null)
    .show()
  }
  private fun deleteCurrentImage() {
    val media = viewerViewModel.getCurrentMedia() ?: return
    val item = MediaItemz(uri = media.uri, displayName = media.name)
    deletionHelper.deleteItems(listOf(item))
  }
  // ===== INFO =====
  private fun toggleInfoVisibility() {
    if (floatWin?.isShowing() == true) {
      floatWin?.dismissAnimated(// hide
        FloatWin.AnimationConfig(
          duration = 500,
          interpolator = AccelerateInterpolator(),
          type = FloatWin.AnimationType.SLIDE_DOWN
        )
      )
    } else {
      showImageInfo()      // show
    }
  }
  private fun showImageInfo() {
    val ctx = context ?: return
    floatWin = FloatWin(ctx, bottomBar)
    val widthPx = (300 * resources.displayMetrics.density).toInt()
    val media = viewerViewModel.getCurrentMedia() ?: return
    floatWin?.show(
      R.layout.float_img_info,
      widthPx,
      ViewGroup.LayoutParams.WRAP_CONTENT,
      300,
      0,
      Gravity.BOTTOM or Gravity.CENTER,
      { content ->
        val titleTxt = content.findViewById<TextView>(R.id.txt_title)
        titleTxt.text = "Name: ${media.name}"
        val detailTxt = content.findViewById<TextView>(R.id.txt_details)
        detailTxt.text = buildString {
          append("Type: ${if (media.isVideo()) "Video" else "Image"}\n")
          append("Size: ${formatSize(media.size)}\n")
          append("Dimensions: ${media.width}x${media.height}\n")
          append("Modified: ${formatDate(media.dateModified)}")
          if (media.isVideo()) append("\nDuration: ${media.getFormattedDuration()}")
        }
        content.findViewById<View>(R.id.btn_close).setOnClickListener {
          floatWin?.dismiss()
        }
      },
      false,
      false,
      FloatWin.AnimationConfig(
        duration = 500,
        interpolator = AccelerateInterpolator(),
        type = FloatWin.AnimationType.SLIDE_UP
      )
    )
  }
  private fun formatSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 ->
    String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))
    else ->
    String.format(java.util.Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024))
  }
  private fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp * 1000))
  }
  // ===== VIDEO PLAY OVERLAY =====
  private fun showVidPlay() {
    val position = viewerViewModel.currentIndex.value
    val mediaList = viewerViewModel.mediaList.value
    if (mediaList.isNotEmpty()) {
      VidFrg().apply {
        arguments = Bundle().apply {
          putParcelableArrayList("media_items", ArrayList(mediaList))
          putInt("current_index", position)
        }
      }.also { (activity as? MainActivity)?.openViewer(it) }
    }
  }
  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    val page = adapter?.getZoomablePageAt(viewPagerImg.currentItem)
    page?.resetToFit(false)
  } private class CenterSmoothScroller(context: Context) : LinearSmoothScroller(context) {
    override fun calculateDtToFit(
      viewStart: Int, viewEnd: Int,
      boxStart: Int, boxEnd: Int,
      snapPreference: Int
    ): Int = (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
  }
  private fun scrollStripToCenter(position: Int, animate: Boolean = true) {
    val lm = stripRecycler.layoutManager as? LinearLayoutManager ?: return
    if (animate) {
      lm.startSmoothScroll(CenterSmoothScroller(requireContext()).apply {
          targetPosition = position
      })
    } else {
      // Initial display — calculate offset so item lands at the center of the strip
      val itemWidth = resources.getDimensionPixelSize(R.dimen.strip_thumb_size) // 60dp
      val offset = (stripRecycler.width / 2) - (itemWidth / 2)
      lm.scrollToPositionWithOffset(position, offset)
    }
  }
}
