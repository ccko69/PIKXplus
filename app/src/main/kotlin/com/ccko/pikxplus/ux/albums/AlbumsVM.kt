package com.ccko.pikxplus.ux.albums

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ccko.pikxplus.shared.data.AlbumInfo
import com.ccko.pikxplus.shared.data.MSRepo
import com.ccko.pikxplus.shared.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for AlbumsFragment. Handles album loading logic using Coroutines and Repository.
 */
 
class AlbumsVM(application: Application) : AndroidViewModel(application) {
  private val repository = MSRepo(application)
  private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

  private val _albums = MutableStateFlow<List<AlbumInfo>>(emptyList())
  val albums: StateFlow<List<AlbumInfo>> = _albums.asStateFlow()
  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()
  private val _isRefreshingPhotos = MutableStateFlow(false)
  val isRefreshingPhotos: StateFlow<Boolean> = _isRefreshingPhotos.asStateFlow()
  /** Sectioned list: bookmarks first, then regular albums. Hidden albums excluded by default. */
  val sectionedAlbums: StateFlow<List<AlbumsAdpt.Item>> = MutableStateFlow(emptyList())
  /**
   * When true, the sectioned list keeps hidden albums in the output so they can
   * be un-hidden from the UI. Triggered while the fragment is in selection mode.
   */
  var updateIncludeHidden: Boolean = false
  init {
    // Rebuild sectioned list whenever raw albums change or includeHidden flips.
    viewModelScope.launch {
      _albums.collect { all ->
        val bookmarkedIds = getBookmarkedIds()
        val hiddenIds = getHiddenIds()
        // Mutually-exclusive buckets so an album never renders twice. During
        // selection mode a hidden album must appear under "Hidden" only — not
        // also under its old bookmark/regular bucket. A bookmarked + hidden
        // album collapses into "Hidden" (its bookmark still shows via the red
        // icon since isBookmarked is set from prefs in loadAlbums()).
        val hidden = if (updateIncludeHidden) all.filter { it.id in hiddenIds } else emptyList()
        val bookmarked = all.filter { it.id in bookmarkedIds && it.id !in hiddenIds }
        val regular = all.filter { it.id !in bookmarkedIds && it.id !in hiddenIds }
        val items = buildList {
          if (bookmarked.isNotEmpty()) {
            add(AlbumsAdpt.Item.Header("Bookmarks"))
            bookmarked.forEach { add(AlbumsAdpt.Item.Album(it)) }
          }
          if (regular.isNotEmpty()) {
            add(AlbumsAdpt.Item.Header("Albums"))
            regular.forEach { add(AlbumsAdpt.Item.Album(it)) }
          }
          if (hidden.isNotEmpty()) {
            add(AlbumsAdpt.Item.Header("Hidden"))
            hidden.forEach { add(AlbumsAdpt.Item.Album(it)) }
          }
        }
        (sectionedAlbums as MutableStateFlow).value = items
      }
    }
  }
  fun setIncludeHidden(value: Boolean) {
    if (updateIncludeHidden == value) return
    updateIncludeHidden = value
    // Push the current albums list through the pipeline again so the collector
    // rebuilds the sectioned output with the new includeHidden value.
    _albums.value = _albums.value.toList()
  }
  fun loadAlbums() {
    viewModelScope.launch {
      _isLoading.value = true
      _error.value = null
      try {
        val loadedAlbums = repository.loadAlbums().map {
          it.copy(
                  isBookmarked = it.id in getBookmarkedIds(),
                  isHidden = it.id in getHiddenIds()
          )
        }
        _albums.value = loadedAlbums
      } catch (e: Exception) {
        _albums.value = emptyList()
        _error.value = "Failed to load albums"
      } finally {
        _isLoading.value = false
      }
    }
  }
  /** Refreshes both Albums and notifies Photos to reload. */
  fun refreshAll() {
    loadAlbums()
    _isRefreshingPhotos.value = true
    _isRefreshingPhotos.value = false
  }
  fun toggleBookmark(albumId: String) {
    val current = getBookmarkedIds().toMutableSet()
    if (!current.add(albumId)) current.remove(albumId)
    prefs.edit().putStringSet("album_bookmarked", current).apply()
    loadAlbums()
  }
  fun toggleHidden(albumId: String) {
    val current = getHiddenIds().toMutableSet()
    if (!current.add(albumId)) current.remove(albumId)
    prefs.edit().putStringSet("album_hidden", current).apply()
    loadAlbums()
  }
  /**
   * Apply a batch bookmark change in a single prefs write, then reload once.
   * Used by the multi-select toolbar in AlbumsFrg.
   */
  fun setBookmarked(albumIds: Set<String>, bookmarked: Boolean) {
    if (albumIds.isEmpty()) return
    val current = getBookmarkedIds().toMutableSet()
    if (bookmarked) current.addAll(albumIds) else current.removeAll(albumIds)
    prefs.edit().putStringSet("album_bookmarked", current).apply()
    loadAlbums()
  }
  /**
   * Apply a batch hidden change in a single prefs write, then reload once.
   */
  fun setHidden(albumIds: Set<String>, hidden: Boolean) {
    if (albumIds.isEmpty()) return
    val current = getHiddenIds().toMutableSet()
    if (hidden) current.addAll(albumIds) else current.removeAll(albumIds)
    prefs.edit().putStringSet("album_hidden", current).apply()
    loadAlbums()
  }
  private fun getBookmarkedIds(): Set<String> =
          prefs.getStringSet("album_bookmarked", emptySet()) ?: emptySet()
  private fun getHiddenIds(): Set<String> =
          prefs.getStringSet("album_hidden", emptySet()) ?: emptySet()
}
