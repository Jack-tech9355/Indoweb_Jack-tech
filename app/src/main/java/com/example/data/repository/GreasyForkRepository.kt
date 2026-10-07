package com.example.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class GreasyForkScriptItem(
    val id: Long,
    val name: String,
    val description: String,
    val url: String,
    val codeUrl: String,
    val version: String,
    val author: String,
    val totalInstalls: Long,
    val dailyInstalls: Long
)

class GreasyForkRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun searchScripts(query: String): List<GreasyForkScriptItem> = withContext(Dispatchers.IO) {
        val endpoint = if (query.isBlank()) {
            "https://greasyfork.org/scripts.json?sort=daily_installs"
        } else {
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            "https://greasyfork.org/scripts.json?q=$encoded&sort=total_installs"
        }

        try {
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; Indoweb)")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val array = JSONArray(body)
                val items = mutableListOf<GreasyForkScriptItem>()

                for (i in 0 until minOf(array.length(), 40)) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optLong("id", 0)
                    val name = obj.optString("name", "Untitled Script")
                    val description = obj.optString("description", "")
                    val url = obj.optString("url", "")
                    val codeUrl = obj.optString("code_url", "")
                    val version = obj.optString("version", "1.0")

                    // Extract authors
                    val authorsArr = obj.optJSONArray("authors")
                    val authorName = if (authorsArr != null && authorsArr.length() > 0) {
                        authorsArr.getJSONObject(0).optString("name", "GreasyFork Author")
                    } else {
                        "Community"
                    }

                    val totalInstalls = obj.optLong("total_installs", 0)
                    val dailyInstalls = obj.optLong("daily_installs", 0)

                    if (codeUrl.isNotBlank()) {
                        items.add(
                            GreasyForkScriptItem(
                                id = id,
                                name = name,
                                description = description,
                                url = url,
                                codeUrl = codeUrl,
                                version = version,
                                author = authorName,
                                totalInstalls = totalInstalls,
                                dailyInstalls = dailyInstalls
                            )
                        )
                    }
                }
                items
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchScriptCode(codeUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(codeUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; Indoweb)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() ?: "" else ""
            }
        } catch (_: Exception) {
            ""
        }
    }
}
