package com.ccko.pikxplus.viewers.img

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ccko.pikxplus.MainActivity
import com.ccko.pikxplus.shared.data.MediaItems
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for ImgFrg. Manages the media list, current index, and view state.
 *
 * FIX: Removed viewModelScope.launch wrappers from saveState() and clearState().
 *      SharedPreferences.edit().apply() is already async — wrapping it in a coroutine
 *      adds overhead with no benefit and is safe to call directly on the main thread.
 * FIX: Fixed malformed KDoc on navigateToIndex() (triple-backtick code fence is not
 *      valid inside a KDoc block and renders as a broken code block in IDE).
 */
class ImgVM(application: Application) : AndroidViewModel(application) {
    // ===== STATE FLOWS =====
    private val _mediaList = MutableStateFlow<List<MediaItems>>(emptyList())
    val mediaList: StateFlow<List<MediaItems>> = _mediaList.asStateFlow()
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    private val _isVideoMode = MutableStateFlow(false)
    val isVideoMode: StateFlow<Boolean> = _isVideoMode.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // val savedMatrixStates = SparseArray<Pair<Matrix, Float>>()
    // ===== PREFERENCES KEYS =====
    companion object {
        private const val PREF_KEY_LAST_URI   = "viewer_last_image"
        private const val PREF_KEY_LAST_INDEX = "viewer_last_index"
    }
    // ===== INITIALIZATION =====
    /** Set media list and jump to the given initial index. */
    fun setMediaList(media: List<MediaItems>, initialIndex: Int = 0) {
        _mediaList.value = media
        _currentIndex.value = initialIndex.coerceIn(0, (media.size - 1).coerceAtLeast(0))
    }
    /** Navigate to a specific index. No-op if out of range. */
    fun navigateToIndex(index: Int) {
        if (index in 0 until _mediaList.value.size) {
            _currentIndex.value = index
            saveState()
        }
    }
    fun navigateNext() {
        if (_currentIndex.value < _mediaList.value.size - 1) {
            _currentIndex.value++
            saveState()
        }
    }
    fun navigatePrevious() {
        if (_currentIndex.value > 0) {
            _currentIndex.value--
            saveState()
        }
    }
    fun setVideoMode(isVideo: Boolean) {
        _isVideoMode.value = isVideo
    }
    fun getCurrentMedia(): MediaItems? {
        val index = _currentIndex.value
        return _mediaList.value.getOrNull(index)
    }
    // ===== DELETION =====
    /**
     * Removes the item at the current index from the list.
     * Adjusts the index so the viewer stays on a valid item.
     * Returns true if deletion succeeded.
     */
    fun deleteCurrentItem(): Boolean {
        val index = _currentIndex.value
        if (index !in _mediaList.value.indices) return false

        val newList = _mediaList.value.toMutableList()
        newList.removeAt(index)
        _mediaList.value = newList
        _currentIndex.value = when {
            newList.isEmpty()    -> 0
            index >= newList.size -> newList.size - 1
            else                  -> index
        }
        return true
    }
    // ===== NAVIGATION HELPERS =====
    fun hasNext(): Boolean     = _currentIndex.value < _mediaList.value.size - 1
    fun hasPrevious(): Boolean = _currentIndex.value > 0

    // ===== PERSISTENCE =====
    fun saveState() {
        val currentMedia = getCurrentMedia() ?: return
        try {
            MainActivity.prefs
                .edit()
                .putString(PREF_KEY_LAST_URI, currentMedia.uri.toString())
                .putInt(PREF_KEY_LAST_INDEX, _currentIndex.value)
                .apply()
        } catch (e: Exception) { /* ignore */ }
    }
    fun restoreState(): Pair<String?, Int> {
        val prefs = MainActivity.prefs
        val savedUri   = prefs.getString(PREF_KEY_LAST_URI, null)
        val savedIndex = prefs.getInt(PREF_KEY_LAST_INDEX, -1)
        return savedUri to savedIndex
    }
    fun clearState() {
        try {
            MainActivity.prefs
                .edit()
                .remove(PREF_KEY_LAST_URI)
                .remove(PREF_KEY_LAST_INDEX)
                .apply()
        } catch (e: Exception) { /* ignore */ }
    }
    // ===== PRELOADING =====
    /**
     * Placeholder for future explicit prefetch logic.
     * Currently Coil handles preloading via offscreenPageLimit in the ViewPager.
     */
    fun preloadNearbyImages() {
        // No-op: Coil + offscreenPageLimit = 2 handles preloading automatically.
    }
}
