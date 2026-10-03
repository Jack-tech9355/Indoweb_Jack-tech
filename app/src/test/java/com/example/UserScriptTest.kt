package com.example

import com.example.data.model.UserScript
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
}
