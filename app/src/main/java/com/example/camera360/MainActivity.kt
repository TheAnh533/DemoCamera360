package com.example.camera360

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // JavaScript interface to expose Android methods to WebView
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
        
        // Create a root layout
        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }
        
        // Initialize WebView with proper layout parameters
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            
            // Enable hardware acceleration
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            // Configure WebView settings
            settings.apply {
                // Basic settings
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                
                // Viewport settings
                useWideViewPort = true
                loadWithOverviewMode = true
                
                // Zoom settings
                setSupportZoom(true)
                builtInZoomControls = false
                displayZoomControls = false
                
                // File and content access
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                
                // Performance settings
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                
                // Enable WebGL and hardware acceleration
                setGeolocationEnabled(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                
                // Text and encoding
                defaultTextEncodingName = "utf-8"
                loadsImagesAutomatically = true
                
                // Enable debugging in WebView (only in debug builds)
                    WebView.setWebContentsDebuggingEnabled(true)

            
            }
            
            // Add JavaScript interface
            addJavascriptInterface(WebAppInterface(context), "Android")
            
            // Configure WebView client
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    Log.d("MainActivity", "Page finished loading: $url")
                    
                    // Inject viewport meta tag and other initialization JavaScript
                    view.evaluateJavascript("""
                        (function() {
                            // Set viewport meta tag
                            var meta = document.createElement('meta');
                            meta.name = 'viewport';
                            meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
                            var head = document.getElementsByTagName('head')[0];
                            if (head) {
                                head.appendChild(meta);
                            }
                            
                            // Force layout update
                            document.body.style.overflow = 'hidden';
                            document.documentElement.style.overflow = 'hidden';
                            
                            // Notify Android that the page is ready
                            if (window.Android) {
                                Android.log('WebView layout initialized');
                            }
                        })();
                    """.trimIndent(), null)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    super.onReceivedError(view, request, error)
                    Log.e("MainActivity", "WebView error loading ${request.url}: ${error.description}")
                }
                
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    // Handle any URL loading if needed
                    return false
                }
            }
            
            // Configure Chrome client for console logging
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    val message = "${consoleMessage.messageLevel()}: ${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}"
                    when (consoleMessage.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> Log.e("WebView", message)
                        ConsoleMessage.MessageLevel.WARNING -> Log.w("WebView", message)
                        else -> Log.d("WebView", message)
                    }
                    return true
                }
            }
            
            // Configure scroll behavior
            isScrollbarFadingEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            overScrollMode = View.OVER_SCROLL_NEVER
            
            // Disable long click (prevents text selection popup)
            setOnLongClickListener { true }
        }
        
        // Add WebView to root layout
        rootLayout.addView(webView)
        
        // Set the root layout as content view
        setContentView(rootLayout)
        
        // Load the initial URL
        webView.loadUrl("file:///android_asset/index.html")
        
        // Load the main page
        webView.loadUrl("file:///android_asset/index.html")
        
        // Start loading HDR assets
        loadHDRsFromAssets()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadHDRsFromAssets() {
        runOnUiThread {
            // First, check if the WebView is ready
            val checkReadyJs = """
                if (window.setHDRList) {
                    Android.log('WebView is ready, setHDRList exists');
                    true;
                } else {
                    Android.log('WebView is not ready yet, setHDRList not found');
                    false;
                }
            """.trimIndent()
            
            webView.evaluateJavascript(checkReadyJs) { isReady ->
                if (isReady == "true") {
                    // WebView is ready, proceed with loading HDRs
                    loadHDRsFromDynamicModule()
                } else {
                    // WebView is not ready yet, wait a bit and try again
                    Log.d("MainActivity", "WebView not ready, retrying in 500ms")
                    webView.postDelayed({
                        loadHDRsFromAssets()
                    }, 500)
                }
            }
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadHDRsFromDynamicModule() {
        try {
            // Try to load from dynamic feature module first
            HdrDynamicFeatureHelper.getInstance(this).loadHdrAssets { hdrFiles ->
                Log.d("MainActivity", "Received ${hdrFiles.size} HDR files from dynamic feature")
                if (hdrFiles.isNotEmpty()) {
                    // Convert to JSON and properly escape the string
                    val jsonArray = JSONArray(hdrFiles)
                    val jsonString = jsonArray.toString()
                    Log.d("MainActivity", "HDR files JSON: $jsonString")
                    
                    // Call the WebView to process the HDR files
                    processHdrFilesInWebView(jsonString)
                } else {
                    // Fallback to local assets if dynamic feature is not available
                    Log.d("MainActivity", "No HDR files found in dynamic feature, trying local assets")
                    loadLocalHdrAssets()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading HDRs from dynamic module", e)
            loadLocalHdrAssets()
        }
    }

    private fun processHdrFilesInWebView(jsonString: String) {
        runOnUiThread {
            // First, log the HDR list to the WebView console
            webView.evaluateJavascript("""
                console.log('Processing HDR files in WebView:', $jsonString);
                JSON.stringify($jsonString);
            """.trimIndent()) { _ -> 
                // Then call the setHDRList function
                val jsCode = """
                    try {
                        const hdrList = $jsonString;
                        if (window.setHDRList) {
                            window.setHDRList(hdrList);
                            Android.log('Successfully set HDR list with ' + hdrList.length + ' items');
                            true;
                        } else {
                            Android.log('Error: setHDRList function not found in WebView');
                            false;
                        }
                    } catch (e) {
                        Android.log('Error in setHDRList: ' + e.message);
                        false;
                    }
                """.trimIndent()
                
                webView.evaluateJavascript(jsCode) { result ->
                    Log.d("MainActivity", "setHDRList execution result: $result")
                    if (result != "true") {
                        Log.d("MainActivity", "setHDRList failed, trying fallback")
                        loadLocalHdrAssets()
                    }
                }
            }
        }
    }

    private fun loadLocalHdrAssets() {
        runOnUiThread {
            try {
                val hdrFiles = mutableListOf<String>()
                assets.list("hdri_4k")?.let { files ->
                    files.filter { it.endsWith(".hdr", ignoreCase = true) }
                        .map { "file:///android_asset/hdri_4k/$it" }
                        .toCollection(hdrFiles)
                }

                if (hdrFiles.isNotEmpty()) {
                    val jsonArray = JSONArray(hdrFiles)
                    val jsonString = jsonArray.toString()
                    Log.d("MainActivity", "Local HDR files JSON: $jsonString")
                    processHdrFilesInWebView(jsonString)
                } else {
                    Log.e("MainActivity", "No HDR files found in local assets")
                    loadFallbackHDRs()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading local HDR assets", e)
                loadFallbackHDRs()
            }
        }
    }

    private fun loadFallbackHDRs() {
        runOnUiThread {
            val fallbackHdr = "file:///android_asset/hdri_4k/ex.hdr"
            val jsonArray = JSONArray(listOf(fallbackHdr))
            val jsonString = jsonArray.toString()
            Log.d("MainActivity", "Loading fallback HDR: $jsonString")
            
            // Process the fallback HDR files in the WebView
            processHdrFilesInWebView(jsonString)
        }
    }

    override fun onDestroy() {
        webView.destroy() // Clean up WebView
        super.onDestroy()
    }
}
