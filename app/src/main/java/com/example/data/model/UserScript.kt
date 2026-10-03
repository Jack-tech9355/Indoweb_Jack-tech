package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "user_scripts")
data class UserScript(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val urlPattern: String = "*", // e.g. "*", "wikipedia.org", "https://*.google.com/*"
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
     * Checks if this script should execute on the target URL.
     */
    fun matchesUrl(targetUrl: String): Boolean {
        if (targetUrl.isBlank()) return false
        val pattern = urlPattern.trim()
        if (pattern.isEmpty() || pattern == "*" || pattern == "*://*/*" || pattern == "<all_urls>") {
            return true
        }

        return try {
            if (pattern.startsWith("/") && pattern.endsWith("/") && pattern.length > 2) {
                // Regex pattern
                val regexStr = pattern.substring(1, pattern.length - 1)
                Regex(regexStr, RegexOption.IGNORE_CASE).containsMatchIn(targetUrl)
            } else if (pattern.contains("*")) {
                // Glob wildcard pattern -> convert to regex
                val escaped = Regex.escape(pattern)
                    .replace("\\*", ".*")
                    .replace("\\?", ".")
                Regex(escaped, RegexOption.IGNORE_CASE).containsMatchIn(targetUrl)
            } else {
                // Simple host / substring match
                targetUrl.contains(pattern, ignoreCase = true)
            }
        } catch (_: Exception) {
            // Fallback to substring matching if regex syntax was invalid
            targetUrl.contains(pattern, ignoreCase = true)
        }
    }
}
