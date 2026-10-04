package com.example.ui.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    viewModel: BrowserViewModel,
    onWebViewCreated: (WebView) -> Unit,
    onOpenFileChooser: ((ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Boolean)? = null,
    onDownloadTriggered: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val enabledScripts by viewModel.enabledScripts.collectAsState()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var defaultUserAgent by remember { mutableStateOf<String?>(null) }

    // Intercept back button if WebView can go back
    BackHandler(enabled = uiState.canGoBack) {
        webViewInstance?.let { wv ->
            if (wv.canGoBack()) {
                wv.goBack()
            }
        }
    }

    // React to User-Agent & Desktop Mode changes
    LaunchedEffect(uiState.userAgentType, uiState.isDesktopMode) {
        webViewInstance?.settings?.let { settings ->
            val customUa = uiState.userAgentType.userAgentString
            if (customUa != null) {
                if (defaultUserAgent == null) {
                    defaultUserAgent = settings.userAgentString
                }
                settings.userAgentString = customUa
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
            } else {
                defaultUserAgent?.let { settings.userAgentString = it }
            }
            if (uiState.currentUrl.isNotBlank() && !uiState.currentUrl.startsWith("about:")) {
                webViewInstance?.reload()
            }
        }
    }

    // Search query find in page
    LaunchedEffect(uiState.findQuery) {
        if (uiState.findQuery.isBlank()) {
            webViewInstance?.clearMatches()
        } else {
            webViewInstance?.findAllAsync(uiState.findQuery)
        }
    }

    // Handle Reader Mode Injection
    LaunchedEffect(uiState.isReaderMode) {
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
            webViewInstance?.evaluateJavascript(readerCss, null)
        } else {
            webViewInstance?.evaluateJavascript(
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
                        if (totalDragX > 160f && webViewInstance?.canGoBack() == true) {
                            webViewInstance?.goBack()
                        } else if (totalDragX < -160f && webViewInstance?.canGoForward() == true) {
                            webViewInstance?.goForward()
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
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // Chromium WebView configuration
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

                        cacheMode = if (uiState.isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                        mediaPlaybackRequiresUserGesture = false // Enables background media & autoplay

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            safeBrowsingEnabled = true
                        }
                    }

                    // Cookie manager configuration
                    val cookieManager = CookieManager.getInstance()
                    if (uiState.isIncognito) {
                        cookieManager.setAcceptCookie(false)
                        cookieManager.setAcceptThirdPartyCookies(this, false)
                    } else {
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                    }

                    // Attach JS Bridge for Media Sniffing & Console Capture
                    addJavascriptInterface(
                        IndowebJsBridge(
                            onMediaDetected = { url, title, mimeType ->
                                viewModel.onMediaDetected(url, title, mimeType)
                            },
                            onConsoleLog = { level, message ->
                                viewModel.recordJsConsole(level, message)
                            }
                        ),
                        "IndowebBridge"
                    )

                    // Attach custom clients
                    webViewClient = IndowebWebViewClient(
                        context = ctx,
                        viewModel = viewModel,
                        getEnabledScripts = { enabledScripts },
                        isAdBlockerEnabled = { true }
                    )

                    webChromeClient = IndowebWebChromeClient(
                        context = ctx,
                        viewModel = viewModel,
                        onFileChooser = onOpenFileChooser
                    )

                    // File Download Listener using Android DownloadManager
                    setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, contentLength ->
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

                            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                            dm?.enqueue(request)
                            Toast.makeText(ctx, "Starting download: $fileName", Toast.LENGTH_SHORT).show()
                            onDownloadTriggered?.invoke(fileName, downloadUrl)
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    defaultUserAgent = settings.userAgentString
                    webViewInstance = this
                    onWebViewCreated(this)
                }
            },
            update = { webView ->
                webViewInstance = webView
            }
        )

        // Loading indicator
        AnimatedVisibility(
            visible = uiState.isLoading,
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
            webViewInstance?.apply {
                stopLoading()
                clearMatches()
            }
        }
    }
}
