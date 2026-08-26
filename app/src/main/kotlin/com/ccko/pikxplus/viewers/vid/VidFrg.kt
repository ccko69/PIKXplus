package com.ccko.pikxplus.viewers.vid

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.ccko.pikxplus.MainActivity
import com.ccko.pikxplus.R
import com.ccko.pikxplus.viewers.img.ImgFrg
import com.ccko.pikxplus.shared.data.MediaItems
import com.ccko.pikxplus.viewers.vid.VidGstHandler
import com.ccko.pikxplus.shared.utils.FloatWin
import com.ccko.pikxplus.ux.settings.VidSetDlg
import com.ccko.pikxplus.ux.settings.PrefKeys
import java.util.Locale
import kotlin.math.ln

import android.util.Log

/**
 * Full-screen video player with Media3 ExoPlayer.
 * Opened when user taps play button on video thumbnail in ImgFrg.
 */
class VidFrg : Fragment() {

    companion object {
        private const val TAG               = "VidFrg"
        private const val ROTATION_DURATION = 500L
    }
    // ===== VIEWS =====
    private lateinit var exoPlyView:    PlayerView
    private lateinit var vidController: VidCtrl
    private lateinit var vidSetDlg:     VidSetDlg
    // ===== MEDIA3 =====
    private var player: ExoPlayer? = null
    // ===== GESTURE HANDLER =====
    private var gestureHandler: VidGstHandler? = null
    // ===== AUDIO / BRIGHTNESS =====
    private lateinit var audioManager: AudioManager
    private var maxVolume = 0
    private var currentBrightness = -1f
    // ===== PROGRESS UPDATER =====
    private val controlsHandler = Handler(Looper.getMainLooper())
    private var progressUpdater: Runnable? = null
    private var progressUpdatesPaused = false
    private var wasPlayingBeforeSeek  = false
    private var isShuffleEnabled      = false
    // ===== DATA =====
    private var mediaItems:   List<MediaItems> = emptyList()
    private var currentIndex: Int = 0
    // ===== FLOATING WINDOWS =====
    private var floatWin:          FloatWin? = null
    private var volumeOverlay:     FloatWin? = null
    private var brightnessOverlay: FloatWin? = null
    val imgFrg = ImgFrg()
    // ===== ROTATION =====
    private var rotationSteps = 0
    // ===== ZOOM / PAN STATE =====
    private var currentScale = 1.0f
    private var translationX = 0f
    private var translationY = 0f
    private val MAX_SCALE          = 20.0f
    private val MIN_SCALE_DEFAULT  = 1.0f
    private val MIN_SCALE_FALLBACK = 0.5f
    private val PAN_LIMIT_RATIO    = 0.5f
    
    // ===== LIFECYCLE =====
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.frg_vidply, container, false)
    }
    @Suppress("DEPRECATION")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        exoPlyView       = view.findViewById(R.id.exoPly)
        vidController    = view.findViewById(R.id.vidCtrlRoot)
        exoPlyView.pivotX = 0f
        exoPlyView.pivotY = 0f
        vidController.setRatioMode(VidCtrl.RatioMode.FIT)
        
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume    = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        floatWin          = FloatWin(requireContext(), view)
        volumeOverlay     = FloatWin(requireContext(), view)
        brightnessOverlay = FloatWin(requireContext(), view)

        gestureHandler = VidGstHandler(
            context = requireContext(),
            host    = createGestureCallback()
        )
        vidController.setOnTouchListener(gestureHandler)

        setupVideoOverlayControls()
        
        // val imgFrg = ImgFrg()
        // imgFrg.arguments = Bundle().apply {
            // putInt("current_index", position)
        // }

        val repo = (activity as? MainActivity)?.settingsRepo ?: throw IllegalStateException("Repo not found")
        vidSetDlg = VidSetDlg(repo, viewLifecycleOwner, requireActivity())
        vidSetDlg.observe()
        vidSetDlg.restoreSavedSwipeBrightness()
        vidSetDlg.bindVidController(vidController)

        arguments?.let {
            mediaItems   = it.getParcelableArrayList("media_items") ?: emptyList()
            currentIndex = it.getInt("current_index", 0)

            if (mediaItems.isNotEmpty() && currentIndex in mediaItems.indices) {
                val item = mediaItems[currentIndex]
                
                imgFrg.arguments = Bundle().apply {
                    putInt("current_index", 0)
                }
                
                if (item.isVideo()) {
                    playVideo(item)
                } else {
                    Toast.makeText(context, "Not a video!", Toast.LENGTH_SHORT).show()
                    // (activity as? MainActivity)?.onBackPressedDispatcher?.onBackPressed()
                    (activity as? MainActivity)?.openViewer(imgFrg)
                }
            } else {
                Toast.makeText(context, "Invalid video", Toast.LENGTH_SHORT).show()
                // (activity as? MainActivity)?.onBackPressedDispatcher?.onBackPressed()
                (activity as? MainActivity)?.openViewer(imgFrg)
            }
        }
    }
    override fun onResume() {
        super.onResume()
        // No gesture delegate to re-register — OnTouchListener set in onViewCreated persists.
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }
    override fun onPause() {
        super.onPause()
        player?.pause()
        (activity as? MainActivity)?.hideUiOverlay()
        (activity as? MainActivity)?.toggleOrientationReset()
    }
    override fun onDestroyView() {
        super.onDestroyView()

        stopProgressUpdater()

        (activity as? MainActivity)?.hideUiOverlay()
        (activity as? MainActivity)?.toggleOrientationReset()

        floatWin?.dismiss()
        volumeOverlay?.dismiss()
        brightnessOverlay?.dismiss()
        
        exoPlyView.clearAnimation()
        exoPlyView.animate().cancel()

        player?.release()
        player = null

        vidController.setOnTouchListener(null)
        gestureHandler?.cleanup()
        gestureHandler = null
        // Restore system brightness
        vidSetDlg.resetSwipeBrightness()
       /* if (currentBrightness >= 0) {
            val lp = requireActivity().window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            requireActivity().window.attributes = lp
        } */
    }
    // ===== GESTURE CALLBACKS =====
    private fun createGestureCallback(): VidGstHandler.HostCallback {
        return object : VidGstHandler.HostCallback {
        
            override fun onSingleTap() {
                togglevidController()
            }
            override fun onDoubleTap() { /* Seek handled in handler */ }
            
            override fun onSeek(deltaMs: Long) {
                player?.let {
                    val newPos = (it.currentPosition + deltaMs).coerceIn(0, it.duration)
                    it.seekTo(newPos) // This only happens ONCE when the user lifts their finger
                }
                progressUpdatesPaused = false
                vidController.hideSeekPreview() // Hide the floating preview text
            }
            override fun onSeekPreview(previewPositionMs: Long) {
                progressUpdatesPaused = true
                // ONLY update the UI text/seekbar, DO NOT call player.seekTo() here!
                vidController.showSeekPreview(previewPositionMs, getPlayerDuration())
            }
            override fun onVolumeChange(delta: Float) {
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val newVol     = (currentVol + (delta * maxVolume).toInt()).coerceIn(0, maxVolume)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, AudioManager.FLAG_SHOW_UI)
                showVolumeOverlay(newVol)
            }
            override fun onBrightnessChange(delta: Float) {
                val lp = requireActivity().window.attributes
                if (currentBrightness < 0) {
                    currentBrightness = lp.screenBrightness
                    if (currentBrightness < 0) currentBrightness = 0.5f
                }
                
                val newBrightness = vidSetDlg.applySwipeBrightness(delta)
                if (newBrightness >= 0) {
                    showBrightnessOverlay(newBrightness)
                }
                
                currentBrightness = (currentBrightness + delta).coerceIn(0.01f, 1.0f)
                lp.screenBrightness = currentBrightness
                requireActivity().window.attributes = lp
                MainActivity.prefs?.edit()?.putFloat(PrefKeys.VID_SWIPE_BRIGHTNESS, currentBrightness)?.apply()
                showBrightnessOverlay(currentBrightness)
            }
            override fun onPinchZoom(scaleFactor: Float, focusX: Float, focusY: Float) {
                applyScale(scaleFactor, focusX, focusY)
            }
            override fun onPan(dx: Float, dy: Float) {
                applyPan(dx, dy)
            }
            override fun getPlayerPosition(): Long = player?.currentPosition ?: 0L
            override fun getPlayerDuration(): Long {
                val duration = player?.duration ?: -1L
                return if (duration <= 0) -1L else duration
            }
            override fun pausePlayerProgressUpdates() {
                progressUpdatesPaused = true
                if (player?.isPlaying == true) {
                    player?.pause()
                    wasPlayingBeforeSeek = true
                }
            }
            override fun resumePlayerProgressUpdates() {
                progressUpdatesPaused = false
                if (wasPlayingBeforeSeek) {
                    player?.play()
                    wasPlayingBeforeSeek = false
                }
            }
        }
    }
    // ===== SCALE / PAN =====
    private fun applyScale(scaleFactor: Float, focusX: Float, focusY: Float) {
        val newScale = (currentScale * scaleFactor).coerceIn(
            if (shouldAllowQuarterScaleFallback()) MIN_SCALE_FALLBACK else MIN_SCALE_DEFAULT,
            MAX_SCALE
        )
        // Map focusX/Y from VidCtrl coordinates to ExoPlyView coordinates
        val vidLoc = IntArray(2)
        val playerLoc = IntArray(2)
        vidController.getLocationOnScreen(vidLoc)
        exoPlyView.getLocationOnScreen(playerLoc)
    
        val offsetX = vidLoc[0] - playerLoc[0]
        val offsetY = vidLoc[1] - playerLoc[1]
    
        val adjustedX = focusX - offsetX
        val adjustedY = focusY - offsetY
        // Keep the focus point stationary relative to the screen
        val scaleRatio = newScale / currentScale
        translationX = adjustedX - (adjustedX - translationX) * scaleRatio
        translationY = adjustedY - (adjustedY - translationY) * scaleRatio
    
        currentScale = newScale
        
        exoPlyView.scaleX = currentScale
        exoPlyView.scaleY = currentScale
        exoPlyView.translationX = translationX
        exoPlyView.translationY = translationY
        clampPan()
    }
    private fun shouldAllowQuarterScaleFallback(): Boolean =
        exoPlyView.width > 0 && exoPlyView.height > 0
    private fun applyPan(dx: Float, dy: Float) {
        translationX += dx
        translationY += dy
        clampPan()
    }
    private fun clampPan() {
        val viewW = exoPlyView.width.toFloat()
        val viewH = exoPlyView.height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return
    
        val parentW = (exoPlyView.parent as? View)?.width?.toFloat() ?: resources.displayMetrics.widthPixels.toFloat()
        val parentH = (exoPlyView.parent as? View)?.height?.toFloat() ?: resources.displayMetrics.heightPixels.toFloat()
    
        val scaledW = viewW * currentScale
        val scaledH = viewH * currentScale
        // Horizontal limits
        if (scaledW <= parentW) {
            // If video is smaller than screen, center it
            translationX = (parentW - scaledW) / 2f
        } else {
            // If video is larger, prevent edges from crossing screen boundaries
            val maxTranslation = 0f
            val minTranslation = parentW - scaledW
            translationX = translationX.coerceIn(minTranslation, maxTranslation)
        }
        // Vertical limits
        if (scaledH <= parentH) {
            translationY = (parentH - scaledH) / 2f
        } else {
            val maxTranslation = 0f
            val minTranslation = parentH - scaledH
            translationY = translationY.coerceIn(minTranslation, maxTranslation)
        }
        exoPlyView.translationX = translationX
        exoPlyView.translationY = translationY
    }
    // ===== CONTROLS OVERLAY SETUP =====
    private fun setupVideoOverlayControls() {
        vidController.setControlListener(object : VidCtrl.ControlListener {

            override fun onBackPressed() {
                // (activity as? MainActivity)?.onBackPressedDispatcher?.onBackPressed()
                (activity as? MainActivity)?.openViewer(imgFrg)
        
                animateCloseViewer()
            }
            override fun onPlayPauseToggle() {
                player?.let { if (it.isPlaying) it.pause() else it.play() }
            }
            override fun onSeekTo(positionMs: Long) {
                player?.seekTo(positionMs)
            }
            override fun onMuteToggle() {
                player?.let {
                    val newVolume = if (it.volume > 0) 0f else 1f
                    it.volume = newVolume
                    vidController.setMuted(newVolume == 0f)
                }
            }
            override fun onPreviousVideo() { loadPreviousVideo() }
            override fun onNextVideo()     { loadNextVideo() }
            override fun onLoopToggle() {
                player?.let {
                    val looping = it.repeatMode == Player.REPEAT_MODE_ONE
                    it.repeatMode = if (looping) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                    vidController.setLooping(!looping)
                }
            }
            override fun onLockToggle() {
                // Controls overlay handles show/hide internally on lock
            }
            override fun onOrientationToggle() {
                (activity as? MainActivity)?.toggleOrientation()
            }
            override fun onPlaylistOpen()  { showPlaylistWindow() }
            override fun onShuffleToggle() {
                toggleShuffle()
                // Toast.makeText(context, "Shuffle: Coming soon", Toast.LENGTH_SHORT).show()
            }
            override fun onSpeedChange() { showSpeedWindow() }

            override fun onRatioChange() {
                val currentMode = exoPlyView.resizeMode
                val nextMode = when (currentMode) {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> {
                     //   Toast.makeText(context, "Crop", Toast.LENGTH_SHORT).show()
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> {
                       // Toast.makeText(context, "Stretch", Toast.LENGTH_SHORT).show()
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }
                    else -> {
                        Toast.makeText(context, "Fit", Toast.LENGTH_SHORT).show()
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }
                currentScale = 1.0f
                translationX = 0f
                translationY = 0f
                exoPlyView.scaleX       = 1.0f
                exoPlyView.scaleY       = 1.0f
                exoPlyView.translationX = 0f
                exoPlyView.translationY = 0f
                exoPlyView.resizeMode   = nextMode
                
                val ratioMode = when (nextMode) {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> VidCtrl.RatioMode.FILL
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> VidCtrl.RatioMode.CROP
                    else -> VidCtrl.RatioMode.FIT
                }
                vidController.setRatioMode(ratioMode)
            }
            override fun onMoreOptions() {
                Toast.makeText(context, "More options: Coming soon", Toast.LENGTH_SHORT).show()
            }
        })
    }
    // ===== PLAYBACK =====
    private fun playVideo(item: MediaItems) {
        if (player == null) {
            player = ExoPlayer.Builder(requireContext()).build()
            exoPlyView.player = player
            player?.addListener(createPlayerListener())
            
            vidSetDlg.bindPlayer(player!!) 
        }
        val mediaItem = MediaItem.fromUri(item.uri)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()

        vidController.setVideoTitle(item.name)
        vidController.setRatioMode(
            when (exoPlyView.resizeMode) {
                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> VidCtrl.RatioMode.FILL
                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> VidCtrl.RatioMode.CROP
            else -> VidCtrl.RatioMode.FIT
            }
        )
        vidController.show()
    }
    private fun createPlayerListener(): Player.Listener {
        return object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) loadNextVideo()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                vidController.updatePlayPauseButton(isPlaying)
                if (isPlaying) startProgressUpdater() else stopProgressUpdater()
            }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                vidSetDlg.setVideoDimensions(videoSize.width, videoSize.height)
            }
        }
    }
    private fun loadNextVideo() {
        if (mediaItems.isEmpty() || currentIndex >= mediaItems.size - 1) {
            Toast.makeText(context, "No more videos", Toast.LENGTH_SHORT).show()
            return
        }
        if (isShuffleEnabled) {
            shuffleToRandomVideo()
            return
        }
        for (i in currentIndex + 1 until mediaItems.size) {
            if (mediaItems[i].isVideo()) {
                currentIndex = i
                playVideo(mediaItems[i])
                return
            }
        }
        Toast.makeText(context, "No more videos", Toast.LENGTH_SHORT).show()
    }
    private fun loadPreviousVideo() {
    
        if (mediaItems.isEmpty() || currentIndex <= 0) {
            Toast.makeText(context, "No previous videos", Toast.LENGTH_SHORT).show()
            return
        }
        if (isShuffleEnabled) {
            shuffleToRandomVideo()
            return
        }
        for (i in currentIndex - 1 downTo 0) {
            if (mediaItems[i].isVideo()) {
                currentIndex = i
                playVideo(mediaItems[i])
                return
            }
        }
        Toast.makeText(context, "No previous videos", Toast.LENGTH_SHORT).show()
    }
    // ===== UI TOGGLE =====
    private fun togglevidController() {
        if (vidController.isShowing()) {
            vidController.hide()
        } else {
            vidController.show()
        }
    }
    // ===== PROGRESS UPDATER =====
    private fun startProgressUpdater() {
        if (progressUpdater == null) {
            progressUpdater = Runnable {
                if (!progressUpdatesPaused && player != null) {
                    vidController.updateProgress(
                        player!!.currentPosition,
                        player!!.duration
                    )
                }
                controlsHandler.postDelayed(progressUpdater!!, 500)
            }
        }
        controlsHandler.removeCallbacks(progressUpdater!!)
        controlsHandler.post(progressUpdater!!)
    }
    private fun stopProgressUpdater() {
        progressUpdater?.let {
            controlsHandler.removeCallbacks(it)
            progressUpdater = null
        }
    }
    // ===== VOLUME / BRIGHTNESS OVERLAYS =====
    private fun showVolumeOverlay(volume: Int) {
        val percentage = ((volume / maxVolume.toFloat()) * 100).toInt()
        volumeOverlay?.showOrUpdate(FloatWin.TYPE_VOLUME, R.layout.floating_volume) { content ->
            val seekBar: SeekBar   = content.findViewById(R.id.volumeSeekBar)
            val text:    TextView  = content.findViewById(R.id.volumeText)
            val icon:    ImageView = content.findViewById(R.id.volumeIcon)
            // Match seekbar steps to system volume steps (typically 15) so
            // the thumb position matches the system slider exactly.
            seekBar.max       = maxVolume
            seekBar.progress  = volume
            seekBar.isEnabled = true
            text.text         = "$percentage%"
            icon.setImageResource(
                if (percentage == 0) R.drawable.ic_volume_off else R.drawable.ic_volume_on
            )
        }
    }
    private fun showBrightnessOverlay(brightness: Float) {
        val percentage = (brightness * 100).toInt()
        brightnessOverlay?.showOrUpdate(FloatWin.TYPE_BRIGHTNESS, R.layout.floating_brightness) { content ->
            val seekBar: SeekBar  = content.findViewById(R.id.brightnessSeekBar)
            val text:    TextView = content.findViewById(R.id.brightnessText)

            seekBar.max       = 100
            seekBar.progress  = percentage
            seekBar.isEnabled = true
            text.text         = "$percentage%"
        }
    }
    // ===== SPEED CONTROL =====
    private fun showSpeedWindow() {
        if (floatWin?.isShowing() == true) {
            floatWin?.dismiss()
            return
        }
        floatWin?.showTyped(FloatWin.TYPE_SPEED, R.layout.floating_speed) { content ->
            setupSpeedWindowLogic(content)
        }
    }
    private fun setupSpeedWindowLogic(container: View) {
        val speedSeekBar:   SeekBar     = container.findViewById(R.id.speedSeekBar)
        val speedValueText: TextView    = container.findViewById(R.id.speedValueText)
        val minusBtn:       ImageButton = container.findViewById(R.id.speedMinusBtn)
        val plusBtn:        ImageButton = container.findViewById(R.id.speedPlusBtn)

        val MIN_PROGRESS = 0
        val MAX_PROGRESS = 150
        val STEP         = 5

        speedSeekBar.max = MAX_PROGRESS

        val progressToSpeed: (Int)   -> Float = { 0.5f + (it / 100f) }
        val speedToProgress: (Float) -> Int   = { kotlin.math.round((it - 0.5f) * 100f).toInt() }

        val applySpeedFromProgress = Runnable {
            val progress = speedSeekBar.progress
            val snapped  = (progress / STEP) * STEP
            if (snapped != progress) speedSeekBar.progress = snapped

            val newSpeed = progressToSpeed(speedSeekBar.progress)
            speedValueText.text = String.format(Locale.getDefault(), "%.2fx", newSpeed)

            player?.setPlaybackParameters(
                androidx.media3.common.PlaybackParameters(newSpeed)
            )
            vidController.setSpeedActive(kotlin.math.abs(newSpeed - 1.0f) > 0.001f)
        }
        var currentSpeed = 1.0f
        try {
            currentSpeed = player?.playbackParameters?.speed ?: 1.0f
        } catch (e: Exception) { /* ignore */ }

        var initialProgress = speedToProgress(currentSpeed).coerceIn(MIN_PROGRESS, MAX_PROGRESS)
        initialProgress     = (initialProgress / STEP) * STEP
        speedSeekBar.progress = initialProgress
        applySpeedFromProgress.run()

        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                applySpeedFromProgress.run()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { stopProgressUpdater() }
            override fun onStopTrackingTouch(seekBar: SeekBar?)  { startProgressUpdater() }
        })
        minusBtn.setOnClickListener {
            speedSeekBar.progress = (speedSeekBar.progress - STEP).coerceAtLeast(MIN_PROGRESS)
            applySpeedFromProgress.run()
        }
        plusBtn.setOnClickListener {
            speedSeekBar.progress = (speedSeekBar.progress + STEP).coerceAtMost(MAX_PROGRESS)
            applySpeedFromProgress.run()
        }
    }
    // ===== ROTATION =====
    private fun applyRotationAnimated() {
        val targetAngle  = rotationSteps * 90f
        val currentAngle = exoPlyView.rotation

        ObjectAnimator.ofFloat(exoPlyView, "rotation", currentAngle, targetAngle).apply {
            duration     = ROTATION_DURATION
            interpolator = DecelerateInterpolator()
            start()
        }
        animateClamp()
    }
    private fun animateClamp() {
        val parent = exoPlyView.parent as? View ?: return
        if (parent.width == 0 || parent.height == 0) {
            parent.post(this::animateClamp)
            return
        }
        val lp          = exoPlyView.layoutParams
        val startWidth  = lp.width
        val startHeight = lp.height
        val pw          = parent.width
        val ph          = parent.height
        val swap        = (rotationSteps % 2) != 0
        val targetWidth = if (swap) ph else pw
        val targetHeight= if (swap) pw else ph

        if (startWidth == targetWidth && startHeight == targetHeight) return

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration     = ROTATION_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val f    = animation.animatedFraction
                val animLp = exoPlyView.layoutParams
                animLp.width  = (startWidth  + (targetWidth  - startWidth)  * f).toInt()
                animLp.height = (startHeight + (targetHeight - startHeight) * f).toInt()
                exoPlyView.layoutParams = animLp
            }
            start()
        }
    }
    private fun animateCloseViewer() {
        if (!::exoPlyView.isInitialized) {
            // (activity as? MainActivity)?.onBackPressedDispatcher?.onBackPressed()
            (activity as? MainActivity)?.openViewer(imgFrg)
            return
        }
        exoPlyView.animate()
            .translationY(exoPlyView.height.toFloat())
            .setDuration(150)
            .withEndAction {
                // (activity as? MainActivity)?.onBackPressedDispatcher?.onBackPressed()
                (activity as? MainActivity)?.openViewer(imgFrg)
            }
            .start()
    }
    // ===== SHUFFLE - PLAYLIST =====
    private fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
        vidController.setShuffleActive(isShuffleEnabled)
        val msg = if (isShuffleEnabled) "Shuffle ON" else "Shuffle OFF"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
    private fun shuffleToRandomVideo() {
        // Exclude current video if possible
        val videos = mediaItems.filter { it.isVideo() }
        val candidates = if (videos.size == 1) videos else videos.filter { it.uri != mediaItems.getOrNull(currentIndex)?.uri }
        if (candidates.isEmpty()) return
        val randomVideo = candidates.random()
        currentIndex = mediaItems.indexOf(randomVideo)
        playVideo(randomVideo)
    }
    private fun showPlaylistWindow() {
        if (floatWin?.isShowing() == true) {
            floatWin?.dismiss()
            return
        }
        floatWin?.showTyped(FloatWin.TYPE_PLAYLIST, R.layout.floating_playlist) { content ->
            setupPlaylistLogic(content)
        }
    }
    private fun setupPlaylistLogic(container: View) {
        val recyclerView: RecyclerView = container.findViewById(R.id.playlistRecyclerView)
        val countText:    TextView     = container.findViewById(R.id.playlistCount)

        if (mediaItems.isEmpty()) {
            countText.text = "0 videos"
            return
        }
        val videos = mediaItems.filter { it.isVideo() }
        countText.text = "${videos.size} video${if (videos.size != 1) "s" else ""}"

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter        = PlaylistAdapter(videos, currentIndex)
        recyclerView.scrollToPosition(currentIndex.coerceAtLeast(0))
    }
    private inner class PlaylistAdapter(
        private val videos:              List<MediaItems>,
        private val currentPlayingIndex: Int
    ) : RecyclerView.Adapter<PlaylistAdapter.VideoViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_playlist_video, parent, false)
            return VideoViewHolder(view)
        }
        override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
            holder.bind(videos[position], position == currentPlayingIndex)
        }
        override fun getItemCount(): Int = videos.size

        inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val thumbnail:        ImageView = itemView.findViewById(R.id.videoThumbnail)
            private val name:             TextView  = itemView.findViewById(R.id.videoName)
            private val size:             TextView  = itemView.findViewById(R.id.videoSize)
            private val playingIndicator: ImageView = itemView.findViewById(R.id.playingIndicator)
            init {
                itemView.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        val actualIndex = findActualIndex(videos[pos])
                        if (actualIndex >= 0) {
                            currentIndex = actualIndex
                            playVideo(videos[pos])
                            floatWin?.dismiss()
                        }
                    }
                }
            }
            fun bind(video: MediaItems, isPlaying: Boolean) {
                name.text = video.name
                size.text = formatSize(video.size)
                playingIndicator.visibility = if (isPlaying) View.VISIBLE else View.GONE

                thumbnail.load(video.uri) {
                    crossfade(true)
                    size(160, 90)
                    placeholder(R.drawable.ic_broken_image)
                    error(R.drawable.ic_broken_image)
                }
            }
        }
        private fun findActualIndex(video: MediaItems): Int =
            mediaItems.indexOfFirst { it.uri == video.uri }
    }
    // ===== HELPERS =====
    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    private fun getPlayerDuration(): Long {
        val duration = player?.duration ?: -1L
        return if (duration <= 0) -1L else duration
    }
}