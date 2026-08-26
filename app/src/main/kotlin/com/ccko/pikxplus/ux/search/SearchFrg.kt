package com.ccko.pikxplus.ux.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.ccko.pikxplus.MainActivity
import com.ccko.pikxplus.R
import com.ccko.pikxplus.shared.MainFrgAdpt
import com.ccko.pikxplus.shared.SharedVM
import com.google.android.material.chip.ChipGroup
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Search Fragment — filter control hub for PhotosFrg.
 *
 * When a filter chip or search query is applied:
 *   1. Filter is emitted to SharedVM (observed by PhotosFragment).
 *   2. App navigates to the Photos tab so results are visible immediately.
 *
 * NOTE on the `query` field in Filter:
 *   The chip auto-fills the search box with extension strings like "jpg jpeg png webp".
 *   However, PhotosVM.applyFilter() currently only checks filter.type and ignores
 *   filter.query entirely. The query field is wired here and passed through, but won't
 *   do anything until applyFilter() in PhotosVM is updated to filter by filename.
 *   For now the chip-based type filter works correctly; text search is a future addition.
 */
class SearchFrg : Fragment() {

    companion object {
        private const val TAG = "SearchFragment"
        private const val PREF_KEY_SEARCH_FILTER = "search_filter_type"
        private const val PREF_KEY_SEARCH_QUERY  = "search_query"
        private const val DEBOUNCE_MS = 300L
    }
    // ===== VIEWS =====
    private lateinit var filterChipGroup: ChipGroup
    private lateinit var chipImages: Chip
    private lateinit var chipVideos: Chip
    private lateinit var chipGifs: Chip
    private lateinit var searchInput: EditText
    private lateinit var btnReset: Button
    // ===== VIEW MODEL =====
    private lateinit var sharedViewModel: SharedVM
    // ===== STATE =====
    private var currentFilterType: FilterType? = null
    private var debounceJob: Job? = null
    // Internal enum for UI chip state
    private enum class FilterType { IMAGES, VIDEOS, GIFS }
    /**
     * Filter data class passed to SharedVM and observed by PhotosFragment.
     */
    data class Filter(
        val type: Type,
        val query: String = ""
    ) {
        enum class Type { ALL, PHOTOS, VIDEOS, ANIMATED }
    }
    // ===== LIFECYCLE =====
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.hide()
        restoreFilterState()
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_search, container, false)
        
        filterChipGroup = view.findViewById(R.id.filterChipGroup)
        chipImages      = view.findViewById(R.id.chipImages)
        chipVideos      = view.findViewById(R.id.chipVideos)
        chipGifs        = view.findViewById(R.id.chipGifs)
        searchInput     = view.findViewById(R.id.searchInput)
        btnReset        = view.findViewById(R.id.btnReset)
        return view
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedViewModel = ViewModelProvider(requireActivity())[SharedVM::class.java]

        setupChipGroup()
        setupSearchInput()
        setupResetButton()
        restoreUiState()
        // Re-emit filter if there's a saved state so PhotosFragment stays in sync
        if (currentFilterType != null || searchInput.text.isNotBlank()) {
            emitFilterImmediate()
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        debounceJob?.cancel()
    }
    // ===== SETUP =====
    private fun setupChipGroup() {
        filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilterType = if (checkedIds.isEmpty()) {
                null
            } else {
                when (checkedIds.first()) {
                    R.id.chipImages -> FilterType.IMAGES
                    R.id.chipVideos -> FilterType.VIDEOS
                    R.id.chipGifs   -> FilterType.GIFS
                    else            -> null
                }
            }
            // Auto-fill search box to reflect chip selection (for display/context only;
            // PhotosVM currently filters by type, not by filename extension)
            when (currentFilterType) {
                FilterType.IMAGES -> {
                  searchInput.setText("jpg jpeg png webp")
                  searchInput.requestFocus()
                }
                FilterType.VIDEOS -> {
                  searchInput.setText("mp4 avi mkv")
                  searchInput.requestFocus()
                }
                FilterType.GIFS   -> {
                  searchInput.setText("gif webp")
                  searchInput.requestFocus()
                }
                null              -> { /* leave searchInput as-is */ }
            }
            searchInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    emitFilterDebounced()
                    true
                } else false
            }
            // emitFilterDebounced()
        }
    }
    private fun setupSearchInput() {
        searchInput.doAfterTextChanged {
            emitFilterDebounced()
        }
    }
    private fun setupResetButton() {
        btnReset.setOnClickListener {
            filterChipGroup.clearCheck()
            currentFilterType = null
            searchInput.text?.clear()
            emitFilterImmediate()
            clearSavedFilterState()
        }
    }
    // ===== FILTER EMISSION =====
    private fun emitFilterDebounced() {
        debounceJob?.cancel()
        debounceJob = lifecycleScope.launch {
            delay(DEBOUNCE_MS)
            emitFilterImmediate()
        }
    }
    private fun emitFilterImmediate() {
        val query = searchInput.text?.toString()?.trim() ?: ""
        val filter = buildFilter(currentFilterType, query)

        sharedViewModel.setFilter(filter)
        saveFilterState(currentFilterType, query)
        if (filter != null) {
            navigateToPhotosFragment()
        }
    }
    private fun buildFilter(type: FilterType?, query: String): Filter? {
        if (type == null && query.isBlank()) return null

        val filterType = when (type) {
            FilterType.IMAGES -> Filter.Type.PHOTOS
            FilterType.VIDEOS -> Filter.Type.VIDEOS
            FilterType.GIFS   -> Filter.Type.ANIMATED
            null              -> Filter.Type.ALL
        }
        return Filter(type = filterType, query = query)
    }
    private fun navigateToPhotosFragment() {
        (activity as? MainActivity)?.navigateToTab(MainFrgAdpt.POSITION_PHOTOS)
    }
    // ===== PREFERENCES =====
    private fun restoreFilterState() {
        try {
            MainActivity.prefs.let { prefs ->
                val savedType = prefs.getString(PREF_KEY_SEARCH_FILTER, null)
                if (!savedType.isNullOrBlank()) {
                    currentFilterType = try {
                        FilterType.valueOf(savedType)
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
            }
        } catch (e: Exception) { /* ignore */ }
    }
    private fun restoreUiState() {
        // Restore chip selection
        when (currentFilterType) {
            FilterType.IMAGES -> chipImages.isChecked = true
            FilterType.VIDEOS -> chipVideos.isChecked = true
            FilterType.GIFS   -> chipGifs.isChecked = true
            null              -> filterChipGroup.clearCheck()
        }
        // Restore search query only if the chip hasn't already filled it
        if (searchInput.text.isNullOrBlank()) {
            val saved = MainActivity.prefs.getString(PREF_KEY_SEARCH_QUERY, "")
            if (!saved.isNullOrBlank()) {
                searchInput.setText(saved)
            }
        }
    }
    private fun saveFilterState(type: FilterType?, query: String) {
        try {
            MainActivity.prefs.edit()
                .putString(PREF_KEY_SEARCH_FILTER, type?.name)
                .putString(PREF_KEY_SEARCH_QUERY, query)
                .apply()
        } catch (e: Exception) { /* ignore */ }
    }
    private fun clearSavedFilterState() {
        try {
            MainActivity.prefs.edit()
                .remove(PREF_KEY_SEARCH_FILTER)
                .remove(PREF_KEY_SEARCH_QUERY)
                .apply()
        } catch (e: Exception) { /* ignore */ }
    }
}
