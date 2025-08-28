package com.example.hdr_assets

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetManager
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class HdrAssetsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? {
        return when (uri.path?.substringAfterLast('.').orEmpty().lowercase()) {
            "hdr" -> "application/octet-stream"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "application/octet-stream"
        }
    }

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: throw FileNotFoundException("Context is null")
        
        try {
            val pathSegments = uri.pathSegms() ?: throw FileNotFoundException("No path provided")
            
            // The path should be in the format: /hdri_4k/filename.hdr
            if (pathSegments.size != 2) {
                throw FileNotFoundException("Invalid path format. Expected: /hdri_4k/filename.hdr")
            }
            
            val assetPath = "${pathSegments[0]}/${pathSegments[1]}"
            
            return openAssetFile(assetPath, mode) ?: throw FileNotFoundException("Asset not found: $assetPath")
        } catch (e: Exception) {
            Log.e("HdrAssetsProvider", "Error opening file: ${uri.path}", e)
            throw FileNotFoundException("Could not open file: ${e.message}")
        }
    }
    
    @Throws(FileNotFoundException::class)
    private fun openAssetFile(assetPath: String, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null
        
        return when (mode) {
            "r" -> {
                try {
                    val afd = context.assets.openFd(assetPath)
                    val fd = afd.fileDescriptor
                    if (fd.valid()) {
                        ParcelFileDescriptor.dup(fd).also {
                            try {
                                afd.close()
                            } catch (e: IOException) {
                                Log.w("HdrAssetsProvider", "Error closing AssetFileDescriptor", e)
                            }
                        }
                    } else {
                        afd.close()
                        throw IOException("Invalid file descriptor for asset: $assetPath")
                    }
                } catch (e: IOException) {
                    Log.e("HdrAssetsProvider", "Error opening asset file: $assetPath", e)
                    throw FileNotFoundException("Could not open file: $assetPath")
                }
            }
            else -> throw SecurityException("Unsupported mode: $mode. Only 'r' (read) mode is supported.")
        }
    }
    
    private fun Uri.pathSegms(): List<String>? {
        return path?.trimStart('/')?.split('/')?.filter { it.isNotEmpty() }
    }

    // Unused methods
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
