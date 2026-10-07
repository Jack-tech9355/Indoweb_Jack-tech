package com.example.ui.browser

import android.app.Application
import android.content.Context
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.Bookmark
import com.example.data.model.HistoryItem
import com.example.data.model.UserAgentType
import com.example.data.model.UserScript
import com.example.data.model.WebTab
import com.example.data.repository.BrowserRepository
import com.example.data.repository.GreasyForkRepository
import com.example.data.repository.GreasyForkScriptItem
import com.example.ui.scripts.UserScriptDependencyManager
import com.example.ui.scripts.UserScriptParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

data class ScriptLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val scriptName: String,
    val url: String,
    val isSuccess: Boolean,
    val message: String
)

data class JsConsoleLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: String, // "LOG", "WARN", "ERROR", "INFO"
    val message: String
)

sealed class WebPermissionPrompt {
    data class Device(
        val origin: String,
        val resources: List<String>,
        val labels: List<String>,
        val request: PermissionRequest
    ) : WebPermissionPrompt()

    data class Geolocation(
        val origin: String,
        val callback: GeolocationPermissions.Callback
    ) : WebPermissionPrompt()
}

data class BrowserUiState(
    val currentUrl: String = "",
    val inputUrl: String = "",
    val pageTitle: String = "Indoweb",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isDesktopMode: Boolean = false,
    val userAgentType: UserAgentType = UserAgentType.DEFAULT_MOBILE,
    val isBookmarked: Boolean = false,
    val isSecure: Boolean = false,
    val injectedScriptsCount: Int = 0,
    val searchEngine: String = "DuckDuckGo", // DuckDuckGo, Google, Bing, Yahoo, Ecosia, Brave, Startpage
    val isAddressBarAtBottom: Boolean = false,
    val isIncognito: Boolean = false,
    val isReaderMode: Boolean = false,
    val isAdBlockerEnabled: Boolean = true,
    val blockedAdsCount: Int = 0,
    val detectedMediaUrl: String? = null,
    val detectedMediaTitle: String? = null,
    val detectedMediaType: String? = null,
    val findQuery: String = "",
    val isFindBarVisible: Boolean = false,
    val isScriptsSheetVisible: Boolean = false,
    val isBookmarksSheetVisible: Boolean = false,
    val isTabSwitcherVisible: Boolean = false,
    val isScriptStoreVisible: Boolean = false,
    val isJsConsoleVisible: Boolean = false,
    val isSettingsSheetVisible: Boolean = false,
    val isSiteShieldVisible: Boolean = false,
    val scriptBeingEdited: UserScript? = null,
    val isAddingScript: Boolean = false,
    val pendingInstallScript: UserScript? = null,
    val isFetchingScript: Boolean = false,
    val showSslDialog: Boolean = false,
    val consoleMessage: String? = null,
    val greasyForkSearchQuery: String = "",
    val isSearchingGreasyFork: Boolean = false,
    val greasyForkResults: List<GreasyForkScriptItem> = emptyList()
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("indoweb_session_prefs", Context.MODE_PRIVATE)
    private val repository: BrowserRepository
    private val greasyForkRepo = GreasyForkRepository()
    val dependencyManager = UserScriptDependencyManager(application)

    private val _tabs = MutableStateFlow<List<WebTab>>(emptyList())
    val tabs: StateFlow<List<WebTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String>("")
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _executionLogs = MutableStateFlow<List<ScriptLog>>(emptyList())
    val executionLogs: StateFlow<List<ScriptLog>> = _executionLogs.asStateFlow()

    private val _jsConsoleLogs = MutableStateFlow<List<JsConsoleLog>>(emptyList())
    val jsConsoleLogs: StateFlow<List<JsConsoleLog>> = _jsConsoleLogs.asStateFlow()

    private val _pendingPermissionPrompt = MutableStateFlow<WebPermissionPrompt?>(null)
    val pendingPermissionPrompt: StateFlow<WebPermissionPrompt?> = _pendingPermissionPrompt.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application, viewModelScope)
        repository = BrowserRepository(
            database.userScriptDao(),
            database.bookmarkDao(),
            database.historyDao()
        )

        viewModelScope.launch {
            repository.ensureDefaultScripts()
        }

        // Restore saved settings
        val isBottomBar = prefs.getBoolean("address_bar_bottom", false)
        val savedEngine = prefs.getString("search_engine", "DuckDuckGo") ?: "DuckDuckGo"
        val adBlocker = prefs.getBoolean("ad_blocker_enabled", true)
        _uiState.update {
            it.copy(
                isAddressBarAtBottom = isBottomBar,
                searchEngine = savedEngine,
                isAdBlockerEnabled = adBlocker
            )
        }

        // Restore saved session tabs
        restoreSavedTabs()
    }

    val allScripts: StateFlow<List<UserScript>> = repository.allScripts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val enabledScripts: StateFlow<List<UserScript>> = repository.enabledScripts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<Bookmark>> = repository.allBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryItem>> = repository.recentHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // -------------------------------------------------------------
    // MULTI-TAB MANAGEMENT & SESSION RESTORATION
    // -------------------------------------------------------------

    private fun restoreSavedTabs() {
        val savedJson = prefs.getString("saved_tabs_json", null)
        val restoredTabs = mutableListOf<WebTab>()
        if (!savedJson.isNullOrBlank()) {
            try {
                val array = JSONArray(savedJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val url = obj.optString("url", "")
                    val title = obj.optString("title", "New Tab")
                    val isDesktop = obj.optBoolean("isDesktop", false)
                    restoredTabs.add(
                        WebTab(
                            url = url,
                            title = title,
                            isDesktopMode = isDesktop,
                            isIncognito = false
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        if (restoredTabs.isEmpty()) {
            val initialTab = WebTab(url = "", title = "Start Page")
            restoredTabs.add(initialTab)
        }

        _tabs.value = restoredTabs
        val firstId = restoredTabs.first().id
        _activeTabId.value = firstId
        syncActiveTabToUi(restoredTabs.first())
    }

    private fun saveTabsSession() {
        val regularTabs = _tabs.value.filter { !it.isIncognito }
        val array = JSONArray()
        for (tab in regularTabs) {
            val obj = JSONObject().apply {
                put("url", tab.url)
                put("title", tab.title)
                put("isDesktop", tab.isDesktopMode)
            }
            array.put(obj)
        }
        prefs.edit().putString("saved_tabs_json", array.toString()).apply()
    }

    fun openNewTab(url: String = "", isIncognito: Boolean = false) {
        val newTab = WebTab(
            url = url,
            title = if (url.isBlank()) (if (isIncognito) "Private Tab" else "Start Page") else url,
            isIncognito = isIncognito
        )
        _tabs.update { it + newTab }
        _activeTabId.value = newTab.id
        syncActiveTabToUi(newTab)
        saveTabsSession()
    }

    fun closeTab(tabId: String) {
        val currentTabs = _tabs.value
        if (currentTabs.size <= 1) {
            val freshTab = WebTab(url = "", title = "Start Page")
            _tabs.value = listOf(freshTab)
            _activeTabId.value = freshTab.id
            syncActiveTabToUi(freshTab)
        } else {
            val remaining = currentTabs.filter { it.id != tabId }
            _tabs.value = remaining
            if (_activeTabId.value == tabId) {
                val nextActive = remaining.last()
                _activeTabId.value = nextActive.id
                syncActiveTabToUi(nextActive)
            }
        }
        saveTabsSession()
    }

    fun switchTab(tabId: String) {
        val target = _tabs.value.find { it.id == tabId } ?: return
        _activeTabId.value = tabId
        syncActiveTabToUi(target)
        _uiState.update { it.copy(isTabSwitcherVisible = false) }
    }

    fun closeAllTabs() {
        val freshTab = WebTab(url = "", title = "Start Page")
        _tabs.value = listOf(freshTab)
        _activeTabId.value = freshTab.id
        syncActiveTabToUi(freshTab)
        saveTabsSession()
    }

    private fun syncActiveTabToUi(tab: WebTab) {
        _uiState.update {
            it.copy(
                currentUrl = tab.url,
                inputUrl = tab.url,
                pageTitle = tab.title,
                isDesktopMode = tab.isDesktopMode,
                userAgentType = tab.userAgentType,
                canGoBack = tab.canGoBack,
                canGoForward = tab.canGoForward,
                isLoading = tab.isLoading,
                progress = tab.progress,
                isSecure = tab.isSecure,
                injectedScriptsCount = tab.injectedScriptsCount,
                isIncognito = tab.isIncognito,
                isReaderMode = tab.isReaderMode,
                detectedMediaUrl = tab.detectedMediaUrl,
                detectedMediaTitle = tab.detectedMediaTitle,
                detectedMediaType = tab.mediaType,
                blockedAdsCount = tab.blockedAdsCount
            )
        }
    }

    private fun updateActiveTab(transform: (WebTab) -> WebTab) {
        val activeId = _activeTabId.value
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == activeId) transform(tab) else tab
            }
        }
        _tabs.value.find { it.id == activeId }?.let { syncActiveTabToUi(it) }
        saveTabsSession()
    }

    // -------------------------------------------------------------
    // NAVIGATION & BROWSER ACTIONS
    // -------------------------------------------------------------

    fun updateInputUrl(newUrl: String) {
        _uiState.update { it.copy(inputUrl = newUrl) }
    }

    fun loadUrl(url: String) {
        val formatted = formatInputAsUrl(url.trim(), _uiState.value.searchEngine)
        updateActiveTab {
            it.copy(
                url = formatted,
                title = if (formatted == "about:blank" || formatted.isBlank()) "Start Page" else it.title,
                isLoading = true,
                progress = 15,
                isSecure = formatted.startsWith("https://"),
                detectedMediaUrl = null,
                detectedMediaTitle = null,
                mediaType = null,
                blockedAdsCount = 0
            )
        }
    }

    fun onPageStarted(url: String) {
        updateActiveTab {
            it.copy(
                url = url,
                isLoading = true,
                isSecure = url.startsWith("https://"),
                detectedMediaUrl = null,
                detectedMediaTitle = null,
                mediaType = null
            )
        }
    }

    fun onPageFinished(url: String, title: String?, canBack: Boolean, canForward: Boolean) {
        val resolvedTitle = if (!title.isNullOrBlank()) title else url
        val currentTab = _tabs.value.find { it.id == _activeTabId.value }

        updateActiveTab {
            it.copy(
                url = url,
                title = resolvedTitle,
                isLoading = false,
                progress = 100,
                canGoBack = canBack,
                canGoForward = canForward,
                isSecure = url.startsWith("https://")
            )
        }

        if (currentTab?.isIncognito == false && !url.startsWith("about:") && url.isNotBlank()) {
            viewModelScope.launch {
                repository.addHistory(resolvedTitle, url)
            }
        }
    }

    fun onProgressChanged(newProgress: Int) {
        updateActiveTab {
            it.copy(
                progress = newProgress,
                isLoading = newProgress < 100
            )
        }
    }

    fun onTitleReceived(title: String?) {
        if (!title.isNullOrBlank()) {
            updateActiveTab { it.copy(title = title) }
        }
    }

    fun recordAdBlocked() {
        updateActiveTab {
            it.copy(blockedAdsCount = it.blockedAdsCount + 1)
        }
    }

    fun toggleAdBlocker() {
        val nextState = !_uiState.value.isAdBlockerEnabled
        _uiState.update { it.copy(isAdBlockerEnabled = nextState) }
        prefs.edit().putBoolean("ad_blocker_enabled", nextState).apply()
        _uiState.update { it.copy(consoleMessage = if (nextState) "Ad & Tracker Blocker enabled" else "Ad Blocker disabled") }
    }

    fun toggleDesktopMode() {
        val currentDesktop = _uiState.value.isDesktopMode
        val nextType = if (!currentDesktop) UserAgentType.DESKTOP_CHROME else UserAgentType.DEFAULT_MOBILE
        updateActiveTab {
            it.copy(
                isDesktopMode = !currentDesktop,
                userAgentType = nextType
            )
        }
    }

    fun setUserAgent(type: UserAgentType) {
        updateActiveTab {
            it.copy(
                userAgentType = type,
                isDesktopMode = type == UserAgentType.DESKTOP_CHROME
            )
        }
    }

    fun toggleAddressBarPosition() {
        val newPos = !_uiState.value.isAddressBarAtBottom
        _uiState.update { it.copy(isAddressBarAtBottom = newPos) }
        prefs.edit().putBoolean("address_bar_bottom", newPos).apply()
    }

    fun setSearchEngine(engine: String) {
        _uiState.update { it.copy(searchEngine = engine) }
        prefs.edit().putString("search_engine", engine).apply()
    }

    fun onMediaDetected(mediaUrl: String, mediaTitle: String, mimeType: String) {
        updateActiveTab {
            it.copy(
                detectedMediaUrl = mediaUrl,
                detectedMediaTitle = mediaTitle.ifBlank { "Media Stream" },
                mediaType = mimeType
            )
        }
        _uiState.update { it.copy(consoleMessage = "Playable media detected: $mimeType") }
    }

    fun clearDetectedMedia() {
        updateActiveTab {
            it.copy(
                detectedMediaUrl = null,
                detectedMediaTitle = null,
                mediaType = null
            )
        }
    }

    fun toggleReaderMode() {
        val nextState = !_uiState.value.isReaderMode
        updateActiveTab { it.copy(isReaderMode = nextState) }
    }

    fun toggleBookmarkCurrentPage() {
        val current = _uiState.value
        val url = current.currentUrl
        if (url.isBlank() || url.startsWith("about:")) return

        viewModelScope.launch {
            if (current.isBookmarked) {
                repository.removeBookmark(url)
                _uiState.update { it.copy(isBookmarked = false) }
            } else {
                repository.addBookmark(current.pageTitle, url)
                _uiState.update { it.copy(isBookmarked = true) }
            }
        }
    }

    fun checkBookmarkStatus(url: String) {
        viewModelScope.launch {
            repository.isBookmarked(url).collect { isBookmarked ->
                _uiState.update { it.copy(isBookmarked = isBookmarked) }
            }
        }
    }

    // -------------------------------------------------------------
    // WEB PERMISSIONS HANDLING (Camera, Mic, Geolocation)
    // -------------------------------------------------------------

    fun handlePermissionRequest(request: PermissionRequest?) {
        if (request == null) return
        val origin = request.origin.toString()
        val resources = request.resources
        val cleanOrigin = sanitizeOrigin(origin)

        // Check if decision is already remembered in prefs
        val anyDenied = resources.any { prefs.getString("perm_${cleanOrigin}_$it", null) == "DENY" }
        if (anyDenied) {
            request.deny()
            return
        }

        val allAllowed = resources.all { prefs.getString("perm_${cleanOrigin}_$it", null) == "ALLOW" }
        if (allAllowed) {
            request.grant(resources)
            return
        }

        val labels = resources.map { res ->
            when (res) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> "Camera"
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> "Microphone"
                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> "Protected Media ID"
                else -> res.substringAfterLast(".")
            }
        }

        _pendingPermissionPrompt.value = WebPermissionPrompt.Device(
            origin = origin,
            resources = resources.toList(),
            labels = labels,
            request = request
        )
    }

    fun handleGeolocationPermission(origin: String?, callback: GeolocationPermissions.Callback?) {
        if (origin == null || callback == null) return
        val cleanOrigin = sanitizeOrigin(origin)
        val saved = prefs.getString("perm_${cleanOrigin}_geo", null)

        if (saved == "ALLOW") {
            callback.invoke(origin, true, true)
            return
        } else if (saved == "DENY") {
            callback.invoke(origin, false, true)
            return
        }

        _pendingPermissionPrompt.value = WebPermissionPrompt.Geolocation(origin, callback)
    }

    fun grantPermissionPrompt(prompt: WebPermissionPrompt, remember: Boolean) {
        when (prompt) {
            is WebPermissionPrompt.Device -> {
                if (remember) {
                    val clean = sanitizeOrigin(prompt.origin)
                    val editor = prefs.edit()
                    prompt.resources.forEach { res ->
                        editor.putString("perm_${clean}_$res", "ALLOW")
                    }
                    editor.apply()
                }
                prompt.request.grant(prompt.resources.toTypedArray())
            }
            is WebPermissionPrompt.Geolocation -> {
                if (remember) {
                    val clean = sanitizeOrigin(prompt.origin)
                    prefs.edit().putString("perm_${clean}_geo", "ALLOW").apply()
                }
                prompt.callback.invoke(prompt.origin, true, remember)
            }
        }
        _pendingPermissionPrompt.value = null
    }

    fun denyPermissionPrompt(prompt: WebPermissionPrompt, remember: Boolean) {
        when (prompt) {
            is WebPermissionPrompt.Device -> {
                if (remember) {
                    val clean = sanitizeOrigin(prompt.origin)
                    val editor = prefs.edit()
                    prompt.resources.forEach { res ->
                        editor.putString("perm_${clean}_$res", "DENY")
                    }
                    editor.apply()
                }
                prompt.request.deny()
            }
            is WebPermissionPrompt.Geolocation -> {
                if (remember) {
                    val clean = sanitizeOrigin(prompt.origin)
                    prefs.edit().putString("perm_${clean}_geo", "DENY").apply()
                }
                prompt.callback.invoke(prompt.origin, false, remember)
            }
        }
        _pendingPermissionPrompt.value = null
    }

    fun getOriginPermissions(origin: String): Map<String, Boolean?> {
        val clean = sanitizeOrigin(origin)
        val cam = prefs.getString("perm_${clean}_${PermissionRequest.RESOURCE_VIDEO_CAPTURE}", null)
        val mic = prefs.getString("perm_${clean}_${PermissionRequest.RESOURCE_AUDIO_CAPTURE}", null)
        val geo = prefs.getString("perm_${clean}_geo", null)
        return mapOf(
            "Camera" to (if (cam == "ALLOW") true else if (cam == "DENY") false else null),
            "Microphone" to (if (mic == "ALLOW") true else if (mic == "DENY") false else null),
            "Location" to (if (geo == "ALLOW") true else if (geo == "DENY") false else null)
        )
    }

    fun setOriginPermission(origin: String, type: String, allowed: Boolean?) {
        val clean = sanitizeOrigin(origin)
        val key = when (type) {
            "Camera" -> "perm_${clean}_${PermissionRequest.RESOURCE_VIDEO_CAPTURE}"
            "Microphone" -> "perm_${clean}_${PermissionRequest.RESOURCE_AUDIO_CAPTURE}"
            "Location" -> "perm_${clean}_geo"
            else -> return
        }
        val editor = prefs.edit()
        if (allowed == null) {
            editor.remove(key)
        } else {
            editor.putString(key, if (allowed) "ALLOW" else "DENY")
        }
        editor.apply()
    }

    fun clearOriginData(origin: String, webView: WebView?) {
        val host = try { URI(origin).host } catch (_: Exception) { "" }
        if (!host.isNullOrBlank()) {
            val cm = CookieManager.getInstance()
            val cookies = cm.getCookie(origin)
            if (cookies != null) {
                cookies.split(";").forEach { cookie ->
                    val name = cookie.substringBefore("=").trim()
                    cm.setCookie(origin, "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                }
            }
            WebStorage.getInstance().deleteOrigin(origin)
            webView?.clearCache(true)
            _uiState.update { it.copy(consoleMessage = "Cleared cookies & cache for $host") }
        }
    }

    private fun sanitizeOrigin(origin: String): String {
        return origin.replace("https://", "")
            .replace("http://", "")
            .replace("/", "_")
            .replace(":", "_")
    }

    // -------------------------------------------------------------
    // GREASYFORK API LIVE SEARCH & 1-TAP INSTALL
    // -------------------------------------------------------------

    fun searchGreasyFork(query: String) {
        _uiState.update { it.copy(greasyForkSearchQuery = query, isSearchingGreasyFork = true) }
        viewModelScope.launch {
            val results = greasyForkRepo.searchScripts(query)
            _uiState.update {
                it.copy(
                    greasyForkResults = results,
                    isSearchingGreasyFork = false
                )
            }
        }
    }

    fun installGreasyForkScript(item: GreasyForkScriptItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingScript = true) }
            val rawCode = greasyForkRepo.fetchScriptCode(item.codeUrl)
            if (rawCode.isNotBlank()) {
                val parsed = UserScriptParser.parse(rawCode, item.codeUrl)
                // Prefetch any @require dependencies in background
                dependencyManager.prefetchDependencies(parsed.requires)
                repository.insertScript(parsed)
                _uiState.update {
                    it.copy(
                        isFetchingScript = false,
                        consoleMessage = "Successfully installed '${parsed.name}' from GreasyFork"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isFetchingScript = false,
                        consoleMessage = "Failed to download script from GreasyFork"
                    )
                }
            }
        }
    }

    // Sheet and Dialog visibility controls
    fun setScriptsSheetVisible(visible: Boolean) { _uiState.update { it.copy(isScriptsSheetVisible = visible) } }
    fun setBookmarksSheetVisible(visible: Boolean) { _uiState.update { it.copy(isBookmarksSheetVisible = visible) } }
    fun setTabSwitcherVisible(visible: Boolean) { _uiState.update { it.copy(isTabSwitcherVisible = visible) } }
    fun setScriptStoreVisible(visible: Boolean) {
        _uiState.update { it.copy(isScriptStoreVisible = visible) }
        if (visible && _uiState.value.greasyForkResults.isEmpty()) {
            searchGreasyFork("")
        }
    }
    fun setJsConsoleVisible(visible: Boolean) { _uiState.update { it.copy(isJsConsoleVisible = visible) } }
    fun setSettingsSheetVisible(visible: Boolean) { _uiState.update { it.copy(isSettingsSheetVisible = visible) } }
    fun setSiteShieldVisible(visible: Boolean) { _uiState.update { it.copy(isSiteShieldVisible = visible) } }
    fun setFindBarVisible(visible: Boolean) { _uiState.update { it.copy(isFindBarVisible = visible) } }
    fun setFindQuery(query: String) { _uiState.update { it.copy(findQuery = query) } }
    fun setAddingScript(adding: Boolean) { _uiState.update { it.copy(isAddingScript = adding) } }
    fun setScriptBeingEdited(script: UserScript?) { _uiState.update { it.copy(scriptBeingEdited = script) } }
    fun setShowSslDialog(show: Boolean) { _uiState.update { it.copy(showSslDialog = show) } }
    fun setInjectedCount(count: Int) { updateActiveTab { it.copy(injectedScriptsCount = count) } }

    fun recordScriptLog(scriptName: String, url: String, isSuccess: Boolean, message: String) {
        val log = ScriptLog(
            scriptName = scriptName,
            url = url,
            isSuccess = isSuccess,
            message = message
        )
        _executionLogs.update { (listOf(log) + it).take(50) }
    }

    fun recordJsConsole(level: String, message: String) {
        val log = JsConsoleLog(level = level.uppercase(), message = message)
        _jsConsoleLogs.update { (listOf(log) + it).take(100) }
    }

    fun clearJsConsole() {
        _jsConsoleLogs.value = emptyList()
    }

    fun toggleScript(id: String, enabled: Boolean) {
        viewModelScope.launch { repository.toggleScript(id, enabled) }
    }

    fun saveScript(script: UserScript) {
        viewModelScope.launch {
            dependencyManager.prefetchDependencies(script.requires)
            repository.insertScript(script)
            _uiState.update { it.copy(isAddingScript = false, scriptBeingEdited = null) }
        }
    }

    fun installStoreScript(script: UserScript) {
        viewModelScope.launch {
            dependencyManager.prefetchDependencies(script.requires)
            repository.insertScript(script)
            _uiState.update { it.copy(consoleMessage = "Installed '${script.name}' to UserScripts") }
        }
    }

    fun deleteScript(id: String) {
        viewModelScope.launch { repository.deleteScript(id) }
    }

    fun resetDefaultScripts() {
        viewModelScope.launch { repository.resetToDefaultScripts() }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { repository.removeBookmark(bookmark.url) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun clearBrowserData(webView: WebView?) {
        webView?.clearCache(true)
        webView?.clearHistory()
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        _uiState.update { it.copy(consoleMessage = "Browser cache, cookies & storage cleared") }
    }

    fun fetchAndPromptInstallUserScript(scriptUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isFetchingScript = true) }
            try {
                val urlObj = java.net.URL(scriptUrl)
                val conn = urlObj.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; Indoweb)")
                conn.connectTimeout = 12000
                conn.readTimeout = 12000

                val content = conn.inputStream.bufferedReader().use { it.readText() }
                val parsedScript = UserScriptParser.parse(content, scriptUrl)
                dependencyManager.prefetchDependencies(parsedScript.requires)
                _uiState.update {
                    it.copy(
                        pendingInstallScript = parsedScript,
                        isFetchingScript = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isFetchingScript = false,
                        consoleMessage = "Failed to download script: ${e.message}"
                    )
                }
            }
        }
    }

    fun confirmInstallPendingScript(script: UserScript) {
        viewModelScope.launch {
            dependencyManager.prefetchDependencies(script.requires)
            repository.insertScript(script)
            _uiState.update {
                it.copy(
                    pendingInstallScript = null,
                    consoleMessage = "Installed UserScript: ${script.name}"
                )
            }
        }
    }

    fun dismissInstallPendingScript() {
        _uiState.update { it.copy(pendingInstallScript = null) }
    }

    fun importUserScript(script: UserScript) {
        viewModelScope.launch {
            dependencyManager.prefetchDependencies(script.requires)
            repository.insertScript(script)
            _uiState.update {
                it.copy(consoleMessage = "Imported script: ${script.name}")
            }
        }
    }

    fun dismissConsoleMessage() {
        _uiState.update { it.copy(consoleMessage = null) }
    }

    fun formatInputAsUrl(input: String, searchEngine: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return "about:blank"
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("about:") || trimmed.startsWith("file://")) {
            return trimmed
        }

        // Quick search prefixes
        val prefixMap = listOf(
            Pair("@yt ", "https://www.youtube.com/results?search_query="),
            Pair("@youtube ", "https://www.youtube.com/results?search_query="),
            Pair("@wiki ", "https://en.wikipedia.org/wiki/Special:Search?search="),
            Pair("@w ", "https://en.wikipedia.org/wiki/Special:Search?search="),
            Pair("@g ", "https://www.google.com/search?q="),
            Pair("@google ", "https://www.google.com/search?q="),
            Pair("@ddg ", "https://duckduckgo.com/?q="),
            Pair("@bing ", "https://www.bing.com/search?q="),
            Pair("@brave ", "https://search.brave.com/search?q="),
            Pair("@eco ", "https://www.ecosia.org/search?q="),
            Pair("@ecosia ", "https://www.ecosia.org/search?q="),
            Pair("@sp ", "https://www.startpage.com/do/dsearch?query="),
            Pair("@startpage ", "https://www.startpage.com/do/dsearch?query="),
            Pair("@gh ", "https://github.com/search?q="),
            Pair("@github ", "https://github.com/search?q="),
            Pair("@reddit ", "https://www.reddit.com/search/?q=")
        )

        for ((prefix, searchUrl) in prefixMap) {
            if (trimmed.startsWith(prefix, ignoreCase = true)) {
                val query = trimmed.substring(prefix.length).trim()
                return searchUrl + URLEncoder.encode(query, "UTF-8")
            }
        }

        // Domain-like check (e.g. "reddit.com", "example.org/path")
        val isDomainLike = trimmed.contains(".") && !trimmed.contains(" ") && trimmed.length > 3
        return if (isDomainLike) {
            "https://$trimmed"
        } else {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            when (searchEngine) {
                "Google" -> "https://www.google.com/search?q=$encoded"
                "Bing" -> "https://www.bing.com/search?q=$encoded"
                "Yahoo" -> "https://search.yahoo.com/search?p=$encoded"
                "Ecosia" -> "https://www.ecosia.org/search?q=$encoded"
                "Brave" -> "https://search.brave.com/search?q=$encoded"
                "Startpage" -> "https://www.startpage.com/do/dsearch?query=$encoded"
                else -> "https://duckduckgo.com/?q=$encoded"
            }
        }
    }
}
