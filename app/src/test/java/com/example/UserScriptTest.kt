package com.example

import com.example.data.model.UserScript
import com.example.ui.scripts.UserScriptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserScriptTest {

    @Test
    fun wildcard_matchesAnyUrl() {
        val script = UserScript(
            name = "Wildcard Test",
            urlPattern = "*",
            scriptCode = "console.log(1);"
        )
        assertTrue(script.matchesUrl("https://en.wikipedia.org/wiki/Main_Page"))
        assertTrue(script.matchesUrl("https://google.com/search?q=test"))
    }

    @Test
    fun domainPattern_matchesSpecificDomain() {
        val script = UserScript(
            name = "Wiki Script",
            urlPattern = "wikipedia.org",
            scriptCode = "console.log(1);"
        )
        assertTrue(script.matchesUrl("https://en.wikipedia.org/wiki/Main_Page"))
        assertFalse(script.matchesUrl("https://google.com/search?q=wikipedia.org.fake.com"))
    }

    @Test
    fun globPattern_matchesWildcardHost() {
        val script = UserScript(
            name = "Google Search Script",
            urlPattern = "*://*.google.com/*",
            scriptCode = "console.log(1);"
        )
        assertTrue(script.matchesUrl("https://www.google.com/search?q=test"))
        assertFalse(script.matchesUrl("https://yahoo.com/search?q=google.com"))
    }

    @Test
    fun parser_extractsRequireAndResource() {
        val rawScript = """
            // ==UserScript==
            // @name         Test Library UserScript
            // @namespace    http://tampermonkey.net/
            // @version      2.1
            // @description  A test script with dependencies
            // @match        *://*.youtube.com/*
            // @require      https://code.jquery.com/jquery-3.6.0.min.js
            // @require      https://cdnjs.cloudflare.com/ajax/libs/lodash.js/4.17.21/lodash.min.js
            // @resource     customCss https://example.com/style.css
            // @run-at       document-start
            // @grant        GM_xmlhttpRequest
            // ==/UserScript==
            
            console.log('Running test script with jQuery: ' + $);
        """.trimIndent()

        val parsed = UserScriptParser.parse(rawScript)
        assertEquals("Test Library UserScript", parsed.name)
        assertEquals("2.1", parsed.version)
        assertEquals("document_start", parsed.runAt)
        assertTrue(parsed.requires.contains("jquery-3.6.0.min.js"))
        assertTrue(parsed.requires.contains("lodash.min.js"))
        assertTrue(parsed.resources.contains("customCss"))
        assertTrue(parsed.matchesUrl("https://www.youtube.com/watch?v=123"))
    }
}
