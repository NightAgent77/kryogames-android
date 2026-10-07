package com.kryogames.app

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import java.io.File

/** Local HTML/JS games run on an HTTPS asset origin; no file:// permissions. */
class WebGameActivity : ControllerActivity() {
    override val useLibraryShortcuts = false
    private val gamepad = WebGamepadBridge()
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val directory = intent.getStringExtra(EXTRA_DIRECTORY)
        val tree = intent.getStringExtra(EXTRA_TREE)
        val entry = intent.getStringExtra(EXTRA_ENTRY)
        fun localUrl(relative: String) =
            "https://appassets.androidplatform.net/local/${relative.split("/").joinToString("/") { Uri.encode(it) }}"
        val loader = if (directory != null) {
            val root = File(directory)
            val relative = entry?.let(::normalizeRequestPath)
            if (relative == null || !root.isDirectory) {
                finish(); return
            }
            WebViewAssetLoader.Builder()
                .addPathHandler("/local/", DirectoryPathHandler(root))
                .build() to localUrl(relative)
        } else if (tree != null) {
            val relative = entry?.let(::normalizeRequestPath)
            if (relative == null) {
                finish(); return
            }
            WebViewAssetLoader.Builder()
                .addPathHandler("/local/", TreePathHandler(this, Uri.parse(tree)))
                .build() to localUrl(relative)
        } else {
            val path = intent.getStringExtra(EXTRA_ASSET) ?: ""
            if (!path.matches(Regex("[A-Za-z0-9_./-]+")) || path.startsWith("/") || path.contains("..")) {
                finish(); return
            }
            WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
                .build() to "https://appassets.androidplatform.net/assets/$path"
        }
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(false)
            settings.mediaPlaybackRequiresUserGesture = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            isFocusable = true
            isFocusableInTouchMode = true
            addJavascriptInterface(gamepad, "KryoPad")
            setBackgroundColor(0xFF101417.toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val intercepted = loader.first.shouldInterceptRequest(request.url) ?: return null
                    val urlPath = request.url.path.orEmpty()
                    val mime = when {
                        urlPath.endsWith(".js") || urlPath.endsWith(".mjs") -> "application/javascript"
                        urlPath.endsWith(".wasm") -> "application/wasm"
                        urlPath.endsWith(".html") -> "text/html"
                        else -> intercepted.mimeType
                    }
                    if (mime == intercepted.mimeType || intercepted.data == null || intercepted.statusCode == 0) {
                        return intercepted
                    }
                    return WebResourceResponse(
                        mime,
                        intercepted.encoding,
                        intercepted.statusCode,
                        intercepted.reasonPhrase,
                        intercepted.responseHeaders,
                        intercepted.data,
                    )
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.scheme != "https" || request.url.host != "appassets.androidplatform.net"
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    view.evaluateJavascript(WEB_GAMEPAD_BOOT, null)
                }
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(WEB_GAMEPAD_BOOT, null)
                    view.requestFocus()
                }
            }
        }
        setContentView(webView)
        webView.loadUrl(loader.second)
        webView.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        gamepad.onKey(event)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        gamepad.onMotion(event)
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onPause() {
        gamepad.clear()
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }
    override fun onResume() { super.onResume(); if (::webView.isInitialized) webView.onResume() }
    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("KryoPad")
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
    companion object {
        const val EXTRA_ASSET = "asset_path"
        const val EXTRA_TREE = "content_tree"
        const val EXTRA_DIRECTORY = "content_directory"
        const val EXTRA_ENTRY = "content_entry"
        const val EXTRA_TITLE = "game_title"
    }
}
