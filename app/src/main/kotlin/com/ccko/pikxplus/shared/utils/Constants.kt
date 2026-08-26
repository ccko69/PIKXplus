package com.ccko.pikxplus.shared.utils

/** App-wide constants */
object Constants {

  // ===== SHARED PREFERENCES =====
  const val PREFS_NAME = "com.ccko.pikxplus_preferences"
  // const val PREFS_NAME = "com.ccko.pikxplus"
  // Album prefs
  const val PREF_LAST_ALBUM_ID = "last_album_id"
  const val PREF_LAST_ALBUM_NAME = "last_album_name"
  const val PREF_LAST_ALBUM_RELATIVE_PATH = "last_album_relative_path"

  // Photos prefs
  const val PREF_KEY_SORT_MODE = "photos_sort_mode"
  const val PREF_KEY_VIEW_MODE = "photos_grid_span"
  const val PREF_KEY_LAST_URI = "viewer_last_image"
  const val PREF_KEY_LAST_INDEX = "viewer_last_index"

  // ===== PERMISSION =====
  const val STORAGE_PERMISSION_CODE = 1001

  // ===== VIEWER =====
  const val VIEWER_ANIMATION_DURATION = 300L
  const val GESTURE_DEADZONE_TOP = 0.10f
  const val GESTURE_DEADZONE_BOTTOM = 0.10f

  // ===== PAGING / BATCH LOADING =====
  const val BATCH_SIZE = 200 // Items per batch for PhotosFrg
  const val PREFETCH_DISTANCE = 40 // Load more when 40 items remaining

  // ===== GRID =====
  const val GRID_SPAN_MIN = 2
  const val GRID_SPAN_MAX = 6
  const val GRID_SPAN_DEFAULT = 6
}
