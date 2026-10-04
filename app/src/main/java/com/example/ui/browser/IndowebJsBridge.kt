package com.example.ui.browser

import android.webkit.JavascriptInterface

class IndowebJsBridge(
    private val onMediaDetected: (url: String, title: String, mimeType: String) -> Unit,
    private val onConsoleLog: (level: String, message: String) -> Unit
) {
    @JavascriptInterface
    fun onMediaFound(url: String, title: String, mimeType: String) {
        if (url.isNotBlank() && !url.startsWith("blob:")) {
            onMediaDetected(url, title, mimeType)
        }
    }

    @JavascriptInterface
    fun log(level: String, message: String) {
        onConsoleLog(level, message)
    }
}
