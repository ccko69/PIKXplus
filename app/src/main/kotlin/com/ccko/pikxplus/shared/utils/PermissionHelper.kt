package com.ccko.pikxplus.shared.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import com.ccko.pikxplus.R

/**
 * Centralized permission handling for storage access. Focus on Android 11 (API 30+) as requested.
 */
object PermissionHelper {

  private const val TAG = "PermissionHelper"

  /** Check if storage permission is granted */
  fun hasStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Environment.isExternalStorageManager()
    } else {
      // For API < 30, we still check legacy permission
      android.Manifest.permission.READ_EXTERNAL_STORAGE.let { permission ->
        context.checkCallingOrSelfPermission(permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
      }
    }
  }

  /** Show permission request dialog for Android 11+ */
  fun showPermissionDialog(
          context: Context,
          onGrantClicked: () -> Unit,
          onExitClicked: () -> Unit
  ) {
    AlertDialog.Builder(context)
            .setTitle("Permission Required")
            .setMessage(
                    "This app requires All Files Access to manage your Images. Please enable it in settings."
            )
            .setPositiveButton("Grant") { _, _ -> onGrantClicked() }
            .setNegativeButton("Exit") { _, _ -> onExitClicked() }
            .setCancelable(false)
            .show()
  }

  /** Get intent to open All Files Access settings */
  fun getManageStorageIntent(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      try {
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
          data = Uri.parse("package:${context.packageName}")
        }
      } catch (e: Exception) {
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
      }
    } else {
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
      }
    }
  }

  /** Launch permission settings */
  fun launchPermissionSettings(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
    val intent = getManageStorageIntent(activity)
    launcher.launch(intent)
  }

  /** Check if permission was granted after returning from settings */
  fun verifyPermissionGranted(context: Context): Boolean {
    return hasStoragePermission(context)
  }
}
