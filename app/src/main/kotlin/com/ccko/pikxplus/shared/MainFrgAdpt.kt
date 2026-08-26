package com.ccko.pikxplus.shared

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.ccko.pikxplus.ux.albums.AlbumsFrg
import com.ccko.pikxplus.ux.camera.CamFrg
import com.ccko.pikxplus.ux.photos.PhotosFrg
import com.ccko.pikxplus.ux.search.SearchFrg
import com.ccko.pikxplus.ux.settings.SetFrg

/** Adapter for main ViewPager2 tabs. Manages fragment creation for each navigation position. */
class MainFrgAdpt(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
  companion object {
    const val POSITION_ALBUMS = 0
    const val POSITION_PHOTOS = 1
    const val POSITION_SEARCH = 2
    const val POSITION_CAMERA = 3
    const val POSITION_SETTINGS = 4
  }
  override fun createFragment(position: Int): Fragment {
    return when (position) {
      POSITION_ALBUMS -> AlbumsFrg()
      POSITION_PHOTOS -> PhotosFrg()
      POSITION_SEARCH -> SearchFrg()
      POSITION_CAMERA -> CamFrg()
      POSITION_SETTINGS -> SetFrg()
      else -> AlbumsFrg()
    }
  }
  override fun getItemCount(): Int = 5
}
