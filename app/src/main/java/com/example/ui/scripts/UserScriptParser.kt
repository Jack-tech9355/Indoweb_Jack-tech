package com.example.ui.scripts

import com.example.data.model.UserScript
import java.util.UUID

object UserScriptParser {

    /**
     * Parses a raw GreasyFork / Tampermonkey script string and extracts
     * metadata from the `// ==UserScript== ... // ==/UserScript==` block.
     */
    fun parse(rawCode: String, sourceUrl: String = ""): UserScript {
        var name = ""
        var description = ""
        var version = "1.0"
        var author = "Community"
        var runAt = "document_end"
        val matches = mutableListOf<String>()
        val grants = mutableListOf<String>()

        val headerRegex = Regex("""//\s*==UserScript==([\s\S]*?)//\s*==/UserScript==""", RegexOption.MULTILINE)
        val headerMatch = headerRegex.find(rawCode)

        if (headerMatch != null) {
            val headerContent = headerMatch.groupValues[1]
            headerContent.lines().forEach { line ->
                val trimmed = line.trim().removePrefix("//").trim()
                if (trimmed.startsWith("@")) {
                    val parts = trimmed.substring(1).split(Regex("""\s+"""), limit = 2)
                    val tag = parts.getOrNull(0)?.lowercase() ?: ""
                    val value = parts.getOrNull(1)?.trim() ?: ""

                    when (tag) {
                        "name" -> if (name.isBlank()) name = value
                        "description" -> if (description.isBlank()) description = value
                        "version" -> version = value
                        "author" -> author = value
                        "match", "include" -> if (value.isNotBlank()) matches.add(value)
                        "run-at" -> {
                            runAt = when (value.lowercase().replace("_", "-")) {
                                "document-start" -> "document_start"
                                "document-body" -> "document_start"
                                "document-idle" -> "document_end"
                                else -> "document_end"
                            }
                        }
                        "grant" -> if (value.isNotBlank() && value != "none") grants.add(value)
                    }
                }
            }
        }

        // Fallbacks if metadata was not found or minimal
        if (name.isBlank()) {
            val fileName = sourceUrl.substringAfterLast("/").substringBefore(".user.js").ifBlank { "Imported Script" }
            name = fileName.replace("-", " ").replace("_", " ").capitalizeWords()
        }
        if (description.isBlank()) {
            description = "Installed UserScript from ${if (sourceUrl.isNotBlank()) sourceUrl else "custom source"}"
        }

        val patternString = if (matches.isNotEmpty()) {
            matches.joinToString(",")
        } else {
            "*"
        }

        return UserScript(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            urlPattern = patternString,
            scriptCode = rawCode,
            isEnabled = true,
            runAt = runAt,
            author = author,
            version = version,
            isBuiltIn = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
}
