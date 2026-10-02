package com.example.data.repository

import com.example.data.local.BookmarkDao
import com.example.data.local.HistoryDao
import com.example.data.local.UserScriptDao
import com.example.data.model.Bookmark
import com.example.data.model.HistoryItem
import com.example.data.model.UserScript
import com.example.ui.scripts.ScriptTemplates
import kotlinx.coroutines.flow.Flow

class BrowserRepository(
    private val userScriptDao: UserScriptDao,
    private val bookmarkDao: BookmarkDao,
    private val historyDao: HistoryDao
) {
    val allScripts: Flow<List<UserScript>> = userScriptDao.getAllScripts()
    val enabledScripts: Flow<List<UserScript>> = userScriptDao.getEnabledScripts()
    val allBookmarks: Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks()
    val recentHistory: Flow<List<HistoryItem>> = historyDao.getRecentHistory()

    suspend fun ensureDefaultScripts() {
        val count = userScriptDao.getCount()
        if (count == 0) {
            userScriptDao.insertScripts(ScriptTemplates.BUILT_IN_SCRIPTS)
        }
    }

    suspend fun insertScript(script: UserScript) {
        userScriptDao.insertScript(script)
    }

    suspend fun updateScript(script: UserScript) {
        userScriptDao.updateScript(script)
    }

    suspend fun deleteScript(id: String) {
        userScriptDao.deleteScriptById(id)
    }

    suspend fun toggleScript(id: String, enabled: Boolean) {
        userScriptDao.toggleScript(id, enabled)
    }

    suspend fun resetToDefaultScripts() {
        userScriptDao.insertScripts(ScriptTemplates.BUILT_IN_SCRIPTS)
    }

    fun isBookmarked(url: String): Flow<Boolean> {
        return bookmarkDao.isBookmarked(url)
    }

    suspend fun addBookmark(title: String, url: String) {
        bookmarkDao.insertBookmark(Bookmark(title = title.ifBlank { url }, url = url))
    }

    suspend fun removeBookmark(url: String) {
        bookmarkDao.deleteByUrl(url)
    }

    suspend fun addHistory(title: String, url: String) {
        if (url.isNotBlank() && !url.startsWith("data:") && !url.startsWith("about:")) {
            historyDao.insertHistory(HistoryItem(title = title.ifBlank { url }, url = url))
        }
    }

    suspend fun clearHistory() {
        historyDao.clearAllHistory()
    }
}
