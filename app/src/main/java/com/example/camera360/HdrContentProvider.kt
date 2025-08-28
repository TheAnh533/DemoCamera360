package com.example.camera360

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileNotFoundException

class HdrContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        throw FileNotFoundException("Not supported, use openAssetFile instead")
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val assetName = uri.lastPathSegment ?: return null
        val input = context?.assets?.open("hdri_4k/$assetName") // chỉ trả về InputStream
        // Tạo pipe để stream cho WebView
        val pipe = ParcelFileDescriptor.createPipe()
        val output = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])

        Thread {
            input.use { inp ->
                output.use { out ->
                    inp!!.copyTo(out)
                }
            }
        }.start()

        return AssetFileDescriptor(pipe[0], 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }


    // Unused ContentProvider methods
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
