package com.example.ui.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.model.UserScript
import com.example.ui.scripts.UserScriptEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

class IndowebWebViewClient(
    private val context: Context,
    private val viewModel: BrowserViewModel,
    private val getEnabledScripts: () -> List<UserScript>,
    private val isAdBlockerEnabled: () -> Boolean = { true }
) : WebViewClient() {

    private val scope = CoroutineScope(Dispatchers.Main)

    // Comprehensive list of ad, tracking, and analytics domains
    private val adDomainFilters = listOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "adservice.google.",
        "adnxs.com",
        "criteo.com",
        "taboola.com",
        "outbrain.com",
        "amazon-adsystem.com",
        "popads.net",
        "scorecardresearch.com",
        "advertising.com",
        "quantserve.com",
        "moatads.com",
        "bidswitch.net",
        "rubiconproject.com",
        "pubmatic.com",
        "casalemedia.com",
        "google-analytics.com",
        "googletagmanager.com",
        "analytics.twitter.com",
        "connect.facebook.net",
        "hotjar.com",
        "segment.io",
        "amplitude.com",
        "adjust.com",
        "appsflyer.com",
        "pagead2.googlesyndication.com",
        "adroll.com",
        "adsystem.com"
    )

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        if (request != null) {
            val urlString = request.url.toString()
            val urlLower = urlString.lowercase()

            // 1. Ad & Tracker Interception
            if (isAdBlockerEnabled()) {
                for (filter in adDomainFilters) {
                    if (urlLower.contains(filter)) {
                        viewModel.recordAdBlocked()
                        return WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                }
            }

            // 2. Network-Level Media Sniffing (.mp4, .m3u8, .mp3, .webm, .m4a)
            val path = request.url.path?.lowercase() ?: ""
            if (path.endsWith(".mp4") || path.endsWith(".m3u8") || path.endsWith(".mp3") ||
                path.endsWith(".webm") || path.endsWith(".m4a") ||
                urlLower.contains(".m3u8?") || urlLower.contains(".mp4?")
            ) {
                val mimeType = when {
                    path.endsWith(".m3u8") || urlLower.contains(".m3u8") -> "application/x-mpegurl"
                    path.endsWith(".mp3") -> "audio/mpeg"
                    path.endsWith(".m4a") -> "audio/mp4"
                    path.endsWith(".webm") -> "video/webm"
                    else -> "video/mp4"
                }

                val title = when {
                    mimeType == "application/x-mpegurl" -> "HLS Video Stream (.m3u8)"
                    mimeType.startsWith("audio/") -> "Audio Track (${request.url.lastPathSegment ?: "stream"})"
                    else -> "Video Stream (${request.url.lastPathSegment ?: "video"})"
                }

                view?.post {
                    viewModel.onMediaDetected(urlString, title, mimeType)
                }
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        val url = uri.toString()

        // Intercept GreasyFork / Tampermonkey .user.js script clicks
        if (isUserScriptUrl(url)) {
            viewModel.fetchAndPromptInstallUserScript(url)
            return true
        }

        // Standard web schemes handled by WebView
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://") || url.startsWith("about:")) {
            return false
        }

        // Handle external protocols (tel, mailto, intent, market, etc.)
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        } catch (_: Exception) {
            return true
        }
    }

    private fun isUserScriptUrl(url: String): Boolean {
        val clean = url.split("?").first().lowercase()
        return clean.endsWith(".user.js") ||
                (url.contains("greasyfork.org/scripts/") && url.contains("/code/")) ||
                url.contains("openuserjs.org/install/")
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url == null) return

        viewModel.onPageStarted(url)
        viewModel.checkBookmarkStatus(url)

        if (view != null && !url.startsWith("about:") && !url.startsWith("data:")) {
            injectCoreInfrastructure(view)
            injectCosmeticAdBlockFilter(view)
            injectScriptsByStage(view, url, "document_start")
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view == null || url == null) return

        viewModel.onPageFinished(
            url = url,
            title = view.title,
            canBack = view.canGoBack(),
            canForward = view.canGoForward()
        )

        if (url.startsWith("about:") || url.startsWith("data:")) return

        injectCosmeticAdBlockFilter(view)
        injectMediaSniffer(view)
        injectScriptsByStage(view, url, "document_end")
    }

    private fun injectCoreInfrastructure(webView: WebView) {
        val coreJs = """
            (function() {
                if (window._indowebHooked) return;
                window._indowebHooked = true;
                
                // Hook Console
                const origLog = console.log;
                const origWarn = console.warn;
                const origError = console.error;
                
                console.log = function() {
                    origLog.apply(console, arguments);
                    try {
                        const msg = Array.from(arguments).map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' ');
                        if (window.IndowebBridge) window.IndowebBridge.log('LOG', msg);
                    } catch(e) {}
                };
                
                console.warn = function() {
                    origWarn.apply(console, arguments);
                    try {
                        const msg = Array.from(arguments).map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' ');
                        if (window.IndowebBridge) window.IndowebBridge.log('WARN', msg);
                    } catch(e) {}
                };
                
                console.error = function() {
                    origError.apply(console, arguments);
                    try {
                        const msg = Array.from(arguments).map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' ');
                        if (window.IndowebBridge) window.IndowebBridge.log('ERROR', msg);
                    } catch(e) {}
                };
            })();
        """.trimIndent()
        webView.evaluateJavascript(coreJs, null)
    }

    private fun injectCosmeticAdBlockFilter(webView: WebView) {
        if (!isAdBlockerEnabled()) return
        val cosmeticCss = """
            (function() {
                const styleId = 'indoweb-adblock-cosmetic';
                if (document.getElementById(styleId)) return;
                const style = document.createElement('style');
                style.id = styleId;
                style.textContent = `
                    iframe[src*="doubleclick"], iframe[src*="adnxs"], iframe[src*="adservice"],
                    .adsbygoogle, .ad-banner, .advertisement, [id*="google_ads"], [id*="banner-ad"],
                    [class*="sponsored-post"], [class*="ad-container"], [data-ad-client], .ad-slot,
                    .ad-unit, .commercial-unit, #advert, .ad_box, .banner_ad, [id*="ad_holder"],
                    [class*="dfp-ad"], [data-adunit], .taboola-placeholder, .outbrain-placeholder {
                        display: none !important;
                        visibility: hidden !important;
                        height: 0 !important;
                        max-height: 0 !important;
                        overflow: hidden !important;
                        pointer-events: none !important;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);
            })();
        """.trimIndent()
        webView.evaluateJavascript(cosmeticCss, null)
    }

    private fun injectMediaSniffer(webView: WebView) {
        val snifferJs = """
            (function() {
                function scanMedia() {
                    document.querySelectorAll('video, audio').forEach(el => {
                        const src = el.src || el.currentSrc || (el.querySelector('source') && el.querySelector('source').src);
                        if (src && !src.startsWith('blob:') && !el.dataset.sniffed) {
                            el.dataset.sniffed = 'true';
                            if (window.IndowebBridge) {
                                window.IndowebBridge.onMediaFound(src, document.title || 'Web Video', el.tagName.toLowerCase());
                            }
                        }
                    });
                }
                scanMedia();
                setInterval(scanMedia, 2500);
            })();
        """.trimIndent()
        webView.evaluateJavascript(snifferJs, null)
    }

    private fun injectScriptsByStage(webView: WebView, currentUrl: String, stage: String) {
        val enabledScripts = getEnabledScripts()
        val matchingScripts = enabledScripts.filter { script ->
            val scriptStage = if (script.runAt.isBlank()) "document_end" else script.runAt
            scriptStage.equals(stage, ignoreCase = true) && script.matchesUrl(currentUrl)
        }

        if (stage == "document_end") {
            val totalMatching = enabledScripts.count { it.matchesUrl(currentUrl) }
            viewModel.setInjectedCount(totalMatching)
        }

        scope.launch {
            for (script in matchingScripts) {
                // Fetch/retrieve pre-cached @require dependency libraries (e.g. jQuery, Lodash)
                val preloadedRequires = viewModel.dependencyManager.resolveDependencies(script.requires)
                val wrappedCode = UserScriptEngine.wrapScript(script, stage, preloadedRequires)
                webView.evaluateJavascript(wrappedCode) { result ->
                    viewModel.recordScriptLog(
                        scriptName = "${script.name} [$stage]",
                        url = currentUrl,
                        isSuccess = true,
                        message = "Injected: ${result?.take(60) ?: "OK"}"
                    )
                }
            }
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            val failingUrl = request.url.toString()
            val desc = error?.description ?: "Failed to load page"
            viewModel.recordScriptLog(
                scriptName = "Network",
                url = failingUrl,
                isSuccess = false,
                message = "Error: $desc"
            )
        }
    }
}
