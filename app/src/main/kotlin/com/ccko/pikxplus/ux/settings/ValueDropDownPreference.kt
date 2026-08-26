package com.ccko.pikxplus.ux.settings
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.ListPopupWindow
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.ccko.pikxplus.R
import com.google.android.material.color.MaterialColors
class ValueDropDownPreference @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : Preference(context, attrs) {
  private var entries: Array<CharSequence>? = null
  private var entryValues: Array<CharSequence>? = null
  private var currentValue: String? = null
  private var holder: PreferenceViewHolder? = null
  init {
    // Inject our reusable widget layout (value + arrow) into the right side of the row
    widgetLayoutResource = R.layout.preference_widget_dropdown_value
    // Read entries and entryValues from XML attributes
    val a = context.obtainStyledAttributes(attrs, androidx.preference.R.styleable.ListPreference)
    entries = a.getTextArray(androidx.preference.R.styleable.ListPreference_entries)
    entryValues = a.getTextArray(androidx.preference.R.styleable.ListPreference_entryValues)
    a.recycle()
  }
  override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
    return a.getString(index)
  }
  override fun onSetInitialValue(defaultValue: Any?) {
    value = getPersistedString(defaultValue as? String)
  }
  override fun onBindViewHolder(holder: PreferenceViewHolder) {
    super.onBindViewHolder(holder)
    this.holder = holder
    // Update the text in the widget layout
    val text = getEntryForValue(currentValue)
    (holder.findViewById(R.id.pref_value_text) as? TextView)?.text = text ?: ""
  }
  override fun onClick() {
    // Anchor the popup specifically to the widget frame (the right side with the arrow)
    val anchorView = holder?.findViewById(android.R.id.widget_frame) ?: holder?.itemView
    val popup = ListPopupWindow(context)
    popup.anchorView = anchorView
    popup.width = ListPopupWindow.WRAP_CONTENT
    popup.setDropDownGravity(Gravity.BOTTOM)
    popup.verticalOffset = -80
    popup.isModal = true // Dismisses when clicking outside
    // Use the new BaseAdapter instead of ArrayAdapter
    val adapter = CheckmarkAdapter(entries ?: emptyArray())
    popup.setAdapter(adapter)
    popup.setOnItemClickListener { _, _, position, _ ->
      val newValue = entryValues?.getOrNull(position)?.toString() ?: return@setOnItemClickListener
      if (callChangeListener(newValue)) {
        value = newValue
      }
      popup.dismiss()
    }
    // Rounded corners background
    val bg = GradientDrawable().apply {
      setColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.WHITE))
      cornerRadius = 16f * context.resources.displayMetrics.density
    }
    popup.setBackgroundDrawable(bg)
    popup.show()
  }
  var value: String?
  get() = currentValue
  set(newValue) {
    if (newValue != currentValue) {
      currentValue = newValue
      persistString(newValue)
      notifyChanged() // Triggers onBindViewHolder to update the row text
    }
  }
  private fun getEntryForValue(value: String?): CharSequence? {
    if (value == null || entryValues == null) return null
    val index = entryValues!!.indexOf(value)
    return if (index >= 0) entries?.getOrNull(index) else null
  }
  /**
     * Custom adapter that inflates the dropdown items and shows a checkmark 
     * next to the currently selected item.
     * 
     * Note: We use BaseAdapter instead of ArrayAdapter because ArrayAdapter's default 
     * getView() expects the root layout to be a TextView, which causes a 
     * ClassCastException when using a custom LinearLayout layout.
     */
  private inner class CheckmarkAdapter(
    private val items: Array<CharSequence>
  ) : BaseAdapter() {
    override fun getCount(): Int = items.size
    override fun getItem(position: Int): CharSequence = items[position]
    override fun getItemId(position: Int): Long = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.spinner_item_with_check, parent, false)
      val textView = view.findViewById<TextView>(android.R.id.text1)
      textView.text = items[position]
      val checkIcon = view.findViewById<ImageView>(R.id.spinner_check)
      val itemValue = entryValues?.getOrNull(position)?.toString()
      if (itemValue == currentValue) {
        checkIcon?.visibility = View.VISIBLE
      } else {
        checkIcon?.visibility = View.GONE
      }
      return view
    }
  }
}
