package com.ccko.pikxplus.shared.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager

data class MediaItemz(
    val uri: Uri,
    val displayName: String
)

class MediaDeletionHelper(
    private val context: Context,
    private val treePickerLauncher: ActivityResultLauncher<Uri?>,
    private val onAllDeletionsComplete: (deletedCount: Int, failedCount: Int) -> Unit
) {
    private val prefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)
    private val resolver = context.contentResolver

    // Holds items that need SAF deletion
    private var pendingSafItems: List<MediaItemz> = emptyList()
    private var pendingDeletedCount = 0

    /**
     * Call this to delete a list of media items.
     * It will try MediaStore first, and for those that fail with SecurityException,
     * it will use SAF (with cached tree or picker).
     */
    fun deleteItems(items: List<MediaItemz>) {
        if (items.isEmpty()) return

        val mediaStoreOk = mutableListOf<MediaItemz>()
        val mediaStoreFailed = mutableListOf<MediaItemz>()

        // Try MediaStore for all items
        for (item in items) {
            if (deleteViaMediaStore(item.uri)) {
                mediaStoreOk.add(item)
            } else {
                mediaStoreFailed.add(item)
            }
        }

        // If everything was deleted via MediaStore, we are done
        if (mediaStoreFailed.isEmpty()) {
            onAllDeletionsComplete(mediaStoreOk.size, 0)
            return
        }

        // Some items need SAF – check for cached tree URI
        val cachedTreeUri = prefs.getString("saf_tree_uri", null)?.let { Uri.parse(it) }

        if (cachedTreeUri != null && isTreeStillValid(cachedTreeUri)) {
            // Try to delete pending items using the cached tree
            val remaining = deleteFromTree(cachedTreeUri, mediaStoreFailed)
            val deletedCount = mediaStoreOk.size + (mediaStoreFailed.size - remaining.size)
            val failedCount = remaining.size
            if (remaining.isEmpty()) {
                onAllDeletionsComplete(deletedCount, 0)
            } else {
                // If some still not found, fall through to picker flow
                pendingSafItems = remaining
                pendingDeletedCount = mediaStoreOk.size
                showPickerDialog()
            }
        } else {
            // No valid cached tree – show picker dialog
            pendingSafItems = mediaStoreFailed
            pendingDeletedCount = mediaStoreOk.size
            showPickerDialog()
        }
    }

    /**
     * Handle the tree picker result from the fragment/activity.
     * Call this in the registerForActivityResult callback.
     */
    fun handleTreePickerResult(treeUri: Uri?) {
        if (treeUri == null) {
            // User cancelled picker
            Toast.makeText(context, "Folder selection cancelled", Toast.LENGTH_SHORT).show()
            val failedCount = pendingSafItems.size
            pendingSafItems = emptyList()
            onAllDeletionsComplete(pendingDeletedCount, failedCount)
            return
        }

        // Save persistently
        prefs.edit().putString("saf_tree_uri", treeUri.toString()).apply()

        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        resolver.takePersistableUriPermission(treeUri, takeFlags)

        // Delete pending items from the selected tree
        val remaining = deleteFromTree(treeUri, pendingSafItems)
        val totalDeleted = pendingDeletedCount + (pendingSafItems.size - remaining.size)
        val totalFailed = remaining.size

        pendingSafItems = emptyList()
        onAllDeletionsComplete(totalDeleted, totalFailed)
    }

    private fun showPickerDialog() {
        AlertDialog.Builder(context)
            .setTitle("Permission Required")
            .setMessage(
                "To delete this file, you need to grant access to the folder where it is stored.\n\n" +
                "Please navigate to that folder and tap \"Allow\"."
            )
            .setPositiveButton("Open folder picker") { _, _ ->
                treePickerLauncher.launch(null) // launch from root
            }
            .setNegativeButton("Cancel") { _, _ ->
                val failedCount = pendingSafItems.size
                pendingSafItems = emptyList()
                onAllDeletionsComplete(pendingDeletedCount, failedCount)
            }
            .show()
    }

    // ---------- MediaStore deletion ----------
    private fun deleteViaMediaStore(uri: Uri): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(uri, values, null, null)
            resolver.delete(uri, null, null) > 0
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    // ---------- SAF deletion with recursive search ----------
    private fun deleteFromTree(treeUri: Uri, items: List<MediaItemz>): List<MediaItemz> {
        val remaining = mutableListOf<MediaItemz>()
        // Build a set of display names to find quickly
        val namesToFind = items.map { it.displayName }.toMutableSet()

        // Recursive search and delete
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        deleteItems(items)
        // deleteRecursive(treeUri, rootChildrenUri, namesToFind)

        // Any names still in the set were not found/deleted
        for (item in items) {
            if (item.displayName in namesToFind) {
                remaining.add(item)
            }
        }
        return remaining
    }

  /*  private fun deleteRecursive(
        treeUri: Uri,
        parentUri: Uri,
        namesToFind: MutableSet<String>
    ) {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val cursor = resolver.query(parentUri, projection, null, null, null) ?: return
        cursor.use {
            while (it.moveToNext() && namesToFind.isNotEmpty()) {
                val docId = it.getString(0)
                val displayName = it.getString(1)
                val mimeType = it.getString(2)

                if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                    // Recurse into subdirectory
                    val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                    deleteRecursive(treeUri, childUri, namesToFind)
                } else if (displayName in namesToFind) {
                    // Found a file to delete
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    try {
                        DocumentsContract.deleteDocument(resolver, docUri)
                        namesToFind.remove(displayName) // delete one occurrence
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Could not delete this one, keep in set
                    }
                }
            }
        }
    }*/

    // ---------- Cache validation ----------
    private fun isTreeStillValid(treeUri: Uri): Boolean {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        return try {
            resolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null, null, null)?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }
}