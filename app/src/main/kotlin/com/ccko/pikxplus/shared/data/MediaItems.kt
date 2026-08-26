package com.ccko.pikxplus.shared.data

import android.net.Uri
import android.os.Parcelable
import java.util.Locale
import kotlinx.parcelize.Parcelize

import android.provider.MediaStore
import android.content.ContentResolver

@Parcelize
data class MediaItems(
        val id: String,
        val name: String,
        val dateModified: Long,
        val size: Long,
        val uri: Uri,
        val type: MediaType,
        val mimeType: String,
        val width: Int,
        val height: Int,
        val duration: Long = 0,
        val bucketId: String? = null,
        val bucketName: String? = null,
        val relativePath: String? = null,
        val volumeName: String? = null
) : Parcelable {

  enum class MediaType {
    IMAGE, VIDEO, ANIMATED
  }
  val isOnSdCard: Boolean
    get() = volumeName != null && volumeName != "external_primary"

  fun isVideo(): Boolean = type == MediaType.VIDEO
  fun isAnimated(): Boolean = type == MediaType.ANIMATED
  fun isStaticImage(): Boolean = type == MediaType.IMAGE

  fun getFormattedDuration(): String {
    if (!isVideo() || duration <= 0) return ""
    val totalSeconds = duration / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
      String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
      String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
  }
  fun getFormattedDimensions(): String {
    return if (width > 0 && height > 0) "${width}x${height}" else ""
  }
  companion object {
    fun createImage(
            id: String,
            name: String,
            dateModified: Long,
            size: Long,
            uri: Uri,
            mimeType: String,
            width: Int,
            height: Int,
            bucketId: String? = null,
            bucketName: String? = null,
            relativePath: String? = null,
            volumeName: String? = null
    ): MediaItems {
      return MediaItems(
              id = id,
              name = name,
              dateModified = dateModified,
              size = size,
              uri = uri,
              type = MediaType.IMAGE,
              mimeType = mimeType,
              width = width,
              height = height,
              duration = 0,
              bucketId = bucketId,
              bucketName = bucketName,
              relativePath = relativePath,
              volumeName = volumeName
      )
    }
    fun createVideo(
            id: String,
            name: String,
            dateModified: Long,
            size: Long,
            uri: Uri,
            mimeType: String,
            width: Int,
            height: Int,
            duration: Long,
            bucketId: String? = null,
            bucketName: String? = null,
            relativePath: String? = null,
            volumeName: String? = null
    ): MediaItems {
      return MediaItems(
              id = id,
              name = name,
              dateModified = dateModified,
              size = size,
              uri = uri,
              type = MediaType.VIDEO,
              mimeType = mimeType,
              width = width,
              height = height,
              duration = duration,
              bucketId = bucketId,
              bucketName = bucketName,
              relativePath = relativePath,
              volumeName = volumeName
      )
    }
        /**
     * Builds a single MediaItems from a content URI.
     * Safe fallback when the URI doesn’t belong to a known album bucket.
     */
    fun fromUri(uri: Uri, contentResolver: ContentResolver): MediaItems? {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.VOLUME_NAME
        )
        return try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                    val dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED))
                    val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                    val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))
                    val width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH))
                    val height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT))
                    val duration = if (mimeType?.startsWith("video/") == true)
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION))
                    else 0L
                    val bucketId = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID))
                    val bucketName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME))
                    val relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH))
                    val volumeName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.VOLUME_NAME))
    
                    if (mimeType?.startsWith("video/") == true) {
                        createVideo(
                            id = id.toString(),
                            name = name ?: "video",
                            dateModified = dateModified,
                            size = size,
                            uri = uri,
                            mimeType = mimeType,
                            width = width,
                            height = height,
                            duration = duration,
                            bucketId = bucketId,
                            bucketName = bucketName,
                            relativePath = relativePath,
                            volumeName = volumeName
                        )
                    } else {
                        createImage(
                            id = id.toString(),
                            name = name ?: "image",
                            dateModified = dateModified,
                            size = size,
                            uri = uri,
                            mimeType = mimeType ?: "image/*",
                            width = width,
                            height = height,
                            bucketId = bucketId,
                            bucketName = bucketName,
                            relativePath = relativePath,
                            volumeName = volumeName
                        )
                    }
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
    
  }
}