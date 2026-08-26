package com.ccko.pikxplus.shared.data

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Album/Folder information data class */
@Parcelize
data class AlbumInfo(
    val id: String,
    val name: String,
    var photoCount: Int = 0,
    var videoCount: Int = 0,
    var thumbnailUri: Uri? = null,
    val relativePath: String? = null,
    val volumeName: String? = null,
    var isBookmarked: Boolean = false,
    var isHidden: Boolean = false
) : Parcelable {

    val totalCount: Int get() = photoCount + videoCount
    val isOnSdCard: Boolean get() = volumeName != null && volumeName != "external_primary"

    fun hasOnlyPhotos() = photoCount > 0 && videoCount == 0
    fun hasOnlyVideos() = videoCount > 0 && photoCount == 0
    fun hasMixedMedia() = photoCount > 0 && videoCount > 0
    fun isVideoAlbum() = videoCount > 0 && photoCount == 0

    fun getMediaTypeLabel(): String = when {
        photoCount > 0 && videoCount > 0 -> "$photoCount photos, $videoCount videos"
        photoCount > 0 -> "$photoCount photos"
        videoCount > 0 -> "$videoCount videos"
        else -> "0 items"
    }
}