package com.example.ui.browser

import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class IndowebJsBridge(
    private val webViewProvider: () -> WebView?,
    private val onMediaDetected: (url: String, title: String, mimeType: String) -> Unit,
    private val onConsoleLog: (level: String, message: String) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

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

    /**
     * Native OkHttp implementation of GM_xmlhttpRequest that completely
     * bypasses browser Cross-Origin Resource Sharing (CORS) restrictions.
     */
    @JavascriptInterface
    fun gmXmlHttpRequest(reqId: String, detailsJson: String) {
        scope.launch {
            try {
                val details = JSONObject(detailsJson)
                val targetUrl = details.getString("url")
                val method = details.optString("method", "GET").uppercase()
                val data = details.optString("data", null)

                val requestBuilder = Request.Builder().url(targetUrl)

                val headersObj = details.optJSONObject("headers")
                if (headersObj != null) {
                    val keys = headersObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        requestBuilder.header(key, headersObj.getString(key))
                    }
                }

                when (method) {
                    "GET" -> requestBuilder.get()
                    "HEAD" -> requestBuilder.head()
                    "POST" -> {
                        val body = (data ?: "").toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
                        requestBuilder.post(body)
                    }
                    "PUT" -> {
                        val body = (data ?: "").toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
                        requestBuilder.put(body)
                    }
                    "DELETE" -> {
                        val body = if (data != null) data.toRequestBody(null) else null
                        requestBuilder.delete(body)
                    }
                    "PATCH" -> {
                        val body = (data ?: "").toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
                        requestBuilder.patch(body)
                    }
                    else -> requestBuilder.get()
                }

                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    val respJson = JSONObject().apply {
                        put("status", response.code)
                        put("statusText", response.message)
                        put("responseText", response.body?.string() ?: "")
                        val headerSb = StringBuilder()
                        for (i in 0 until response.headers.size) {
                            headerSb.append(response.headers.name(i)).append(": ").append(response.headers.value(i)).append("\r\n")
                        }
                        put("responseHeaders", headerSb.toString())
                    }

                    postJs("if (window.__gmXhrCallback) window.__gmXhrCallback(${JSONObject.quote(reqId)}, false, ${JSONObject.quote(respJson.toString())});")
                }
            } catch (e: Exception) {
                val errJson = JSONObject().apply {
                    put("status", 0)
                    put("statusText", e.message ?: "Network error")
                    put("responseText", "")
                    put("responseHeaders", "")
                }
                postJs("if (window.__gmXhrCallback) window.__gmXhrCallback(${JSONObject.quote(reqId)}, true, ${JSONObject.quote(errJson.toString())});")
            }
        }
    }

    private fun postJs(js: String) {
        val wv = webViewProvider() ?: return
        wv.post {
            wv.evaluateJavascript(js, null)
        }
    }
}
