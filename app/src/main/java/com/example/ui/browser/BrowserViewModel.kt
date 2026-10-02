package com.example.ui.browser

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.Bookmark
import com.example.data.model.HistoryItem
import com.example.data.model.UserScript
import com.example.data.repository.BrowserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScriptLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val scriptName: String,
    val url: String,
    val isSuccess: Boolean,
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
    val isBookmarked: Boolean = false,
    val isSecure: Boolean = false,
    val injectedScriptsCount: Int = 0,
    val searchEngine: String = "DuckDuckGo", // DuckDuckGo or Google
    val findQuery: String = "",
    val isFindBarVisible: Boolean = false,
    val isScriptsSheetVisible: Boolean = false,
    val isBookmarksSheetVisible: Boolean = false,
    val scriptBeingEdited: UserScript? = null,
    val isAddingScript: Boolean = false,
    val showSslDialog: Boolean = false,
    val consoleMessage: String? = null
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BrowserRepository
    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _executionLogs = MutableStateFlow<List<ScriptLog>>(emptyList())
    val executionLogs: StateFlow<List<ScriptLog>> = _executionLogs.asStateFlow()

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
    }

    val allScripts: StateFlow<List<UserScript>> = repository.allScripts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val enabledScripts: StateFlow<List<UserScript>> = repository.enabledScripts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<Bookmark>> = repository.allBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryItem>> = repository.recentHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateInputUrl(newUrl: String) {
        _uiState.update { it.copy(inputUrl = newUrl) }
    }

    fun loadUrl(url: String) {
        val formatted = formatInputAsUrl(url.trim(), _uiState.value.searchEngine)
        _uiState.update {
            it.copy(
                currentUrl = formatted,
                inputUrl = formatted,
                isLoading = true,
                progress = 10,
                isSecure = formatted.startsWith("https://")
            )
        }
    }

    fun onPageStarted(url: String) {
        _uiState.update {
            it.copy(
                currentUrl = url,
                inputUrl = url,
                isLoading = true,
                isSecure = url.startsWith("https://")
            )
        }
    }

    fun onPageFinished(url: String, title: String?, canBack: Boolean, canForward: Boolean) {
        val resolvedTitle = if (!title.isNullOrBlank()) title else url
        _uiState.update {
            it.copy(
                currentUrl = url,
                inputUrl = url,
                pageTitle = resolvedTitle,
                isLoading = false,
                progress = 100,
                canGoBack = canBack,
                canGoForward = canForward,
                isSecure = url.startsWith("https://")
            )
        }

        // Record history
        viewModelScope.launch {
            repository.addHistory(resolvedTitle, url)
        }
    }

    fun onProgressChanged(newProgress: Int) {
        _uiState.update {
            it.copy(
                progress = newProgress,
                isLoading = newProgress < 100
            )
        }
    }

    fun onTitleReceived(title: String?) {
        if (!title.isNullOrBlank()) {
            _uiState.update { it.copy(pageTitle = title) }
        }
    }

    fun setNavigationState(canBack: Boolean, canForward: Boolean) {
        _uiState.update { it.copy(canGoBack = canBack, canGoForward = canForward) }
    }

    fun toggleDesktopMode() {
        _uiState.update { it.copy(isDesktopMode = !it.isDesktopMode) }
    }

    fun setSearchEngine(engine: String) {
        _uiState.update { it.copy(searchEngine = engine) }
    }

    fun toggleBookmarkCurrentPage() {
        val current = _uiState.value
        val url = current.currentUrl
        if (url.isBlank()) return

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

    fun setScriptsSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(isScriptsSheetVisible = visible) }
    }

    fun setBookmarksSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(isBookmarksSheetVisible = visible) }
    }

    fun setFindBarVisible(visible: Boolean) {
        _uiState.update { it.copy(isFindBarVisible = visible) }
    }

    fun setFindQuery(query: String) {
        _uiState.update { it.copy(findQuery = query) }
    }

    fun setAddingScript(adding: Boolean) {
        _uiState.update { it.copy(isAddingScript = adding) }
    }

    fun setScriptBeingEdited(script: UserScript?) {
        _uiState.update { it.copy(scriptBeingEdited = script) }
    }

    fun setShowSslDialog(show: Boolean) {
        _uiState.update { it.copy(showSslDialog = show) }
    }

    fun setInjectedCount(count: Int) {
        _uiState.update { it.copy(injectedScriptsCount = count) }
    }

    fun recordScriptLog(scriptName: String, url: String, isSuccess: Boolean, message: String) {
        val log = ScriptLog(
            scriptName = scriptName,
            url = url,
            isSuccess = isSuccess,
            message = message
        )
        _executionLogs.update { (listOf(log) + it).take(50) }
    }

    fun toggleScript(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleScript(id, enabled)
        }
    }

    fun saveScript(script: UserScript) {
        viewModelScope.launch {
            repository.insertScript(script)
            _uiState.update { it.copy(isAddingScript = false, scriptBeingEdited = null) }
        }
    }

    fun deleteScript(id: String) {
        viewModelScope.launch {
            repository.deleteScript(id)
        }
    }

    fun resetDefaultScripts() {
        viewModelScope.launch {
            repository.resetToDefaultScripts()
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            repository.removeBookmark(bookmark.url)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun clearBrowserData(webView: WebView?) {
        webView?.clearCache(true)
        webView?.clearHistory()
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        _uiState.update { it.copy(consoleMessage = "Browser cache, cookies & history cleared") }
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
