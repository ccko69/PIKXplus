package com.ccko.pikxplus.ux.settings
object PrefKeys {
  // ===== Image =====
  const val IMG_MAX_BRIGHTNESS = "img_max_brightness"
  // const val IMG_DETAILED_INFO = "img_detailed_info"
  const val IMG_COLOR_ADJUST = "img_color_adjust"
  const val IMG_HOTSPOT = "img_hotspot"
  const val IMG_HEADER = "img_header"
  const val IMG_PARALLAX = "img_parallax"
  const val IMG_THUMB_QUALITY = "img_thumb_quality"
  const val IMG_LOOP = "img_loop"
  const val IMG_AUTO_ROTATION = "img_auto_rotation"
  const val IMG_MIRROR = "img_auto_mirror"
  const val IMG_ANIMATION = "img_animation"
  const val IMG_AUTO_ROTATION_DIRECTION = "img_auto_rotation_direction"
  const val IMG_AUTO_ROTATION_MODE = "img_auto_rotation_mode"
  // ===== Album =====
  const val ALBUM_BOOKMARKED = "album_bookmarked"   // Set<String> of album IDs
  const val ALBUM_HIDDEN = "album_hidden"        // Set<String> of album IDs
  const val ALBUM_VIEW_MODE = "album_view_mode"     // "list" or "grid"
  // ===== Video =====
  const val VID_MAX_BRIGHTNESS = "vid_max_brightness"
  const val VID_MUTE = "vid_mute"
  const val VID_LOOP = "vid_loop"
  const val VID_LANDSCAPE_MODE = "vid_landscape_mode"     // "portrait" | "landscape" | "auto"
  const val VID_SMART_PREVIOUS = "vid_smart_previous"
  const val VID_REMEMBER = "vid_remember"
  const val VID_SMART_RESUME = "vid_smart_resume"
  const val VID_SMOOTH_SWITCH = "vid_smooth_switch"
  const val VID_SYNC_BAR = "vid_sync_bar"
  const val VID_VOL_BOOST = "vid_vol_boost"
  const val VID_PAUSE_DC = "vid_pause_dc"
  const val VID_AUTOHIDE = "vid_autohide"   // int
  const val VID_SWIPE_BRIGHTNESS = "vid_swipe_brightness"  // float, swipe override
  // ===== General =====
  const val GEN_KEEP_SCREEN = "gen_keep_screen"
  const val GEN_ALBUM_START = "gen_album_start"
  const val GEN_NAVBAR = "gen_navbar"
  const val GEN_OVERRIDE_ORI = "gen_override_ori"
  const val GEN_RECYCLE = "gen_recycle"
  const val GEN_NOMEDIA = "gen_nomedia"
  const val GEN_DOT = "gen_dot"
  // Actions / text – no value persistence needed
  const val GEN_SCAN = "gen_scan"
  const val GEN_CACHE = "gen_cache"
  const val GEN_ABOUT = "gen_about"
  const val GEN_BUILD_VER = "gen_build_ver"
}
