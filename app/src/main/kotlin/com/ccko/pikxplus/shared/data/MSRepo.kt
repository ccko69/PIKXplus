package com.ccko.pikxplus.shared.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.ContentResolver

/**
 * Repository for MediaStore queries.
 * All methods are suspend functions to run on IO dispatcher.
 *
 * NOTE: Animated WebP detection is deferred to the viewer (lazy) for performance.
 *       GIFs are detected here by MIME type since it's free (no I/O needed).
 */
class MSRepo(private val context: Context) {

    companion object {
        private const val TAG = "MediaStoreRepository"
    }
    // ===== ALBUM LOADING =====
    /**
     * Load all albums with separate photo and video counts.
     * Runs on IO dispatcher.
     */
    suspend fun loadAlbums(): List<AlbumInfo> = withContext(Dispatchers.IO) {
        val albumMap = LinkedHashMap<String, AlbumInfo>()
        loadImageAlbums(albumMap)
        loadVideoAlbums(albumMap)

        val albums = mutableListOf<AlbumInfo>()
        var totalPhotos = 0
        var totalVideos = 0
        var latestThumbnail: Uri? = null

        for (album in albumMap.values) {
            totalPhotos += album.photoCount
            totalVideos += album.videoCount
            if (latestThumbnail == null) {
                latestThumbnail = album.thumbnailUri
            }
        }
        if (totalPhotos + totalVideos > 0 && latestThumbnail != null) {
            albums.add(
                AlbumInfo(
                    id = "all_media",
                    name = "All Media",
                    photoCount = totalPhotos,
                    videoCount = totalVideos,
                    thumbnailUri = latestThumbnail,
                    relativePath = null,
                    volumeName = "external_primary"
                )
            )
        }
        albums.addAll(albumMap.values)
        albums
    }
    /**
     * Load image albums and populate the map.
     * Thumbnail is set once on first encounter (newest image, since query is DATE_TAKEN DESC).
     */
    private fun loadImageAlbums(albumMap: MutableMap<String, AlbumInfo>) {
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.VOLUME_NAME
        )
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            )?.use { cursor ->
                if (cursor.count == 0) return

                val bucketIdCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val idCol         = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val relPathCol    = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                val volumeCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.VOLUME_NAME)

                while (cursor.moveToNext()) {
                    val bucketId     = cursor.getString(bucketIdCol)
                    val bucketName   = cursor.getString(bucketNameCol)
                    val imageId      = cursor.getLong(idCol)
                    val relativePath = cursor.getString(relPathCol)
                    val volumeName   = cursor.getString(volumeCol)

                    val thumbnailUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId
                    )
                    // FIX: getOrPut only sets thumbnail on first encounter (newest image).
                    // it does NOT overwrite thumbnailUri afterward — that was making it the oldest.
                    val album = albumMap.getOrPut(bucketId) {
                        AlbumInfo(
                            id           = bucketId,
                            name         = bucketName,
                            photoCount   = 0,
                            videoCount   = 0,
                            thumbnailUri = thumbnailUri,
                            relativePath = relativePath,
                            volumeName   = volumeName
                        )
                    }
                    album.photoCount++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image albums", e)
        }
    }
    /**
     * Load video albums and merge into existing map.
     * Thumbnail is only set for video-only albums (no photos).
     */
    private fun loadVideoAlbums(albumMap: MutableMap<String, AlbumInfo>) {
        val projection = arrayOf(
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.VOLUME_NAME
        )
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_TAKEN} DESC"
            )?.use { cursor ->
                if (cursor.count == 0) return

                val bucketIdCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val idCol         = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val relPathCol    = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
                val volumeCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.VOLUME_NAME)

                while (cursor.moveToNext()) {
                    val bucketId     = cursor.getString(bucketIdCol)
                    val bucketName   = cursor.getString(bucketNameCol)
                    val videoId      = cursor.getLong(idCol)
                    val relativePath = cursor.getString(relPathCol)
                    val volumeName   = cursor.getString(volumeCol)

                    val thumbnailUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId
                    )

                    val album = albumMap.getOrPut(bucketId) {
                        AlbumInfo(
                            id           = bucketId,
                            name         = bucketName,
                            photoCount   = 0,
                            videoCount   = 0,
                            thumbnailUri = thumbnailUri, // Only used if no photos exist
                            relativePath = relativePath,
                            volumeName   = volumeName
                        )
                    }
                    album.videoCount++
                    // Keep photo thumbnail if photos exist; only set video thumbnail for video-only albums
                    if (album.photoCount == 0 && album.videoCount == 1) {
                        album.thumbnailUri = thumbnailUri // First (newest) video thumbnail
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading video albums", e)
        }
    }
    // ===== MEDIA COUNT =====
    /**
     * Get total media count for an album without loading all items.
     * Useful for showing "Loading X items..." progress.
     */
    suspend fun loadMediaCount(
        albumId: String?,
        albumName: String?,
        folderName: String?
    ): Int = withContext(Dispatchers.IO) {
        getImageCount(albumId, albumName, folderName) +
        getVideoCount(albumId, albumName, folderName)
    }
    private fun getImageCount(albumId: String?, albumName: String?, folderName: String?): Int {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val (selection, selectionArgs) = buildSelection(albumId, albumName, folderName, isVideo = false)
        return try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null
            )?.use { it.count } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error counting images", e)
            0
        }
    }
    private fun getVideoCount(albumId: String?, albumName: String?, folderName: String?): Int {
        val projection = arrayOf(MediaStore.Video.Media._ID)
        val (selection, selectionArgs) = buildSelection(albumId, albumName, folderName, isVideo = true)
        return try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null
            )?.use { it.count } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error counting videos", e)
            0
        }
    }
    // ===== MEDIA LOADING =====
    /**
     * Load media items for a specific album with optional pagination.
     * limit = 0 means load all. offset = starting cursor position.
     */
    suspend fun loadMediaForAlbum(
        albumId: String?,
        albumName: String?,
        folderName: String?,
        limit: Int = 0,
        offset: Int = 0
    ): List<MediaItems> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItems>()
        items.addAll(loadImagesForAlbum(albumId, albumName, folderName, limit, offset))
        items.addAll(loadVideosForAlbum(albumId, albumName, folderName, limit, offset))
        items
    }
    /**
     * Load images for a specific album with pagination support.
     * FIX: This is a plain fun (not suspend) — detectAnimationType is NOT called here.
     * Animated WebP is detected lazily in the viewer; GIF is free via MIME type.
     */
     
    // private fun loadImagesForAlbum(
    private suspend fun loadImagesForAlbum(
        albumId: String?,
        albumName: String?,
        folderName: String?,
        limit: Int = 0,
        offset: Int = 0
    ): List<MediaItems> {
        val images = mutableListOf<MediaItems>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.VOLUME_NAME,
            MediaStore.Images.Media.MIME_TYPE
        )
        val (selection, selectionArgs) = buildSelection(albumId, albumName, folderName, isVideo = false)

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol         = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol       = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol       = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val sizeCol       = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthCol      = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val mimeTypeCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val volumeCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.VOLUME_NAME)
                val bucketIdCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val relPathCol    = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

                if (offset > 0 && !cursor.moveToPosition(offset)) return@use

                var count = 0
                while (cursor.moveToNext() && (limit == 0 || count < limit)) {
                    val id           = cursor.getLong(idCol)
                    val name         = cursor.getString(nameCol)
                    val dateModified = cursor.getLong(dateCol)
                    val size         = cursor.getLong(sizeCol)
                    val width        = cursor.getInt(widthCol)
                    val height       = cursor.getInt(heightCol)
                    val mimeType     = cursor.getString(mimeTypeCol)
                    val volumeName   = cursor.getString(volumeCol)
                    val bucketId     = cursor.getString(bucketIdCol)
                    val bucketName   = cursor.getString(bucketNameCol)
                    val relativePath = cursor.getString(relPathCol)

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    // GIF detected free via MIME type.- Animated WebP requires I/O
                    var type = if (mimeType == "image/gif") {
                        MediaItems.MediaType.ANIMATED
                    } else {
                        MediaItems.MediaType.IMAGE
                    }
                    // Check WebP headers during load
                  /*  if (type == MediaItems.MediaType.IMAGE && mimeType == "image/webp") {
                        type = detectAnimationType(uri, mimeType)
                    }*/
                    
                    images.add(
                        MediaItems.createImage(
                            id           = id.toString(),
                            name         = name,
                            dateModified = dateModified,
                            size         = size,
                            uri          = uri,
                            mimeType     = mimeType,
                            width        = width,
                            height       = height,
                            bucketId     = bucketId,
                            bucketName   = bucketName,
                            relativePath = relativePath,
                            volumeName   = volumeName
                        ).copy(type = type)
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading images", e)
        }
        return images
    }
    /**
     * Load videos for a specific album with pagination support.
     */
    private fun loadVideosForAlbum(
        albumId: String?,
        albumName: String?,
        folderName: String?,
        limit: Int = 0,
        offset: Int = 0
    ): List<MediaItems> {
        val videos = mutableListOf<MediaItems>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.VOLUME_NAME,
            MediaStore.Video.Media.MIME_TYPE
        )
        val (selection, selectionArgs) = buildSelection(albumId, albumName, folderName, isVideo = true)
        
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol         = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol       = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateCol       = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val sizeCol       = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeTypeCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val widthCol      = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val durationCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val volumeCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.VOLUME_NAME)
                val bucketIdCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val relPathCol    = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)

                if (offset > 0 && !cursor.moveToPosition(offset)) return@use

                var count = 0
                while (cursor.moveToNext() && (limit == 0 || count < limit)) {
                    val id           = cursor.getLong(idCol)
                    val name         = cursor.getString(nameCol)
                    val dateModified = cursor.getLong(dateCol)
                    val size         = cursor.getLong(sizeCol)
                    val mimeType     = cursor.getString(mimeTypeCol)
                    val width        = cursor.getInt(widthCol)
                    val height       = cursor.getInt(heightCol)
                    val duration     = cursor.getLong(durationCol)
                    val volumeName   = cursor.getString(volumeCol)
                    val bucketId     = cursor.getString(bucketIdCol)
                    val bucketName   = cursor.getString(bucketNameCol)
                    val relativePath = cursor.getString(relPathCol)

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val type = if (mimeType == "image/gif") {
                        MediaItems.MediaType.ANIMATED
                    } else {
                        MediaItems.MediaType.VIDEO
                    }
                    videos.add(
                        MediaItems.createVideo(
                            id           = id.toString(),
                            name         = name,
                            dateModified = dateModified,
                            size         = size,
                            uri          = uri,
                            mimeType = mimeType, 
                            width        = width,
                            height       = height,
                            duration     = duration,
                            bucketId     = bucketId,
                            bucketName   = bucketName,
                            relativePath = relativePath,
                            volumeName   = volumeName
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading videos", e)
        }
        return videos
    }
    // ===== HELPERS =====
    /**
     * Build a WHERE clause based on album parameters.
     * FIX: Uses correct MediaStore constants per media type (image vs video).
     */
    private fun buildSelection(
        albumId: String?,
        albumName: String?,
        folderName: String?,
        isVideo: Boolean
    ): Pair<String?, Array<String>?> {
        val bucketNameCol = if (isVideo) MediaStore.Video.Media.BUCKET_DISPLAY_NAME
                           else MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        val relativePathCol = if (isVideo) MediaStore.Video.Media.RELATIVE_PATH
                              else MediaStore.Images.Media.RELATIVE_PATH
        val bucketIdCol = if (isVideo) MediaStore.Video.Media.BUCKET_ID
                          else MediaStore.Images.Media.BUCKET_ID
        return when {
            albumId == "all_media"                -> null to null
            !albumName.isNullOrEmpty()            -> "$bucketNameCol = ?" to arrayOf(albumName)
            !folderName.isNullOrEmpty()           -> "$relativePathCol = ?" to arrayOf(folderName)
            albumId != null                       -> "$bucketIdCol = ?" to arrayOf(albumId)
            else                                  -> null to null
        }
    }
    /**
     * Detect if an image is animated (GIF or animated WebP).
     * Call this LAZILY from the viewer when the user opens an image — not during loading.
     */
    suspend fun detectAnimationType(uri: Uri, mimeType: String?): MediaItems.MediaType =
        withContext(Dispatchers.IO) {
            if (mimeType == "image/gif") return@withContext MediaItems.MediaType.ANIMATED

            if (mimeType?.contains("webp") == true) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val buffer = ByteArray(30)
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead >= 30) {
                            // RIFF....WEBP header check
                            if (buffer[0] == 'R'.code.toByte() &&
                                buffer[1] == 'I'.code.toByte() &&
                                buffer[2] == 'F'.code.toByte() &&
                                buffer[3] == 'F'.code.toByte() &&
                                buffer[8] == 'W'.code.toByte() &&
                                buffer[9] == 'E'.code.toByte() &&
                                buffer[10] == 'B'.code.toByte() &&
                                buffer[11] == 'P'.code.toByte()
                            ) {
                                // VP8X chunk signals extended format (animation capable)
                                if (buffer[12] == 'V'.code.toByte() &&
                                    buffer[13] == 'P'.code.toByte() &&
                                    buffer[14] == '8'.code.toByte() &&
                                    buffer[15] == 'X'.code.toByte()
                                ) {
                                    // Byte 20: flags — bit 1 is the animation flag
                                    val hasAnimation = (buffer[20].toInt() and 0x02) != 0
                                    if (hasAnimation) return@withContext MediaItems.MediaType.ANIMATED
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error detecting WebP animation", e)
                }
            }
            return@withContext MediaItems.MediaType.IMAGE
        }
}
