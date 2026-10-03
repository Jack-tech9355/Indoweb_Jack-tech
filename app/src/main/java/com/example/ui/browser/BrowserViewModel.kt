package com.example.ui.browser

import android.app.Application
import android.content.Context
import android.webkit.CookieManager
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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
    val searchEngine: String = "DuckDuckGo", // DuckDuckGo, Google, or Bing
    val isAddressBarAtBottom: Boolean = false,
    val isIncognito: Boolean = false,
    val isReaderMode: Boolean = false,
    val detectedMediaUrl: String? = null,
    val detectedMediaTitle: String? = null,
    val findQuery: String = "",
    val isFindBarVisible: Boolean = false,
    val isScriptsSheetVisible: Boolean = false,
    val isBookmarksSheetVisible: Boolean = false,
    val isTabSwitcherVisible: Boolean = false,
    val isScriptStoreVisible: Boolean = false,
    val isJsConsoleVisible: Boolean = false,
    val isSettingsSheetVisible: Boolean = false,
    val scriptBeingEdited: UserScript? = null,
    val isAddingScript: Boolean = false,
    val showSslDialog: Boolean = false,
    val consoleMessage: String? = null
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("indoweb_session_prefs", Context.MODE_PRIVATE)
    private val repository: BrowserRepository

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
        _uiState.update { it.copy(isAddressBarAtBottom = isBottomBar, searchEngine = savedEngine) }

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
        // Save non-incognito tabs
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
            // If only 1 tab left, reset to new tab
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

    fun closeAllTabs(keepIncognito: Boolean = false) {
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
                detectedMediaTitle = tab.detectedMediaTitle
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
                detectedMediaTitle = null
            )
        }
    }

    fun onPageStarted(url: String) {
        updateActiveTab {
            it.copy(
                url = url,
                isLoading = true,
                isSecure = url.startsWith("https://"),
                detectedMediaUrl = null
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

        // Record history only if NOT incognito
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
                detectedMediaTitle = mediaTitle.ifBlank { "Media Stream ($mimeType)" }
            )
        }
        _uiState.update { it.copy(consoleMessage = "Playable media detected!") }
    }

    fun clearDetectedMedia() {
        updateActiveTab { it.copy(detectedMediaUrl = null, detectedMediaTitle = null) }
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

    // Sheet and Dialog visibility controls
    fun setScriptsSheetVisible(visible: Boolean) { _uiState.update { it.copy(isScriptsSheetVisible = visible) } }
    fun setBookmarksSheetVisible(visible: Boolean) { _uiState.update { it.copy(isBookmarksSheetVisible = visible) } }
    fun setTabSwitcherVisible(visible: Boolean) { _uiState.update { it.copy(isTabSwitcherVisible = visible) } }
    fun setScriptStoreVisible(visible: Boolean) { _uiState.update { it.copy(isScriptStoreVisible = visible) } }
    fun setJsConsoleVisible(visible: Boolean) { _uiState.update { it.copy(isJsConsoleVisible = visible) } }
    fun setSettingsSheetVisible(visible: Boolean) { _uiState.update { it.copy(isSettingsSheetVisible = visible) } }
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
            repository.insertScript(script)
            _uiState.update { it.copy(isAddingScript = false, scriptBeingEdited = null) }
        }
    }

    fun installStoreScript(script: UserScript) {
        viewModelScope.launch {
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

    fun dismissConsoleMessage() {
        _uiState.update { it.copy(consoleMessage = null) }
    }

    private fun formatInputAsUrl(input: String, searchEngine: String): String {
        if (input.isBlank()) return "about:blank"
        if (input.startsWith("http://") || input.startsWith("https://") || input.startsWith("about:") || input.startsWith("file://")) {
            return input
        }
        val isDomainLike = input.contains(".") && !input.contains(" ") && input.length > 3
        return if (isDomainLike) {
            "https://$input"
        } else {
            val encoded = java.net.URLEncoder.encode(input, "UTF-8")
            when (searchEngine) {
                "Google" -> "https://www.google.com/search?q=$encoded"
                "Bing" -> "https://www.bing.com/search?q=$encoded"
                else -> "https://duckduckgo.com/?q=$encoded"
            }
        }
    }
}
