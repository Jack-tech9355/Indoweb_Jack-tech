package com.example.ui.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.model.UserScript

class IndowebWebViewClient(
    private val context: Context,
    private val viewModel: BrowserViewModel,
    private val getEnabledScripts: () -> List<UserScript>
) : WebViewClient() {

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
        url?.let {
            viewModel.onPageStarted(it)
            viewModel.checkBookmarkStatus(it)
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

        // Do not inject scripts into internal about: pages or error pages
        if (url.startsWith("about:") || url.startsWith("data:")) return

        // Inject UserScripts matching the current URL
        injectMatchingUserScripts(view, url)
    }

    private fun injectMatchingUserScripts(webView: WebView, currentUrl: String) {
        val enabledScripts = getEnabledScripts()
        val matchingScripts = enabledScripts.filter { it.matchesUrl(currentUrl) }

        viewModel.setInjectedCount(matchingScripts.size)

        for (script in matchingScripts) {
            val wrappedCode = buildInjectedScript(script)
            webView.evaluateJavascript(wrappedCode) { result ->
                val isSuccess = result != null && !result.equals("null", ignoreCase = true)
                viewModel.recordScriptLog(
                    scriptName = script.name,
                    url = currentUrl,
                    isSuccess = true,
                    message = "Injected successfully: ${result?.take(60) ?: "OK"}"
                )
            }
        }
    }

    private fun buildInjectedScript(script: UserScript): String {
        val escapedScriptName = script.name.replace("'", "\\'")
        return """
            (function() {
                try {
                    console.log('[Indoweb] Executing UserScript: ${escapedScriptName}');
                    ${script.scriptCode}
                    return 'OK: ${escapedScriptName}';
                } catch (err) {
                    console.error('[Indoweb] Error in script ${escapedScriptName}:', err);
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
