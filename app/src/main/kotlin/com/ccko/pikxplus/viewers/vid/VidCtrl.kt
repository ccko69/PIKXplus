package com.ccko.pikxplus.viewers.vid

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import com.ccko.pikxplus.R

class VidCtrl @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    // ===== VIEWS =====
    private val topBar:    View
    private val bottomBar: View
    // Top bar
    private val backButton:        ImageButton
    private val videoTitle:        TextView
    private val orientationButton: ImageButton
    private val playlistButton:    ImageButton
    private val loopButton:        ImageButton
    private val shuffleButton:     ImageButton
    private val speedButton:       ImageButton
    private val lockButton:        ImageButton
    private val moreButton:        ImageButton
    // Bottom bar
    private val muteButton:     ImageButton
    private val previousButton: ImageButton
    private val playPauseButton:ImageButton
    private val nextButton:     ImageButton
    private val ratioButton:    ImageButton
    // Seek bar - timer
    private val seekBar:     SeekBar
    private val currentTime: TextView
    private val totalTime:   TextView
    
    private val floatUnlockButton: ImageButton
    private val floatBar:         View
    private val FcurrentTime: TextView
    private val FtotalTime:   TextView
    // ===== STATE =====
    private var isUiLocked = false
    private var isMuted    = false
    private var isLooping  = false
    // ===== CALLBACKS =====
    private var listener: ControlListener? = null
    
    private val autoHideHandler = Handler(Looper.getMainLooper())
    private var autoHideRunnable: Runnable? = null
    private var autoHideDelaySeconds = 5 // Exposed for settings wiring
    
    public enum class RatioMode { FIT, FILL, CROP }
    private var currentRatioMode = RatioMode.FIT

    interface ControlListener {
        fun onBackPressed()
        fun onPlayPauseToggle()
        fun onSeekTo(positionMs: Long)
        fun onMuteToggle()
        fun onPreviousVideo()
        fun onNextVideo()
        fun onLoopToggle()
        fun onLockToggle()
        fun onOrientationToggle()
        fun onPlaylistOpen()
        fun onShuffleToggle()
        fun onSpeedChange()
        fun onRatioChange()
        fun onMoreOptions()
    }
    // ===== INIT =====
    init {
        LayoutInflater.from(context).inflate(R.layout.video_player_overlay, this, true)

        topBar    = findViewById(R.id.videoTopBar)
        bottomBar = findViewById(R.id.videoBottomBar)

        backButton        = findViewById(R.id.videoBackButton)
        videoTitle        = findViewById(R.id.videoTitle)
        orientationButton = findViewById(R.id.orientationButton)
        playlistButton    = findViewById(R.id.playlistButton)
        loopButton        = findViewById(R.id.loopButton)
        shuffleButton     = findViewById(R.id.shuffleButton)
        speedButton       = findViewById(R.id.speedButton)
        lockButton        = findViewById(R.id.lockButton)
        moreButton        = findViewById(R.id.moreButton)

        muteButton      = findViewById(R.id.muteButton)
        previousButton  = findViewById(R.id.previousButton)
        playPauseButton = findViewById(R.id.playPauseButton)
        nextButton      = findViewById(R.id.nextButton)
        ratioButton     = findViewById(R.id.ratioButton)

        seekBar     = findViewById(R.id.videoSeekBar)
        currentTime = findViewById(R.id.currentTime)
        totalTime   = findViewById(R.id.totalTime)
        
        floatUnlockButton = findViewById(R.id.floatUnlockButton)

        floatBar     = findViewById(R.id.FloatBar)
        FcurrentTime = findViewById(R.id.FloatCurrentTime)
        FtotalTime   = findViewById(R.id.FloatTotalTime)

        setupListeners()
        updateButtonStates()
    }
    // ===== LISTENERS =====
    private fun setupListeners() {
        backButton.setOnClickListener { listener?.onBackPressed() }
        playPauseButton.setOnClickListener { if (!isUiLocked) listener?.onPlayPauseToggle() }
        muteButton.setOnClickListener {
            if (!isUiLocked) { isMuted = !isMuted; listener?.onMuteToggle(); updateButtonStates() }
        }
        previousButton.setOnClickListener { if (!isUiLocked) listener?.onPreviousVideo() }
        nextButton.setOnClickListener { if (!isUiLocked) listener?.onNextVideo() }
        loopButton.setOnClickListener {
            if (!isUiLocked) { isLooping = !isLooping; listener?.onLoopToggle(); updateButtonStates() }
        }
        // --- LOCK LOGIC FIXED ---
        lockButton.setOnClickListener {
            isUiLocked = true
            listener?.onLockToggle()
            updateButtonStates()
            hide() // Hide everything except the floating lock button
        }
        floatUnlockButton.setOnClickListener {
            isUiLocked = false
            listener?.onLockToggle()
            updateButtonStates()
            show() // Show the full UI
        }
        orientationButton.setOnClickListener { if (!isUiLocked) listener?.onOrientationToggle() }
        playlistButton.setOnClickListener { if (!isUiLocked) listener?.onPlaylistOpen() }
        shuffleButton.setOnClickListener { if (!isUiLocked) listener?.onShuffleToggle() }
        speedButton.setOnClickListener { if (!isUiLocked) listener?.onSpeedChange() }
        ratioButton.setOnClickListener { if (!isUiLocked) listener?.onRatioChange() }
        moreButton.setOnClickListener { if (!isUiLocked) listener?.onMoreOptions() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isUiLocked) {
                    currentTime.text = formatTime(progress.toLong())
                    FcurrentTime.text = formatTime(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { cancelAutoHide() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (!isUiLocked) listener?.onSeekTo(seekBar!!.progress.toLong())
                show() // Will restart autohide timer
            }
        })
    }
    private fun updateButtonStates() {
        muteButton.setImageResource(if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on)
        muteButton.isSelected = isMuted
        loopButton.setImageResource(if (isLooping) R.drawable.ic_loop_on else R.drawable.ic_loop_off)
        loopButton.isSelected = isLooping
        lockButton.setImageResource(if (isUiLocked) R.drawable.ic_lock else R.drawable.ic_unlock)
        lockButton.isSelected = isUiLocked
        
        ratioButton.setImageResource(
            when (currentRatioMode) {
                RatioMode.FILL -> R.drawable.ic_ratio_fill
                RatioMode.CROP -> R.drawable.ic_ratio_crop
                else           -> R.drawable.ic_ratio_fit
            }
        )
    }
    // ===== VISIBILITY & AUTO-HIDE =====
    fun isShowing(): Boolean = topBar.visibility == VISIBLE

    fun show() {
        cancelAutoHide()
        if (isUiLocked) {
            // If locked, tapping screen only shows the lock button, not the bars
            topBar.visibility = GONE
            bottomBar.visibility = GONE
            floatUnlockButton.visibility = VISIBLE
            floatBar.visibility = VISIBLE
        } else {
            topBar.visibility = VISIBLE
            bottomBar.visibility = VISIBLE
            floatUnlockButton.visibility = GONE
            floatBar.visibility = GONE
            scheduleAutoHide(autoHideDelaySeconds)
        }
    }
    fun hide() {
        cancelAutoHide()
        topBar.visibility = GONE
        bottomBar.visibility = GONE
        
        if (isUiLocked) {
            floatUnlockButton.visibility = VISIBLE
            floatBar.visibility = VISIBLE
        } else {
            floatUnlockButton.visibility = GONE
            floatBar.visibility = VISIBLE // Keep minimal time visible when unlocked but hidden
        }
    }
    // Exposed in seconds so you can wire it to settings
    fun scheduleAutoHide(delaySeconds: Int) {
        this.autoHideDelaySeconds = delaySeconds
        cancelAutoHide()
        if (isShowing() && !isUiLocked) {
            autoHideRunnable = Runnable { hide() }
            autoHideHandler.postDelayed(autoHideRunnable!!, delaySeconds * 1000L)
        }
    }
    private fun cancelAutoHide() {
        autoHideRunnable?.let { autoHideHandler.removeCallbacks(it) }
        autoHideRunnable = null
    }
    // ===== SEEK PREVIEW =====
    fun showSeekPreview(previewMs: Long, durationMs: Long) {
        if (durationMs > 0) seekBar.max = durationMs.toInt()
        seekBar.progress = previewMs.toInt()
        currentTime.text = formatTime(previewMs)
        FcurrentTime.text = formatTime(previewMs)
        FtotalTime.text = formatTime(durationMs)
        
        // Ensure the floating preview bar is visible during scrubbing
        floatBar.visibility = VISIBLE
        cancelAutoHide()
    }
    fun hideSeekPreview() {
        if (isShowing() && !isUiLocked) {
            floatBar.visibility = GONE
            scheduleAutoHide(autoHideDelaySeconds)
        }
    }
    // ===== PUBLIC API SETTERS =====
    fun setUiLocked(locked: Boolean) {
        isUiLocked = locked
        updateButtonStates()
        if (isUiLocked) hide() else show()
    }
    fun setMuted(muted: Boolean) { isMuted = muted; updateButtonStates() }
    fun setLooping(looping: Boolean) { isLooping = looping; updateButtonStates() }
    fun setSpeedActive(active: Boolean) { speedButton.isSelected = active }
    fun setControlListener(listener: ControlListener?) { this.listener = listener }
    fun setVideoTitle(title: String) { videoTitle.text = title }
    fun setShuffleActive(active: Boolean) { shuffleButton.isSelected = active }
    fun setRatioMode(mode: RatioMode) { currentRatioMode = mode; updateButtonStates() }
    
    fun updatePlayPauseButton(isPlaying: Boolean) {
        playPauseButton.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }
    fun updateProgress(currentMs: Long, durationMs: Long) {
        if (durationMs > 0) {
            seekBar.max      = durationMs.toInt()
            seekBar.progress = currentMs.toInt()
            currentTime.text = formatTime(currentMs)
            FcurrentTime.text = formatTime(currentMs)
            totalTime.text   = formatTime(durationMs)
            FtotalTime.text   = formatTime(durationMs)
        }
    }
    fun enableButton(buttonId: Int, enabled: Boolean) {
        findViewById<View>(buttonId)?.let {
            it.isEnabled = enabled
            it.alpha     = if (enabled) 1.0f else 0.75f
        }
    }
    fun formatTime(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val hours   = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
        else String.format("%d:%02d", minutes, seconds)
    }
}