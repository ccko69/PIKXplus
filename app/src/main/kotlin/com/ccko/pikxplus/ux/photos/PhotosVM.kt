package com.ccko.pikxplus.ux.photos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ccko.pikxplus.shared.data.MediaItems
import com.ccko.pikxplus.shared.data.MSRepo
import com.ccko.pikxplus.ux.search.SearchFrg
import java.math.BigInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for PhotosFrg.
 * Handles batch loading, sorting, and filtering.
 */
class PhotosVM(application: Application) : AndroidViewModel(application) {
    private val repository = MSRepo(application)
    // ===== STATE FLOWS =====
    private val _mediaList = MutableStateFlow<List<MediaItems>>(emptyList())
    val mediaList: StateFlow<List<MediaItems>> = _mediaList.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()
    // ===== INTERNAL STATE =====
    // All sort/filter operations produce a new derived list from _rawList.
    private val _rawList = mutableListOf<MediaItems>()

    private var albumId: String? = null
    private var albumName: String? = null
    private var folderName: String? = null

    private var currentSortMode = SortMode.DATE_DESC
    private var currentFilter: SearchFrg.Filter? = null
    // Batch loading state
    private var loadedCount = 0
    private val batchSize = 0 // 0 means load all.

    // ===== PUBLIC API =====
    /** Called by PhotosFragment when an album is selected. */
    fun setAlbumData(albumId: String?, albumName: String?, folderName: String?) {
        this.albumId = albumId
        this.albumName = albumName
        this.folderName = folderName
        loadMedia(reset = true)
    }
    /** Load more items when user scrolls near the end. */
    fun loadMore() {
        if (!_isLoading.value && !_isLoadingMore.value && _hasMore.value) {
            loadMedia(reset = false)
        }
    }
    /** Reload from the beginning (e.g. after deletion). */
    fun refresh() {
        loadMedia(reset = true)
    }
    
    fun markItemAnimated(itemId: String, dateModified: Long) {
        // Find in _rawList and update type
        val idx = _rawList.indexOfFirst { it.id == itemId }
        if (idx >= 0) {
            val old = _rawList[idx]
            if (old.type != MediaItems.MediaType.ANIMATED) {
                _rawList[idx] = old.copy(type = MediaItems.MediaType.ANIMATED)
                emitDerived()
            }
        }
    }
    fun markItemsAnimated(ids: Set<String>) {
        var anyChanged = false
        for (id in ids) {
            val idx = _rawList.indexOfFirst { it.id == id }
            if (idx >= 0 && _rawList[idx].type != MediaItems.MediaType.ANIMATED) {
                _rawList[idx] = _rawList[idx].copy(type = MediaItems.MediaType.ANIMATED)
                anyChanged = true
            }
        }
        if (anyChanged) emitDerived() // ← only ONE emission regardless of N
    }
    /** Change sort mode and re-derive the list from _rawList. */
    fun setSortMode(sortMode: SortMode) {
        if (currentSortMode == sortMode) return
        currentSortMode = sortMode
        emitDerived()
    }
    /** Apply or clear a filter. Re-derives from _rawList — no data loss. */
    fun setFilter(filter: SearchFrg.Filter?) {
        currentFilter = filter
        emitDerived()
    }
    // ===== GETTERS =====
    fun getCurrentAlbumName(): String? = albumName
    fun getCurrentMediaList(): List<MediaItems> = _mediaList.value
    fun getCurrentSortMode(): SortMode = currentSortMode
    // ===== LOADING =====
    private fun loadMedia(reset: Boolean = false) {
        if (reset) {
            loadedCount = 0
            _rawList.clear()
            _mediaList.value = emptyList()
        }
        viewModelScope.launch {
            if (reset) {
                _isLoading.value = true
                _totalCount.value = repository.loadMediaCount(albumId, albumName, folderName)
            } else {
                _isLoadingMore.value = true
            }

            try {
                val newItems = repository.loadMediaForAlbum(
                    albumId = albumId,
                    albumName = albumName,
                    folderName = folderName,
                    limit = batchSize,
                    offset = loadedCount
                )

                if (newItems.isEmpty()) {
                    _hasMore.value = false
                } else {
                    // Accumulate into raw list first
                    _rawList.addAll(newItems)
                    loadedCount += newItems.size
                    _hasMore.value = loadedCount < _totalCount.value
                    // Then sort + filter the ENTIRE accumulated list
                    emitDerived()
                }
            } catch (e: Exception) {
                // TODO: emit error state
            } finally {
                _isLoading.value = false
                _isLoadingMore.value = false
            }
        }
    }
    /**
     * Derives and emits the final list from _rawList by applying
     * the current filter and sort. Single source of truth.
     */
    private fun emitDerived() {
        val filtered = applyFilter(_rawList)
        val sorted = filtered.toMutableList().also { sortMediaList(it) }
        _mediaList.value = sorted
    }
    // ===== SORT =====
    private fun sortMediaList(list: MutableList<MediaItems>) {
        when (currentSortMode) {
            SortMode.NAME_ASC  -> list.sortWith(NATURAL_NAME_COMPARATOR)
            SortMode.NAME_DESC -> list.sortWith(NATURAL_NAME_COMPARATOR.reversed())
            SortMode.DATE_DESC -> list.sortByDescending { it.dateModified }
            SortMode.DATE_ASC  -> list.sortBy { it.dateModified }
            SortMode.SIZE_DESC -> list.sortByDescending { it.size }
            SortMode.SIZE_ASC  -> list.sortBy { it.size }
        }
    }
    // ===== FILTER =====
    private fun applyFilter(list: List<MediaItems>): List<MediaItems> {
        val filter = currentFilter ?: return list
        return list.filter { item ->
            when (filter.type) {
                SearchFrg.Filter.Type.ALL      ->  true
                SearchFrg.Filter.Type.PHOTOS   -> !item.isVideo() && !item.isAnimated()
                SearchFrg.Filter.Type.VIDEOS   ->  item.isVideo()
                SearchFrg.Filter.Type.ANIMATED ->  item.isAnimated()
            }
        }
    }
    // ===== COMPARATORS =====
    enum class SortMode {
        NAME_ASC, NAME_DESC,
        DATE_DESC, DATE_ASC,
        SIZE_DESC, SIZE_ASC
    }
    companion object {
        /** Natural sort: "IMG_1, IMG_2, IMG_10" instead of "IMG_1, IMG_10, IMG_2". */
        val NATURAL_NAME_COMPARATOR = Comparator<MediaItems> { a, b ->
            val s1 = a.name ?: return@Comparator -1
            val s2 = b.name ?: return@Comparator 1

            var i = 0; var j = 0
            val len1 = s1.length; val len2 = s2.length

            while (i < len1 && j < len2) {
                val c1 = s1[i]; val c2 = s2[j]

                if (c1.isDigit() && c2.isDigit()) {
                    // Skip leading zeros
                    while (i < len1 && s1[i] == '0') i++
                    while (j < len2 && s2[j] == '0') j++

                    val numStart1 = i; val numStart2 = j
                    while (i < len1 && s1[i].isDigit()) i++
                    while (j < len2 && s2[j].isDigit()) j++

                    val numStr1 = s1.substring(numStart1, i)
                    val numStr2 = s2.substring(numStart2, j)

                    val num1 = numStr1.ifEmpty { "0" }.toBigIntegerOrNull() ?: BigInteger.ZERO
                    val num2 = numStr2.ifEmpty { "0" }.toBigIntegerOrNull() ?: BigInteger.ZERO

                    val cmp = num1.compareTo(num2)
                    if (cmp != 0) return@Comparator cmp

                    // Equal numeric value: shorter string first (fewer leading zeros)
                    if (numStr1.length != numStr2.length) {
                        return@Comparator numStr2.length.compareTo(numStr1.length)
                    }
                } else {
                    val cmp = c1.lowercaseChar().compareTo(c2.lowercaseChar())
                    if (cmp != 0) return@Comparator cmp
                    i++; j++
                }
            }
            len1.compareTo(len2)
        }
    }
}
