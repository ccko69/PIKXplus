package com.ccko.pikxplus.ux.settings
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.ccko.pikxplus.MainActivity
import com.ccko.pikxplus.R
import com.ccko.pikxplus.shared.utils.ImgClrAdjst
import com.ccko.pikxplus.ux.albums.AlbumsFrg
import com.ccko.pikxplus.ux.settings.SetFrg.ColorAdjustDialogFragment
import com.google.android.material.slider.Slider
class SetFrg : PreferenceFragmentCompat() {
  private var albumFrg : AlbumsFrg? = null
  // private var imgClr : ImgClrAdjst? = null
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.settings, rootKey)
    // val prefs = MainActivity.prefs
    // setEnhanceValue(prefs.getFloat("img_adj_brightness", 0f), prefs.getFloat("img_adj_contrast", 1f), prefs.getFloat("img_adj_saturation", 1f))
    // Mirror only makes sense while Auto Rotation is enabled.
    val rotationPref = findPreference<ValueDropDownPreference>(PrefKeys.IMG_AUTO_ROTATION_MODE)
    val mirrorPref = findPreference<SwitchPreferenceCompat>(PrefKeys.IMG_MIRROR)
    fun syncMirrorState() {mirrorPref?.isEnabled = rotationPref?.value != "disable"  }
    syncMirrorState()
    rotationPref?.setOnPreferenceChangeListener { _, newValue ->
      mirrorPref?.isEnabled = newValue != "disable"
      true
    }
  }
  // If you need to react to changes on the fly:
  override fun onPreferenceTreeClick(preference: Preference): Boolean {
    when (preference.key) {
      // "gen_scan"      -> Toast.makeText(context, "Scanning the Storage.", Toast.LENGTH_SHORT).show()
      "gen_scan" -> {
        albumFrg?.refreshAllFragments()
        Toast.makeText(context, "Scanning the Storage.", Toast.LENGTH_SHORT).show()
      }
      "gen_cache" -> Toast.makeText(context, "Cache Cleard.", Toast.LENGTH_SHORT).show()
      "gen_about" -> Toast.makeText(context, "PIKX+ - Android Gallery App", Toast.LENGTH_SHORT).show()
      "gen_build_ver" -> Toast.makeText(context, "v2.13.354", Toast.LENGTH_SHORT).show()
      // === PLACEHOLDER.===
    }
    return super.onPreferenceTreeClick(preference)
  }
  override fun onDisplayPreferenceDialog(preference: Preference) {
    if (preference is ColorAdjustPreference) {
      val dialogFragment = ColorAdjustDialogFragment.newInstance(preference.key)
      @Suppress("DEPRECATION")
      dialogFragment.setTargetFragment(this, 0)
      dialogFragment.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
    } else {
      super.onDisplayPreferenceDialog(preference)
    }
  }
  // override fun onPause() {
  // super.onPause()
  // val prefs = MainActivity.prefs
  // setEnhanceValue(prefs.getFloat("img_adj_brightness", 0f), prefs.getFloat("img_adj_contrast", 1f), prefs.getFloat("img_adj_saturation", 1f))
  // }
  // fun setEnhanceValue(b: Float, c: Float, s: Float){
  // imgClr?.setBrightness(b)
  // imgClr?.setContrast(c)
  // imgClr?.setSaturation(s)
  // }
  class ColorAdjustDialogFragment : PreferenceDialogFragmentCompat() {
    private lateinit var sliderBrightness: Slider
    private lateinit var sliderContrast: Slider
    private lateinit var sliderSaturation: Slider
    companion object {
      fun newInstance(key: String): ColorAdjustDialogFragment {
        val fragment = ColorAdjustDialogFragment()
        val b = Bundle(1)
        b.putString(ARG_KEY, key)
        fragment.arguments = b
        return fragment
      }
    }
    override fun onBindDialogView(view: View) {
      super.onBindDialogView(view)
      sliderBrightness = view.findViewById(R.id.slider_brightness)
      sliderContrast = view.findViewById(R.id.slider_contrast)
      sliderSaturation = view.findViewById(R.id.slider_saturation)
      // Load saved values
      val prefs = MainActivity.prefs
      sliderBrightness.value = prefs.getFloat("img_adj_brightness", 0f)
      sliderContrast.value = prefs.getFloat("img_adj_contrast", 1f)
      sliderSaturation.value = prefs.getFloat("img_adj_saturation", 1f)
    }
    override fun onDialogClosed(positiveResult: Boolean) {
      if (positiveResult) {
        // setEnhanceValue(sliderBrightness.value, sliderContrast.value, sliderSaturation.value)
        MainActivity.prefs.edit()
        .putFloat("img_adj_brightness", sliderBrightness.value)
        .putFloat("img_adj_contrast", sliderContrast.value)
        .putFloat("img_adj_saturation", sliderSaturation.value)
        .apply()
      }
    }
  }
}
