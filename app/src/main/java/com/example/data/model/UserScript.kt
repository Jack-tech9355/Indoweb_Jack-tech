package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.net.URI
import java.util.UUID

@Entity(tableName = "user_scripts")
data class UserScript(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val urlPattern: String = "*", // Comma-separated match patterns: e.g. "*://*.youtube.com/*,https://*.google.com/*"
    val scriptCode: String,
    val isEnabled: Boolean = true,
    val runAt: String = "document_end", // "document_end" or "document_start"
    val author: String = "User",
    val version: String = "1.0",
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Checks if this script should execute on the target URL according
     * to all comma/newline separated match patterns.
     */
    fun matchesUrl(targetUrl: String): Boolean {
        if (targetUrl.isBlank()) return false
        val patterns = urlPattern.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (patterns.isEmpty()) return true

        return patterns.any { singlePatternMatches(it, targetUrl) }
    }

    private fun singlePatternMatches(pattern: String, targetUrl: String): Boolean {
        if (pattern == "*" || pattern == "*://*/*" || pattern == "<all_urls>" || pattern == "http*://*/*") {
            return true
        }

        return try {
            if (pattern.startsWith("/") && pattern.endsWith("/") && pattern.length > 2) {
                // Regex pattern (/^https:\/\/.../)
                val regexStr = pattern.substring(1, pattern.length - 1)
                Regex(regexStr, RegexOption.IGNORE_CASE).containsMatchIn(targetUrl)
            } else if (pattern.contains("://")) {
                // Standard Tampermonkey match rule: <scheme>://<host><path>
                val parts = pattern.split("://", limit = 2)
                val schemePattern = parts[0]
                val hostAndPath = parts[1]
                val slashIdx = hostAndPath.indexOf('/')
                val hostPattern = if (slashIdx != -1) hostAndPath.substring(0, slashIdx) else hostAndPath
                val pathPattern = if (slashIdx != -1) hostAndPath.substring(slashIdx) else "/*"

                val uri = URI(targetUrl)
                val scheme = uri.scheme ?: ""
                val host = uri.host ?: ""
                val path = (uri.rawPath ?: "") + if (uri.rawQuery != null) "?${uri.rawQuery}" else ""

                // 1. Scheme match
                val schemeMatch = when (schemePattern.lowercase()) {
                    "*" -> scheme.equals("http", true) || scheme.equals("https", true)
                    "http*" -> scheme.equals("http", true) || scheme.equals("https", true)
                    else -> scheme.equals(schemePattern, true)
                }
                if (!schemeMatch) return false

                // 2. Host match
                val hostMatch = if (hostPattern == "*") {
                    true
                } else if (hostPattern.startsWith("*.")) {
                    val root = hostPattern.substring(2)
                    host.equals(root, true) || host.endsWith(".$root", true)
                } else {
                    host.equals(hostPattern, true)
                }
                if (!hostMatch) return false

                // 3. Path match
                val pathRegex = Regex.escape(pathPattern).replace("\\*", ".*")
                Regex("^$pathRegex$", RegexOption.IGNORE_CASE).containsMatchIn(path) ||
                        Regex(pathRegex, RegexOption.IGNORE_CASE).containsMatchIn(path)
            } else if (pattern.contains("*")) {
                var regexStr = Regex.escape(pattern)
                regexStr = regexStr.replace("\\*", ".*")
                regexStr = regexStr.replace("\\?", ".")
                Regex(regexStr, RegexOption.IGNORE_CASE).containsMatchIn(targetUrl)
            } else {
                // Host / domain substring match
                val host = try {
                    URI(targetUrl).host ?: ""
                } catch (_: Exception) { "" }
                if (host.isNotBlank()) {
                    host.equals(pattern, ignoreCase = true) || host.endsWith(".$pattern", ignoreCase = true)
                } else {
                    targetUrl.contains(pattern, ignoreCase = true)
                }
            }
        } catch (_: Exception) {
            targetUrl.contains(pattern, ignoreCase = true)
        }
    }
}
