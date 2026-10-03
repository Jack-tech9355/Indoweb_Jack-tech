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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

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

    // React to Desktop Mode toggle
    LaunchedEffect(uiState.isDesktopMode) {
        webViewInstance?.settings?.let { settings ->
            if (uiState.isDesktopMode) {
                if (defaultUserAgent == null) {
                    defaultUserAgent = settings.userAgentString
                }
                settings.userAgentString = DESKTOP_USER_AGENT
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

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Required Chromium WebView settings per specification
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

                    cacheMode = WebSettings.LOAD_DEFAULT
                    mediaPlaybackRequiresUserGesture = false

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = true
                    }
                }

                // Cookie manager configuration
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

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

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.apply {
                stopLoading()
                clearMatches()
            }
        }
    }
}
