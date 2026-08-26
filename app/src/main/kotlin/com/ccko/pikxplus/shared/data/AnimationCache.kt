package com.ccko.pikxplus.shared.data

import android.content.Context
import android.content.SharedPreferences

class AnimationCache(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("webp_anim_cache", Context.MODE_PRIVATE)

    /** Returns null if not cached, true/false if cached */
    fun get(id: String, dateModified: Long): Boolean? {
        val key = "${id}_$dateModified"
        return if (prefs.contains(key)) prefs.getBoolean(key, false) else null
    }

    fun put(id: String, dateModified: Long, isAnimated: Boolean) {
        prefs.edit().putBoolean("${id}_$dateModified", isAnimated).apply()
    }
}