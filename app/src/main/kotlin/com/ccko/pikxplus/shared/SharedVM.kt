package com.ccko.pikxplus.shared

import androidx.lifecycle.ViewModel
import com.ccko.pikxplus.shared.data.AlbumInfo
import com.ccko.pikxplus.ux.search.SearchFrg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared ViewModel for cross-fragment communication.
 *
 * Channels:
 *   - SearchFrg  → PhotosFrg    : filter state
 *   - ImgFrg          → PhotosFrg    : current swipe index + URI (real-time highlight)
 *   - ImgFrg          → PhotosFrg    : deleted item notification
 *   - AlbumsFrg       → PhotosFrg    : current album
 *   - ImgFrg          → MainActivity : viewer active state
 */
 
class SharedVM : ViewModel() {
    // ===== SEARCH FILTER =====
    // Set by SearchFrg, observed by PhotosFrg.
    private val _filter = MutableStateFlow<SearchFrg.Filter?>(null)
    val filter: StateFlow<SearchFrg.Filter?> = _filter.asStateFlow()
    
    fun setFilter(filter: SearchFrg.Filter?) {
        _filter.value = filter
    }
    // ===== CURRENT VIEWING POSITION =====
    // Updated by ImgFrg on every swipe (call from onPageSelected).
    // Observed by PhotosFrg to highlight the open item in the grid.
    // -1 means no item is currently open in the viewer.
    private val _currentViewingIndex = MutableStateFlow(-1)
    val currentViewingIndex: StateFlow<Int> = _currentViewingIndex.asStateFlow()
    
    fun setCurrentViewingIndex(index: Int) { _currentViewingIndex.value = index }
    fun clearCurrentViewingIndex() { _currentViewingIndex.value = -1 }
    // URI is kept as a secondary fallback: if the list reloads/reorders,
    // the URI lets PhotosFrg find the right item even if the index shifted.
    private val _currentViewingUri = MutableStateFlow<String?>(null)
    val currentViewingUri: StateFlow<String?> = _currentViewingUri.asStateFlow()
    /**
     * Called by ImgFrg in onPageSelected every time the user swipes to a new item.
     */
    fun updateViewingPosition(index: Int, uri: String? = null) {
        _currentViewingIndex.value = index
        _currentViewingUri.value = uri
    }
    /**
     * Called by ImgFrg in onDestroyView when the viewer closes.
     */
    fun clearViewingPosition() {
        _currentViewingIndex.value = -1
        _currentViewingUri.value = null
    }
    // ===== DELETION STATE =====
    // Notified by ImgFrg when an item is deleted.
    // Observed by PhotosFrg to trigger a list refresh.
    private val _deletedPosition = MutableStateFlow<Int?>(null)
    val deletedPosition: StateFlow<Int?> = _deletedPosition.asStateFlow()

    fun notifyItemDeleted(position: Int) {
        _deletedPosition.value = position
    }

    fun clearDeletedPosition() {
        _deletedPosition.value = null
    }
    // ===== CURRENT ALBUM =====
    // Optionally set when an album is selected, for cross-fragment awareness.
    private val _currentAlbum = MutableStateFlow<AlbumInfo?>(null)
    val currentAlbum: StateFlow<AlbumInfo?> = _currentAlbum.asStateFlow()

    fun setCurrentAlbum(album: AlbumInfo?) {
        _currentAlbum.value = album
    }
    // ===== VIEWER ACTIVE STATE =====
    // Set by MainActivity when ImgFrg opens/closes.
    private val _isViewerActive = MutableStateFlow(false)
    val isViewerActive: StateFlow<Boolean> = _isViewerActive.asStateFlow()

    fun setViewerActive(active: Boolean) {
        _isViewerActive.value = active
    }
}
