package com.example.hdr_assets

import android.content.Context
import android.util.Log
import com.google.android.play.core.ktx.requestDeferredInstall
import com.google.android.play.core.ktx.requestProgressFlow
import com.google.android.play.core.splitinstall.*
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

class HdrAssetManager(private val context: Context) {
    private val splitInstallManager = SplitInstallManagerFactory.create(context)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun getHdrFiles(callback: Any) {
        coroutineScope.launch {
            try {
                // Get the callback method using reflection
                val callbackMethod = callback.javaClass.declaredFields
                    .firstOrNull { it.name == "callback" }
                    ?.apply { isAccessible = true }
                    ?.get(callback) as? ((List<String>) -> Unit)
                
                if (callbackMethod == null) {
                    Log.e("HdrAssetManager", "Invalid callback object")
                    return@launch
                }
                
                // Check if the feature is already installed
                if (splitInstallManager.installedModules.contains("hdr_assets")) {
                    val hdrFiles = getHdrFilesFromAssets()
                    if (hdrFiles.isNotEmpty()) {
                        callbackMethod(hdrFiles)
                        return@launch
                    }
                }
                
                // If not installed or no files found, download the module
                downloadHdrModule(callbackMethod)
            } catch (e: Exception) {
                Log.e("HdrAssetManager", "Error getting HDR files", e)
                (callback as? ((List<String>) -> Unit))?.invoke(emptyList())
            }
        }
    }

    private suspend fun downloadHdrModule(callback: (List<String>) -> Unit) {
        try {
            val request = SplitInstallRequest.newBuilder()
                .addModule("hdr_assets")
                .build()

            splitInstallManager.startInstall(request)
            
            splitInstallManager.registerListener { state ->
                when (state.status()) {
                    SplitInstallSessionStatus.DOWNLOADING -> {
                        val progress = (100.0 * state.bytesDownloaded() / state.totalBytesToDownload()).toInt()
                        Log.d("HdrAssetManager", "Downloading: $progress%")
                    }
                    SplitInstallSessionStatus.INSTALLED -> {
                        Log.d("HdrAssetManager", "Module installed successfully")
                        val hdrFiles = getHdrFilesFromAssets()
                        callback(hdrFiles)
                    }
                    SplitInstallSessionStatus.FAILED -> {
                        Log.e("HdrAssetManager", "Installation failed: ${state.errorCode()}")
                        callback(emptyList())
                    }
                    SplitInstallSessionStatus.CANCELED -> {
                        Log.e("HdrAssetManager", "Installation canceled")
                        callback(emptyList())
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            Log.e("HdrAssetManager", "Error during module installation", e)
            callback(emptyList())
        }
    }

    private fun getHdrFilesFromAssets(): List<String> {
        return try {
            val assets = context.assets
            val hdrFiles = mutableListOf<String>()
            
            assets.list("hdri_4k")?.forEach { file ->
                if (file.endsWith(".hdr", ignoreCase = true)) {
                    // Use the content provider to access files
                    val authority = "${context.packageName}.hdr_assets"
                    hdrFiles.add("content://$authority/hdri_4k/$file")
                }
            }
            
            hdrFiles
        } catch (e: Exception) {
            Log.e("HdrAssetManager", "Error reading HDR files from assets", e)
            emptyList()
        }
    }

    companion object {
        @Volatile
        private var instance: HdrAssetManager? = null

        fun getInstance(context: Context): HdrAssetManager {
            return instance ?: synchronized(this) {
                instance ?: HdrAssetManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
