package com.example.camera360

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.*
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.camera360.databinding.ActivityMainBinding
import org.json.JSONArray

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    // JavaScript interface để debug từ WebView
    inner class WebAppInterface(private val mContext: Context) {
        @JavascriptInterface
        fun showToast(toast: String) {
            Log.d("WebAppInterface", "Toast from WebView: $toast")
            Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show()
        }

        @JavascriptInterface
        fun log(message: String) {
            Log.d("WebAppInterface", "Log from WebView: $message")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupWebView()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                allowUniversalAccessFromFileURLs = true
                cacheMode = WebSettings.LOAD_DEFAULT
                WebView.setWebContentsDebuggingEnabled(true)
            }

            addJavascriptInterface(WebAppInterface(context), "Android")

            webViewClient = object : WebViewClient() {
                // Khi WebView load xong -> inject HDR list
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("WebViewClient", "Page finished: $url")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        loadHDRsFromDynamicModule()
                    } else {
                        loadLocalHdrAssets()
                    }
                }

                // Cho phép WebView load content://
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val uri = request?.url ?: return null
                    if (uri.scheme == "content") {
                        return try {
                            val input = contentResolver.openInputStream(uri)
                            WebResourceResponse("image/vnd.radiance", null, input)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error opening content:// $uri", e)
                            null
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    Log.d(
                        "WebView",
                        "${consoleMessage.messageLevel()}: ${consoleMessage.message()} @${consoleMessage.lineNumber()}"
                    )
                    return true
                }
            }

            // Load file index.html trong assets
            loadUrl("file:///android_asset/index.html")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadHDRsFromDynamicModule() {
        HdrDynamicFeatureHelper.getInstance(this).loadHdrAssets { hdrFiles ->
            Log.d("MainActivity", "HDR files from dynamic module: ${hdrFiles.size}")
            if (hdrFiles.isNotEmpty()) {
                sendHdrListToWebView(hdrFiles)
            } else {
                loadLocalHdrAssets()
            }
        }
    }

    private fun loadLocalHdrAssets() {
        val hdrFiles = mutableListOf<String>()
        assets.list("hdri_4k")?.forEach { file ->
            if (file.endsWith(".hdr", true)) {
                hdrFiles.add("file:///android_asset/hdri_4k/$file")
            }
        }

        if (hdrFiles.isNotEmpty()) {
            sendHdrListToWebView(hdrFiles)
        } else {
            Log.e("MainActivity", "No HDRs in local assets")
        }
    }

    private fun sendHdrListToWebView(hdrFiles: List<String>) {
        runOnUiThread {
            val json = JSONArray(hdrFiles).toString()
            val js = "window.setHDRList($json);"
            Log.d("MainActivity", "Injecting HDR list to WebView: $json")
            binding.webView.evaluateJavascript(js, null)
        }
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
