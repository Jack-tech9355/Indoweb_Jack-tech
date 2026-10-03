package com.example.ui.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.model.UserScript
import java.io.ByteArrayInputStream

class IndowebWebViewClient(
    private val context: Context,
    private val viewModel: BrowserViewModel,
    private val getEnabledScripts: () -> List<UserScript>,
    private val isAdBlockerEnabled: () -> Boolean = { true }
) : WebViewClient() {

    // Known ad & tracker domain patterns for network level blocking
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
        "casalemedia.com"
    )

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        if (request != null && isAdBlockerEnabled()) {
            val urlString = request.url.toString().lowercase()
            for (filter in adDomainFilters) {
                if (urlString.contains(filter)) {
                    // Block ad request by returning empty data stream
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        val url = uri.toString()

        // Handle standard web schemes
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://") || url.startsWith("about:")) {
            return false // Let WebView load it
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

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url == null) return

        viewModel.onPageStarted(url)
        viewModel.checkBookmarkStatus(url)

        if (view != null && !url.startsWith("about:") && !url.startsWith("data:")) {
            // Inject console logger and media sniffer early
            injectCoreInfrastructure(view)
            // Inject document_start scripts early
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

        // Inject media observer
        injectMediaSniffer(view)
        // Inject document_end scripts after page load completes
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

        for (script in matchingScripts) {
            val wrappedCode = buildInjectedScript(script, stage)
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

    private fun buildInjectedScript(script: UserScript, stage: String): String {
        val escapedName = script.name.replace("'", "\\'")
        return """
            (function() {
                try {
                    console.log('[Indoweb] Executing (${stage}): ${escapedName}');
                    ${script.scriptCode}
                    return 'OK: ${escapedName}';
                } catch (err) {
                    console.error('[Indoweb] Error in ${escapedName}:', err);
                    return 'ERROR: ' + err.message;
                }
            })();
        """.trimIndent()
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
