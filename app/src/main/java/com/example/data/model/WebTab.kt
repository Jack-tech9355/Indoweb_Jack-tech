package com.example.data.model

import java.util.UUID

data class WebTab(
    val id: String = UUID.randomUUID().toString(),
    val url: String = "",
    val title: String = "New Tab",
    val isIncognito: Boolean = false,
    val isDesktopMode: Boolean = false,
    val userAgentType: UserAgentType = UserAgentType.DEFAULT_MOBILE,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val isSecure: Boolean = false,
    val injectedScriptsCount: Int = 0,
    val isReaderMode: Boolean = false,
    val detectedMediaUrl: String? = null,
    val detectedMediaTitle: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class UserAgentType(val label: String, val userAgentString: String?) {
    DEFAULT_MOBILE("Mobile Chrome", null),
    DESKTOP_CHROME(
        "Desktop Chrome",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    ),
    FIREFOX_MOBILE(
        "Firefox Mobile",
        "Mozilla/5.0 (Android; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0"
    ),
    SAFARI_IPHONE(
        "Safari iPhone",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
    )
}
