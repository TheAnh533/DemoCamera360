package com.example.camera360

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.android.play.core.ktx.requestProgressFlow
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HdrDynamicFeatureHelper(private val context: Context) {
    private val splitInstallManager = SplitInstallManagerFactory.create(context)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    @RequiresApi(Build.VERSION_CODES.O)
    fun loadHdrAssets(callback: (List<String>) -> Unit) {
        coroutineScope.launch {
            try {
                if (splitInstallManager.installedModules.contains("hdr_assets")) {
                    val hdrFiles = getHdrFilesFromDynamicModule()
                    if (hdrFiles.isNotEmpty()) {
                        callback(hdrFiles)
                        return@launch
                    }
                }

                // If not installed or no files found, download the module
                downloadHdrModule(callback)
            } catch (e: Exception) {
                Log.e("HdrDynamicFeatureHelper", "Error loading HDR assets", e)
                callback(emptyList())
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun downloadHdrModule(callback: (List<String>) -> Unit) {
        try {
            val request = SplitInstallRequest.newBuilder()
                .addModule("hdr_assets")
                .build()

            splitInstallManager.requestProgressFlow().collect { state ->
                when (state.status()) {
                    SplitInstallSessionStatus.DOWNLOADING -> {
                        val progress =
                            (100.0 * state.bytesDownloaded() / state.totalBytesToDownload()).toInt()
                        Log.d("HdrDynamicFeatureHelper", "Downloading: $progress%")
                    }

                    SplitInstallSessionStatus.INSTALLED -> {
                        Log.d("HdrDynamicFeatureHelper", "Module installed successfully")
                        val hdrFiles = getHdrFilesFromDynamicModule()
                        callback(hdrFiles)
                    }

                    SplitInstallSessionStatus.FAILED -> {
                        Log.e(
                            "HdrDynamicFeatureHelper",
                            "Installation failed: ${state.errorCode()}"
                        )
                        callback(emptyList())
                    }

                    SplitInstallSessionStatus.CANCELED -> {
                        Log.e("HdrDynamicFeatureHelper", "Installation canceled")
                        callback(emptyList())
                    }

                    else -> {}
                }
            }
        } catch (e: Exception) {
            Log.e("HdrDynamicFeatureHelper", "Error during module installation", e)
            callback(emptyList())
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getHdrFilesFromDynamicModule(): List<String> {
        return try {
            // Create a context for the dynamic feature module
            val featureContext = context.createPackageContext(
                context.packageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            ).createContextForSplit("hdr_assets")

            val assetManager = featureContext.assets
            val hdrFiles = mutableListOf<String>()

            // List HDR files from the assets
            assetManager.list("hdri_4k")?.let { files ->
                files.filter { it.endsWith(".hdr", ignoreCase = true) }.forEach { file ->
                    // Use our content provider to access the files
                    val authority = "${context.packageName}.hdrprovider"
                    hdrFiles.add("content://$authority/hdri_4k/$file")
                }
            }

            Log.d("HdrDynamicFeatureHelper", "Found ${hdrFiles.size} HDR files in dynamic module")
            hdrFiles
        } catch (e: Exception) {
            Log.e("HdrDynamicFeatureHelper", "Error accessing dynamic module assets", e)
            emptyList()
        }
    }

    companion object {
        @Volatile
        private var instance: HdrDynamicFeatureHelper? = null

        fun getInstance(context: Context): HdrDynamicFeatureHelper {
            return instance ?: synchronized(this) {
                instance ?: HdrDynamicFeatureHelper(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
