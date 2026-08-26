package com.ccko.pikxplus.ux.settings
import android.content.Context
import android.util.AttributeSet
import androidx.preference.DialogPreference
import com.ccko.pikxplus.R
import com.google.android.material.slider.Slider
class ColorAdjustPreference @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : DialogPreference(context, attrs) {
  init {
    // Use the same layout for the dialog and the summary
    dialogLayoutResource = R.layout.dialog_color_adjust
    // positiveButtonText = "Apply"
    // negativeButtonText = "Cancel"
  }
  override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any? {
    return a.getString(index)
  }
  override fun onSetInitialValue(defaultValue: Any?) {
    // We don't persist a single string here; we persist 3 separate floats.
    // This just ensures the preference initializes.
  }
}
