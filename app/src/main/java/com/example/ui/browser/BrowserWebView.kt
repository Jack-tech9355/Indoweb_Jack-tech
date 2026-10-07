package com.example.ui.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    viewModel: BrowserViewModel,
    onActiveWebViewChanged: (WebView) -> Unit,
    onOpenFileChooser: ((ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Boolean)? = null,
    onShowFullscreen: ((View, WebChromeClient.CustomViewCallback) -> Unit)? = null,
    onHideFullscreen: (() -> Unit)? = null,
    onDownloadTriggered: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val enabledScripts by viewModel.enabledScripts.collectAsState()

    val webViewPool = remember { mutableMapOf<String, WebView>() }
    var activeWebViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Intercept back button if active WebView can go back
    BackHandler(enabled = uiState.canGoBack) {
        activeWebViewInstance?.let { wv ->
            if (wv.canGoBack()) {
                wv.goBack()
            }
        }
    }

    // React to User-Agent & Desktop Mode changes on active WebView
    LaunchedEffect(uiState.userAgentType, uiState.isDesktopMode, activeTabId) {
        activeWebViewInstance?.settings?.let { settings ->
            val customUa = uiState.userAgentType.userAgentString
            if (customUa != null) {
                settings.userAgentString = customUa
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
            } else {
                settings.userAgentString = null // Reset to default WebView UA
            }
        }
    }

    // Search query find in page
    LaunchedEffect(uiState.findQuery) {
        if (uiState.findQuery.isBlank()) {
            activeWebViewInstance?.clearMatches()
        } else {
            activeWebViewInstance?.findAllAsync(uiState.findQuery)
        }
    }

    // Handle Reader Mode Injection
    LaunchedEffect(uiState.isReaderMode, activeTabId) {
        if (uiState.isReaderMode) {
            val readerCss = """
                (function() {
                    const styleId = 'indoweb-reader-view-style';
                    if (document.getElementById(styleId)) return;
                    const style = document.createElement('style');
                    style.id = styleId;
                    style.innerHTML = `
                        header, footer, nav, aside, .sidebar, .ads, .ad, [class*="banner"], [id*="comment"], [class*="social"], iframe, [role="complementary"] {
                            display: none !important;
                        }
                        body {
                            background-color: #1a1a1a !important;
                            color: #e5e7eb !important;
                            font-family: Georgia, serif !important;
                            font-size: 20px !important;
                            line-height: 1.8 !important;
                            max-width: 740px !important;
                            margin: 0 auto !important;
                            padding: 24px 16px !important;
                        }
                        p, h1, h2, h3, h4, h5, h6, blockquote, article {
                            color: #f3f4f6 !important;
                        }
                        img {
                            max-width: 100% !important;
                            height: auto !important;
                            border-radius: 8px !important;
                            margin: 16px auto !important;
                            display: block !important;
                        }
                    `;
                    document.head.appendChild(style);
                })();
            """.trimIndent()
            activeWebViewInstance?.evaluateJavascript(readerCss, null)
        } else {
            activeWebViewInstance?.evaluateJavascript(
                "const s = document.getElementById('indoweb-reader-view-style'); if(s) s.remove();",
                null
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var totalDragX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDragX = 0f },
                    onDragEnd = {
                        if (totalDragX > 160f && activeWebViewInstance?.canGoBack() == true) {
                            activeWebViewInstance?.goBack()
                        } else if (totalDragX < -160f && activeWebViewInstance?.canGoForward() == true) {
                            activeWebViewInstance?.goForward()
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDragX += dragAmount
                    }
                )
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { container ->
                // 1. Clean up closed tabs from pool to avoid memory leaks
                val currentTabIds = tabs.map { it.id }.toSet()
                val closedIds = webViewPool.keys.filter { it !in currentTabIds }
                for (id in closedIds) {
                    val deadWv = webViewPool.remove(id)
                    deadWv?.apply {
                        stopLoading()
                        loadUrl("about:blank")
                        onPause()
                        clearHistory()
                        container.removeView(this)
                        destroy()
                    }
                }

                // 2. Instantiate or synchronize WebViews for active & background tabs
                for (tab in tabs) {
                    var wv = webViewPool[tab.id]
                    if (wv == null) {
                        wv = WebView(container.context).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            // Chromium WebView Settings
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                cacheMode = if (tab.isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                                mediaPlaybackRequiresUserGesture = false // Keeps background media & audio alive
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    safeBrowsingEnabled = true
                                }
                            }

                            // Cookie isolation
                            val cookieManager = CookieManager.getInstance()
                            if (tab.isIncognito) {
                                cookieManager.setAcceptCookie(false)
                                cookieManager.setAcceptThirdPartyCookies(this, false)
                            } else {
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)
                            }

                            // JS Bridge for media sniffer, console logs & native OkHttp CORS bypass
                            addJavascriptInterface(
                                IndowebJsBridge(
                                    webViewProvider = { this },
                                    onMediaDetected = { url, title, mime ->
                                        viewModel.onMediaDetected(url, title, mime)
                                    },
                                    onConsoleLog = { level, msg ->
                                        viewModel.recordJsConsole(level, msg)
                                    }
                                ),
                                "IndowebBridge"
                            )

                            webViewClient = IndowebWebViewClient(
                                context = container.context,
                                viewModel = viewModel,
                                getEnabledScripts = { enabledScripts },
                                isAdBlockerEnabled = { uiState.isAdBlockerEnabled }
                            )

                            webChromeClient = IndowebWebChromeClient(
                                context = container.context,
                                viewModel = viewModel,
                                onFileChooser = onOpenFileChooser,
                                onShowFullscreen = onShowFullscreen,
                                onHideFullscreen = onHideFullscreen
                            )

                            // File Download Listener
                            setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, _ ->
                                try {
                                    val fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
                                    val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                                        setMimeType(mimetype)
                                        val cookies = CookieManager.getInstance().getCookie(downloadUrl)
                                        addRequestHeader("cookie", cookies)
                                        addRequestHeader("User-Agent", userAgent)
                                        setDescription("Downloading file via Indoweb...")
                                        setTitle(fileName)
                                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                                    }
                                    val dm = container.context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                                    dm?.enqueue(request)
                                    Toast.makeText(container.context, "Starting download: $fileName", Toast.LENGTH_SHORT).show()
                                    onDownloadTriggered?.invoke(fileName, downloadUrl)
                                } catch (e: Exception) {
                                    Toast.makeText(container.context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }

                            if (tab.url.isNotBlank() && tab.url != "about:blank") {
                                loadUrl(tab.url)
                            }
                        }

                        webViewPool[tab.id] = wv
                        container.addView(wv)
                    }

                    // 3. Tab Visibility Switching: PRESERVES DOM, VIDEO, JS, SCROLL & HISTORY
                    if (tab.id == activeTabId) {
                        wv.visibility = View.VISIBLE
                        wv.bringToFront()
                        activeWebViewInstance = wv
                        onActiveWebViewChanged(wv)

                        // If tab URL changed externally and hasn't loaded yet
                        if (tab.url.isNotBlank() && tab.url != "about:blank" && wv.url != tab.url && !tab.isLoading) {
                            wv.loadUrl(tab.url)
                        }
                    } else {
                        // Background tabs stay alive in memory without destroying state
                        wv.visibility = View.GONE
                    }
                }
            }
        )

        // Loading indicator
        AnimatedVisibility(
            visible = uiState.isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            LinearProgressIndicator(
                progress = { uiState.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewPool.values.forEach { wv ->
                wv.stopLoading()
                wv.clearMatches()
                wv.destroy()
            }
            webViewPool.clear()
        }
    }
}
