package com.ccko.pikxplus.ux.settings
import android.content.Context
import android.content.SharedPreferences
import com.ccko.pikxplus.shared.utils.Constants
import com.ccko.pikxplus.ux.settings.PrefKeys
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.callbackFlow
class SetRepo(context: Context) {
  private val prefs: SharedPreferences =
  context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
  init {
    migrateAutoRotationKeys()
  }
  /** Expose the underlying prefs for one-shot seeding reads. */
  val sharedPrefs: SharedPreferences get() = prefs
  // ---------------- Boolean preferences ----------------
  val imgMaxBrightness:       Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.IMG_MAX_BRIGHTNESS, false)
  // val imgDetailedInfo:        Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.IMG_DETAILED_INFO, false)
  val imgHotspotEnabled:      Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.IMG_HOTSPOT, true)
  val imgHeaderEnabled:       Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.IMG_HEADER, true)
  val imgParallaxEnabled:     Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.IMG_PARALLAX, false)
  val imgLoopEnabled:         Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.IMG_LOOP, false)
  // val imgAutoRotation:        Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.IMG_AUTO_ROTATION, false) // Auto Rotation CheckBox.
  val imgAutoMirror:          Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.IMG_MIRROR, false)
  val vidMaxBrightness:       Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.VID_MAX_BRIGHTNESS, false)
  val vidMute:                Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.VID_MUTE, false)
  val vidLoop:                Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.VID_LOOP, false)
  /** Single source of truth: "portrait" | "landscape" | "auto". */
  val vidLandscapeMode: Flow<String> = prefs.getStringFlow(PrefKeys.VID_LANDSCAPE_MODE, "portrait")
  /** Derived: force landscape = mode == "landscape". */
  val vidForceLandscape: Flow<Boolean> = vidLandscapeMode.map { it == "landscape" }
  /** Derived: force portrait = mode == "portrait". */
  val vidForcePortrait: Flow<Boolean> = vidLandscapeMode.map { it == "portrait" }
  /** Derived: auto from video dimensions = mode == "auto". */
  val vidAutoFromDimensions: Flow<Boolean> = vidLandscapeMode.map { it == "auto" }
  val vidSmartPrevious:       Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.VID_SMART_PREVIOUS, false)
  val vidRemember:            Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.VID_REMEMBER, false)
  val vidSmartResume:         Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.VID_SMART_RESUME, false)
  val vidSmoothSwitch:        Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.VID_SMOOTH_SWITCH, false)
  val vidSyncBar:             Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.VID_SYNC_BAR, false)
  val vidVolBoost:            Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.VID_VOL_BOOST, false)
  val vidPauseDisconnected:   Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.VID_PAUSE_DC, false)
  val genKeepScreenOn:        Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.GEN_KEEP_SCREEN, true)
  val genAlbumStart:          Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.GEN_ALBUM_START, true)
  val genNavbar:              Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.GEN_NAVBAR, true)
  val genOverrideOrientation: Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.GEN_OVERRIDE_ORI, false)
  val genRecycle:             Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.GEN_RECYCLE, false)
  val genNomedia:             Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.GEN_NOMEDIA, true)
  val genDot:                 Flow<Boolean> = prefs.getBooleanFlow(PrefKeys.GEN_DOT, false)
  // ---------------- String preferences (DropDown) ----------------
  val imgThumbQuality: Flow<String> = prefs.getStringFlow(PrefKeys.IMG_THUMB_QUALITY, "medium")
  /** Single source of truth: "disable" | "right" | "left". */
  val imgAutoRotationMode: Flow<String> =
  prefs.getStringFlow(PrefKeys.IMG_AUTO_ROTATION_MODE, "disable")
  /** Derived: enabled = mode != "disable". Same name/semantics as before → SetCtrl unchanged. */
  val imgAutoRotation: Flow<Boolean> = imgAutoRotationMode.map { it != "disable" }
  /** Derived: direction is the mode itself. SetCtrl only checks == "right", so "disable" is safe. */
  val imgAutoRotateDirection: Flow<String> = imgAutoRotationMode
  val imgAnimation: Flow<String> = prefs.getStringFlow(PrefKeys.IMG_ANIMATION, "fade")
  // ---------------- Int preferences (SeekBar) ----------------
  val vidAutohideDelay: Flow<Int> = prefs.getIntFlow(PrefKeys.VID_AUTOHIDE, 5)
  // ------::::::
  private fun migrateAutoRotationKeys() {
    if (prefs.contains(PrefKeys.IMG_AUTO_ROTATION_MODE)) return
    val enabled = prefs.getBoolean(PrefKeys.IMG_AUTO_ROTATION, false)
    val oldDir = prefs.getString(PrefKeys.IMG_AUTO_ROTATION_DIRECTION, "left") ?: "left"
    val mode = when {
      !enabled -> "disable"
      oldDir == "right" -> "right"
      else -> "left"
    }
    prefs.edit().putString(PrefKeys.IMG_AUTO_ROTATION_MODE, mode).apply()
  }
  // ================================================================
  //  Helper extension functions to convert SharedPreferences to Flows
  // ================================================================
  private fun SharedPreferences.getBooleanFlow(key: String, defaultValue: Boolean): Flow<Boolean> =
  callbackFlow {
    val listener =
    SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
      if (changedKey == key) {
        trySend(getBoolean(key, defaultValue))
      }
    }
    registerOnSharedPreferenceChangeListener(listener)
    trySend(getBoolean(key, defaultValue)) // emit current value
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
  }
  private fun SharedPreferences.getStringFlow(key: String, defaultValue: String): Flow<String> =
  callbackFlow {
    val listener =
    SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
      if (changedKey == key) {
        trySend(getString(key, defaultValue) ?: defaultValue)
      }
    }
    registerOnSharedPreferenceChangeListener(listener)
    trySend(getString(key, defaultValue) ?: defaultValue)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
  }
  private fun SharedPreferences.getIntFlow(key: String, defaultValue: Int): Flow<Int> =
  callbackFlow {
    val listener =
    SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
      if (changedKey == key) {
        trySend(getInt(key, defaultValue))
      }
    }
    registerOnSharedPreferenceChangeListener(listener)
    trySend(getInt(key, defaultValue))
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
  }
}
