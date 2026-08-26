package com.ccko.pikxplus.ux.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.ccko.pikxplus.R
import com.google.android.material.textview.MaterialTextView

/** Camera Fragment - Placeholder for testing and log for now. */
class CamFrg : Fragment() {

  private lateinit var btnR: Button
  private lateinit var btnM: Button
  private lateinit var btnL: Button
  private lateinit var textLog: MaterialTextView

  override fun onCreateView(
          inflater: LayoutInflater,
          container: ViewGroup?,
          savedInstanceState: Bundle?
  ): View {
    return inflater.inflate(R.layout.frg_cam, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    (activity as? AppCompatActivity)?.supportActionBar?.hide()
    btnR = view.findViewById(R.id.btnR)
    btnM = view.findViewById(R.id.btnM)
    btnL = view.findViewById(R.id.btnL)
    textLog = view.findViewById(R.id.textLog)

    btnM.setOnClickListener { /*...*/}
  }

  fun interLog(msg: String) {
    textLog.text = "$msg\n${textLog.text}"
    // you can also Log.d
  }

  override fun onResume() {
    super.onResume()
  }

  override fun onPause() {
    super.onPause()
  }

  override fun onDestroyView() {
    super.onDestroyView()
  }
}
