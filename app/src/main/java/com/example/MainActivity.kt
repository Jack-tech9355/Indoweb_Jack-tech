package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.example.data.model.UserScript
import com.example.ui.browser.BrowserUiState
import com.example.ui.browser.BrowserViewModel
import com.example.ui.browser.BrowserWebView
import com.example.ui.components.BookmarksHistorySheet
import com.example.ui.components.BottomNavBar
import com.example.ui.components.FindInPageBar
import com.example.ui.components.SpeedDialHome
import com.example.ui.components.SslInfoDialog
import com.example.ui.components.TopUrlBar
import com.example.ui.scripts.ScriptEditorDialog
import com.example.ui.scripts.UserScriptsScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels()
    private var webViewInstance: WebView? = null
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (fileUploadCallback == null) return@registerForActivityResult

        val results: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                Array(count) { i -> data.clipData!!.getItemAt(i).uri }
            } else if (data?.data != null) {
                arrayOf(data.data!!)
            } else {
                null
            }
        } else {
            null
        }

        fileUploadCallback?.onReceiveValue(results)
        fileUploadCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        // Handle incoming URL intents (from external browser clicks or shares)
        handleIntent(intent)

        setContent {
            MyApplicationTheme {
                IndowebApp(
                    viewModel = viewModel,
                    onWebViewCreated = { webView ->
                        webViewInstance = webView
                    },
                    onOpenFileChooser = { callback, params ->
                        fileUploadCallback?.onReceiveValue(null)
                        fileUploadCallback = callback
                        try {
                            val intent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                            }
                            filePickerLauncher.launch(intent)
                            true
                        } catch (e: Exception) {
                            fileUploadCallback = null
                            false
                        }
                    },
                    onRunScriptOnWebView = { script ->
                        runScriptImmediately(script)
                    },
                    onSharePage = { url, title ->
                        sharePage(url, title)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.dataString?.let { url ->
                viewModel.loadUrl(url)
            }
        }
    }

    private fun runScriptImmediately(script: UserScript) {
        val wv = webViewInstance
        if (wv == null) {
            Toast.makeText(this, "No active web page loaded", Toast.LENGTH_SHORT).show()
            return
        }

        val code = """
            (function() {
                try {
                    ${script.scriptCode}
                    return 'Executed: ${script.name.replace("'", "\\'")}';
                } catch(err) {
                    return 'ERROR: ' + err.message;
                }
            })();
        """.trimIndent()

        wv.evaluateJavascript(code) { result ->
            val msg = "Result: ${result ?: "Executed"}"
            Toast.makeText(this, "${script.name} executed", Toast.LENGTH_SHORT).show()
            viewModel.recordScriptLog(
                scriptName = script.name,
                url = wv.url ?: "Current Page",
                isSuccess = true,
                message = msg
            )
        }
    }

    private fun sharePage(url: String, title: String) {
        if (url.isBlank()) return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
        }
        startActivity(Intent.createChooser(shareIntent, "Share Web Page"))
    }
}

@Composable
fun IndowebApp(
    viewModel: BrowserViewModel,
    onWebViewCreated: (WebView) -> Unit,
    onOpenFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Boolean,
    onRunScriptOnWebView: (UserScript) -> Unit,
    onSharePage: (String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val allScripts by viewModel.allScripts.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val history by viewModel.history.collectAsState()
    val executionLogs by viewModel.executionLogs.collectAsState()

    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Show console feedback if any
    LaunchedEffect(uiState.consoleMessage) {
        uiState.consoleMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissConsoleMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopUrlBar(
                    uiState = uiState,
                    onUrlChange = { viewModel.updateInputUrl(it) },
                    onNavigate = { targetUrl ->
                        viewModel.loadUrl(targetUrl)
                        activeWebView?.loadUrl(viewModel.uiState.value.currentUrl)
                    },
                    onReload = {
                        activeWebView?.reload()
                    },
                    onStopLoading = {
                        activeWebView?.stopLoading()
                    },
                    onOpenScripts = {
                        viewModel.setScriptsSheetVisible(true)
                    },
                    onShowSslInfo = {
                        viewModel.setShowSslDialog(true)
                    }
                )

                AnimatedVisibility(
                    visible = uiState.isFindBarVisible,
                    enter = slideInVertically(),
                    exit = slideOutVertically()
                ) {
                    FindInPageBar(
                        query = uiState.findQuery,
                        onQueryChange = { viewModel.setFindQuery(it) },
                        onFindNext = { activeWebView?.findNext(true) },
                        onFindPrevious = { activeWebView?.findNext(false) },
                        onClose = {
                            viewModel.setFindBarVisible(false)
                            viewModel.setFindQuery("")
                            activeWebView?.clearMatches()
                        }
                    )
                }
            }
        },
        bottomBar = {
            BottomNavBar(
                uiState = uiState,
                onBack = {
                    if (activeWebView?.canGoBack() == true) {
                        activeWebView?.goBack()
                    }
                },
                onForward = {
                    if (activeWebView?.canGoForward() == true) {
                        activeWebView?.goForward()
                    }
                },
                onHome = {
                    viewModel.loadUrl("")
                    activeWebView?.loadUrl("about:blank")
                },
                onOpenScripts = {
                    viewModel.setScriptsSheetVisible(true)
                },
                onOpenBookmarks = {
                    viewModel.setBookmarksSheetVisible(true)
                },
                onToggleBookmark = {
                    viewModel.toggleBookmarkCurrentPage()
                },
                onToggleDesktop = {
                    viewModel.toggleDesktopMode()
                },
                onFindInPage = {
                    viewModel.setFindBarVisible(true)
                },
                onShare = {
                    onSharePage(uiState.currentUrl, uiState.pageTitle)
                },
                onClearData = {
                    viewModel.clearBrowserData(activeWebView)
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Web View container
            BrowserWebView(
                viewModel = viewModel,
                onWebViewCreated = { wv ->
                    activeWebView = wv
                    onWebViewCreated(wv)
                },
                onOpenFileChooser = onOpenFileChooser,
                onDownloadTriggered = { fileName, url ->
                    scope.launch {
                        snackbarHostState.showSnackbar("Downloading: $fileName")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Speed Dial / Home overlay when no webpage is loaded
            if (uiState.currentUrl.isBlank() || uiState.currentUrl == "about:blank") {
                SpeedDialHome(
                    scripts = allScripts,
                    searchEngine = uiState.searchEngine,
                    onSelectSearchEngine = { viewModel.setSearchEngine(it) },
                    onOpenUrl = { url ->
                        viewModel.loadUrl(url)
                        activeWebView?.loadUrl(url)
                    },
                    onOpenScripts = {
                        viewModel.setScriptsSheetVisible(true)
                    },
                    onToggleScript = { id, enabled ->
                        viewModel.toggleScript(id, enabled)
                    }
                )
            }
        }
    }

    // SSL Security Dialog
    if (uiState.showSslDialog) {
        SslInfoDialog(
            url = uiState.currentUrl,
            isSecure = uiState.isSecure,
            onDismiss = { viewModel.setShowSslDialog(false) }
        )
    }

    // UserScripts Sheet
    if (uiState.isScriptsSheetVisible) {
        UserScriptsScreen(
            scripts = allScripts,
            logs = executionLogs,
            currentWebUrl = uiState.currentUrl,
            onToggleScript = { id, enabled ->
                viewModel.toggleScript(id, enabled)
            },
            onEditScript = { script ->
                viewModel.setScriptBeingEdited(script)
            },
            onDeleteScript = { id ->
                viewModel.deleteScript(id)
            },
            onAddNewScript = {
                viewModel.setAddingScript(true)
            },
            onResetDefaults = {
                viewModel.resetDefaultScripts()
            },
            onRunScriptNow = { script ->
                onRunScriptOnWebView(script)
            },
            onDismiss = {
                viewModel.setScriptsSheetVisible(false)
            }
        )
    }

    // Script Add / Edit Dialog
    if (uiState.isAddingScript || uiState.scriptBeingEdited != null) {
        ScriptEditorDialog(
            initialScript = uiState.scriptBeingEdited,
            onSave = { script ->
                viewModel.saveScript(script)
            },
            onDismiss = {
                viewModel.setAddingScript(false)
                viewModel.setScriptBeingEdited(null)
            }
        )
    }

    // Bookmarks & History Sheet
    if (uiState.isBookmarksSheetVisible) {
        BookmarksHistorySheet(
            bookmarks = bookmarks,
            history = history,
            onSelectUrl = { selectedUrl ->
                viewModel.loadUrl(selectedUrl)
                activeWebView?.loadUrl(selectedUrl)
            },
            onDeleteBookmark = { bookmark ->
                viewModel.deleteBookmark(bookmark)
            },
            onClearHistory = {
                viewModel.clearHistory()
            },
            onDismiss = {
                viewModel.setBookmarksSheetVisible(false)
            }
        )
    }
}
