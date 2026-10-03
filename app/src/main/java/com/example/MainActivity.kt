package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.data.model.UserScript
import com.example.ui.browser.BrowserUiState
import com.example.ui.browser.BrowserViewModel
import com.example.ui.browser.BrowserWebView
import com.example.ui.components.BookmarksHistorySheet
import com.example.ui.components.BottomNavBar
import com.example.ui.components.BrowserSettingsSheet
import com.example.ui.components.FindInPageBar
import com.example.ui.components.JsConsoleDialog
import com.example.ui.components.ScriptStoreSheet
import com.example.ui.components.SpeedDialHome
import com.example.ui.components.SslInfoDialog
import com.example.ui.components.TabSwitcherSheet
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
                    onExecuteCustomJs = { jsCode ->
                        executeCustomJsCode(jsCode)
                    },
                    onSharePage = { url, title ->
                        sharePage(url, title)
                    },
                    onPrintWebPage = {
                        printCurrentPage()
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

    private fun executeCustomJsCode(jsCode: String) {
        val wv = webViewInstance ?: return
        wv.evaluateJavascript(jsCode) { result ->
            val output = result ?: "undefined"
            viewModel.recordJsConsole("LOG", "Eval result => $output")
            Toast.makeText(this, "JS Result: $output", Toast.LENGTH_SHORT).show()
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

    private fun printCurrentPage() {
        val wv = webViewInstance
        if (wv == null) {
            Toast.makeText(this, "No webpage to print", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val printManager = getSystemService(Context.PRINT_SERVICE) as? PrintManager
            val jobName = "Indoweb Document ${System.currentTimeMillis()}"
            val printAdapter = wv.createPrintDocumentAdapter(jobName)
            printManager?.print(jobName, printAdapter, PrintAttributes.Builder().build())
        } catch (e: Exception) {
            Toast.makeText(this, "Print error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun IndowebApp(
    viewModel: BrowserViewModel,
    onWebViewCreated: (WebView) -> Unit,
    onOpenFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Boolean,
    onRunScriptOnWebView: (UserScript) -> Unit,
    onExecuteCustomJs: (String) -> Unit,
    onSharePage: (String, String) -> Unit,
    onPrintWebPage: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val allScripts by viewModel.allScripts.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val history by viewModel.history.collectAsState()
    val executionLogs by viewModel.executionLogs.collectAsState()
    val jsConsoleLogs by viewModel.jsConsoleLogs.collectAsState()

    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Show console feedback if any
    LaunchedEffect(uiState.consoleMessage) {
        uiState.consoleMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissConsoleMessage()
        }
    }

    // Sync active tab URL loading
    LaunchedEffect(activeTabId, uiState.currentUrl) {
        if (uiState.currentUrl.isNotBlank() && uiState.currentUrl != activeWebView?.url) {
            activeWebView?.loadUrl(uiState.currentUrl)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!uiState.isAddressBarAtBottom) {
                Column {
                    TopUrlBar(
                        uiState = uiState,
                        tabCount = tabs.size,
                        onUrlChange = { viewModel.updateInputUrl(it) },
                        onNavigate = { targetUrl ->
                            viewModel.loadUrl(targetUrl)
                            activeWebView?.loadUrl(viewModel.uiState.value.currentUrl)
                        },
                        onReload = { activeWebView?.reload() },
                        onStopLoading = { activeWebView?.stopLoading() },
                        onOpenScripts = { viewModel.setScriptsSheetVisible(true) },
                        onOpenTabSwitcher = { viewModel.setTabSwitcherVisible(true) },
                        onShowSslInfo = { viewModel.setShowSslDialog(true) }
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
            }
        },
        bottomBar = {
            Column {
                if (uiState.isAddressBarAtBottom) {
                    TopUrlBar(
                        uiState = uiState,
                        tabCount = tabs.size,
                        onUrlChange = { viewModel.updateInputUrl(it) },
                        onNavigate = { targetUrl ->
                            viewModel.loadUrl(targetUrl)
                            activeWebView?.loadUrl(viewModel.uiState.value.currentUrl)
                        },
                        onReload = { activeWebView?.reload() },
                        onStopLoading = { activeWebView?.stopLoading() },
                        onOpenScripts = { viewModel.setScriptsSheetVisible(true) },
                        onOpenTabSwitcher = { viewModel.setTabSwitcherVisible(true) },
                        onShowSslInfo = { viewModel.setShowSslDialog(true) }
                    )
                }

                BottomNavBar(
                    uiState = uiState,
                    tabCount = tabs.size,
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
                    onOpenTabSwitcher = {
                        viewModel.setTabSwitcherVisible(true)
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
                    onToggleReaderMode = {
                        viewModel.toggleReaderMode()
                    },
                    onOpenJsConsole = {
                        viewModel.setJsConsoleVisible(true)
                    },
                    onOpenScriptStore = {
                        viewModel.setScriptStoreVisible(true)
                    },
                    onOpenSettings = {
                        viewModel.setSettingsSheetVisible(true)
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
                    onOpenScriptStore = {
                        viewModel.setScriptStoreVisible(true)
                    },
                    onToggleScript = { id, enabled ->
                        viewModel.toggleScript(id, enabled)
                    }
                )
            }

            // Floating Media Sniffer Download Pill
            if (!uiState.detectedMediaUrl.isNullOrBlank()) {
                val mediaUrl = uiState.detectedMediaUrl!!
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 20.dp, end = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = Color(0xFFA5B4FC),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.width(160.dp)) {
                            Text(
                                text = "Media Detected",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = uiState.detectedMediaTitle ?: "Playable Video/Audio",
                                fontSize = 10.sp,
                                color = Color(0xFFC7D2FE),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))

                        // Download Action
                        IconButton(
                            onClick = {
                                val fileName = "video_${System.currentTimeMillis()}.mp4"
                                try {
                                    val request = android.app.DownloadManager.Request(Uri.parse(mediaUrl)).apply {
                                        setDescription("Downloading video from Indoweb...")
                                        setTitle(fileName)
                                        setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                                    }
                                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
                                    dm?.enqueue(request)
                                    Toast.makeText(context, "Downloading video...", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download Video",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Close Pill
                        IconButton(
                            onClick = { viewModel.clearDetectedMedia() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
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

    // Tab Switcher Sheet
    if (uiState.isTabSwitcherVisible) {
        TabSwitcherSheet(
            tabs = tabs,
            activeTabId = activeTabId,
            onSelectTab = { tabId ->
                viewModel.switchTab(tabId)
            },
            onCloseTab = { tabId ->
                viewModel.closeTab(tabId)
            },
            onNewTab = { isIncognito ->
                viewModel.openNewTab(url = "", isIncognito = isIncognito)
                viewModel.setTabSwitcherVisible(false)
            },
            onCloseAllTabs = {
                viewModel.closeAllTabs()
            },
            onDismiss = {
                viewModel.setTabSwitcherVisible(false)
            }
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

    // Script Store Sheet
    if (uiState.isScriptStoreVisible) {
        ScriptStoreSheet(
            installedScripts = allScripts,
            onInstallScript = { storeScript ->
                viewModel.installStoreScript(storeScript)
            },
            onDismiss = {
                viewModel.setScriptStoreVisible(false)
            }
        )
    }

    // JS Console & Inspector Dialog
    if (uiState.isJsConsoleVisible) {
        JsConsoleDialog(
            logs = jsConsoleLogs,
            onExecuteJs = { jsCode ->
                onExecuteCustomJs(jsCode)
            },
            onClearLogs = {
                viewModel.clearJsConsole()
            },
            onDismiss = {
                viewModel.setJsConsoleVisible(false)
            }
        )
    }

    // Browser Settings Sheet
    if (uiState.isSettingsSheetVisible) {
        BrowserSettingsSheet(
            isAddressBarAtBottom = uiState.isAddressBarAtBottom,
            searchEngine = uiState.searchEngine,
            currentUserAgent = uiState.userAgentType,
            onToggleAddressBarPosition = {
                viewModel.toggleAddressBarPosition()
            },
            onSelectSearchEngine = { engine ->
                viewModel.setSearchEngine(engine)
            },
            onSelectUserAgent = { uaType ->
                viewModel.setUserAgent(uaType)
            },
            onPrintToPdf = {
                onPrintWebPage()
            },
            onClearData = {
                viewModel.clearBrowserData(activeWebView)
            },
            onDismiss = {
                viewModel.setSettingsSheetVisible(false)
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
