package com.example.ui.scripts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class UserScriptDependencyManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val cacheDir: File by lazy {
        File(context.cacheDir, "userscript_deps").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Resolves all @require dependency URLs, fetching from local cache or downloading
     * if not yet cached, and concatenates them into a single executable JS bundle.
     */
    suspend fun resolveDependencies(requires: String): String = withContext(Dispatchers.IO) {
        if (requires.isBlank()) return@withContext ""
        val urls = requires.split(",", "\n").map { it.trim() }.filter { it.isNotBlank() }
        if (urls.isEmpty()) return@withContext ""

        val sb = StringBuilder()
        for (url in urls) {
            try {
                val content = getOrFetchDependency(url)
                if (content.isNotBlank()) {
                    sb.append("\n// --- Required Dependency: ").append(url).append(" ---\n")
                    sb.append(content).append("\n")
                }
            } catch (e: Exception) {
                // If a dependency fails to fetch, log and continue
                sb.append("\n// Failed to load requirement: ").append(url).append(" (").append(e.message).append(")\n")
            }
        }
        sb.toString()
    }

    suspend fun prefetchDependencies(requires: String) = withContext(Dispatchers.IO) {
        if (requires.isBlank()) return@withContext
        val urls = requires.split(",", "\n").map { it.trim() }.filter { it.isNotBlank() }
        for (url in urls) {
            try {
                getOrFetchDependency(url)
            } catch (_: Exception) {}
        }
    }

    private fun getOrFetchDependency(url: String): String {
        val fileName = hashUrl(url) + ".js"
        val file = File(cacheDir, fileName)

        if (file.exists() && file.length() > 0) {
            return file.readText()
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile; Indoweb)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ""
            val body = response.body?.string() ?: ""
            if (body.isNotBlank()) {
                file.writeText(body)
            }
            return body
        }
    }

    private fun hashUrl(url: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(url.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            url.replace("[^a-zA-Z0-9]".toRegex(), "_").take(32)
        }
    }
}
