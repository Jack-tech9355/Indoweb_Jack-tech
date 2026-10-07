package com.example

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Rational
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.data.model.UserScript
import com.example.ui.browser.BrowserViewModel
import com.example.ui.browser.BrowserWebView
import com.example.ui.components.BookmarksHistorySheet
import com.example.ui.components.BottomNavBar
import com.example.ui.components.BrowserSettingsSheet
import com.example.ui.components.FindInPageBar
import com.example.ui.components.JsConsoleDialog
import com.example.ui.components.ScriptStoreSheet
import com.example.ui.components.SiteShieldSheet
import com.example.ui.components.SpeedDialHome
import com.example.ui.components.TabSwitcherSheet
import com.example.ui.components.TopUrlBar
import com.example.ui.components.WebPermissionDialog
import com.example.ui.scripts.ScriptEditorDialog
import com.example.ui.scripts.UserScriptInstallDialog
import com.example.ui.scripts.UserScriptsScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels()
    private var activeWebViewInstance: WebView? = null
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private var isFullscreenMode by mutableStateOf(false)
    private var customFullscreenView by mutableStateOf<View?>(null)
    private var customFullscreenCallback by mutableStateOf<WebChromeClient.CustomViewCallback?>(null)

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

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            insets
        }

        handleIntent(intent)

        setContent {
            MyApplicationTheme {
                IndowebApp(
                    viewModel = viewModel,
                    isFullscreen = isFullscreenMode,
                    fullscreenView = customFullscreenView,
                    onActiveWebViewChanged = { webView ->
                        activeWebViewInstance = webView
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
                    onShowFullscreen = { view, callback ->
                        showFullscreenVideo(view, callback)
                    },
                    onHideFullscreen = {
                        hideFullscreenVideo()
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
                    },
                    onSaveMhtml = {
                        saveCurrentPageAsMhtml()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isFullscreenMode) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {}
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            hideSystemBars()
        } else if (!isFullscreenMode) {
            showSystemBars()
        }
    }

    private fun showFullscreenVideo(view: View, callback: WebChromeClient.CustomViewCallback) {
        customFullscreenView = view
        customFullscreenCallback = callback
        isFullscreenMode = true
        hideSystemBars()
    }

    private fun hideFullscreenVideo() {
        customFullscreenCallback?.onCustomViewHidden()
        customFullscreenCallback = null
        customFullscreenView = null
        isFullscreenMode = false
        showSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun showSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.dataString?.let { url ->
                viewModel.loadUrl(url)
            }
        }
    }

    private fun runScriptImmediately(script: UserScript) {
        val wv = activeWebViewInstance
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
        val wv = activeWebViewInstance ?: return
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
        val wv = activeWebViewInstance
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

    private fun saveCurrentPageAsMhtml() {
        val wv = activeWebViewInstance
        if (wv == null) {
            Toast.makeText(this, "No active webpage to save", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val title = viewModel.uiState.value.pageTitle.replace("[^a-zA-Z0-9]".toRegex(), "_").take(30).ifBlank { "offline_page" }
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, "webpage_${title}_${System.currentTimeMillis()}.mhtml")
            wv.saveWebArchive(file.absolutePath)
            Toast.makeText(this, "Saved offline web archive: ${file.name}", Toast.LENGTH_LONG).show()
            viewModel.recordJsConsole("INFO", "Saved offline MHTML archive to ${file.absolutePath}")
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save offline page: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun IndowebApp(
    viewModel: BrowserViewModel,
    isFullscreen: Boolean,
    fullscreenView: View?,
    onActiveWebViewChanged: (WebView) -> Unit,
    onOpenFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Boolean,
    onShowFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onHideFullscreen: () -> Unit,
    onRunScriptOnWebView: (UserScript) -> Unit,
    onExecuteCustomJs: (String) -> Unit,
    onSharePage: (String, String) -> Unit,
    onPrintWebPage: () -> Unit,
    onSaveMhtml: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val allScripts by viewModel.allScripts.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val history by viewModel.history.collectAsState()
    val executionLogs by viewModel.executionLogs.collectAsState()
    val jsConsoleLogs by viewModel.jsConsoleLogs.collectAsState()
    val pendingPermissionPrompt by viewModel.pendingPermissionPrompt.collectAsState()

    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Handle Back button during fullscreen video
    BackHandler(enabled = isFullscreen) {
        onHideFullscreen()
    }

    // Show console feedback if any
    LaunchedEffect(uiState.consoleMessage) {
        uiState.consoleMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissConsoleMessage()
        }
    }

    if (isFullscreen && fullscreenView != null) {
        // True Edge-to-Edge Fullscreen Video container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { fullscreenView },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
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
                            },
                            onReload = { activeWebView?.reload() },
                            onStopLoading = { activeWebView?.stopLoading() },
                            onOpenScripts = { viewModel.setScriptsSheetVisible(true) },
                            onOpenTabSwitcher = { viewModel.setTabSwitcherVisible(true) },
                            onShowSslInfo = { viewModel.setSiteShieldVisible(true) }
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
                            },
                            onReload = { activeWebView?.reload() },
                            onStopLoading = { activeWebView?.stopLoading() },
                            onOpenScripts = { viewModel.setScriptsSheetVisible(true) },
                            onOpenTabSwitcher = { viewModel.setTabSwitcherVisible(true) },
                            onShowSslInfo = { viewModel.setSiteShieldVisible(true) }
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
                        onOpenSiteShield = {
                            viewModel.setSiteShieldVisible(true)
                        },
                        onSaveMhtml = {
                            onSaveMhtml()
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
                // Multi-Tab Persistent WebView Architecture
                BrowserWebView(
                    viewModel = viewModel,
                    onActiveWebViewChanged = { wv ->
                        activeWebView = wv
                        onActiveWebViewChanged(wv)
                    },
                    onOpenFileChooser = onOpenFileChooser,
                    onShowFullscreen = onShowFullscreen,
                    onHideFullscreen = onHideFullscreen,
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
                                    text = uiState.detectedMediaTitle ?: "Playable Media Stream",
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
                                    val isHls = mediaUrl.contains(".m3u8", ignoreCase = true)
                                    val ext = if (isHls) "m3u8" else "mp4"
                                    val fileName = "media_${System.currentTimeMillis()}.$ext"
                                    try {
                                        val request = android.app.DownloadManager.Request(Uri.parse(mediaUrl)).apply {
                                            setDescription("Downloading media from Indoweb...")
                                            setTitle(fileName)
                                            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                                        }
                                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
                                        dm?.enqueue(request)
                                        Toast.makeText(context, "Downloading media...", Toast.LENGTH_SHORT).show()
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

                            // Dismiss Action
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
    }

    // Interactive Web Permission Dialog (Camera, Mic, Location)
    pendingPermissionPrompt?.let { prompt ->
        WebPermissionDialog(
            prompt = prompt,
            onGrant = { remember ->
                viewModel.grantPermissionPrompt(prompt, remember)
            },
            onDeny = { remember ->
                viewModel.denyPermissionPrompt(prompt, remember)
            }
        )
    }

    // Site Shield & Privacy Sheet
    if (uiState.isSiteShieldVisible) {
        SiteShieldSheet(
            url = uiState.currentUrl,
            isSecure = uiState.isSecure,
            isAdBlockerEnabled = uiState.isAdBlockerEnabled,
            blockedAdsCount = uiState.blockedAdsCount,
            originPermissions = viewModel.getOriginPermissions(uiState.currentUrl),
            onToggleAdBlocker = { viewModel.toggleAdBlocker() },
            onSetPermission = { type, allowed -> viewModel.setOriginPermission(uiState.currentUrl, type, allowed) },
            onClearSiteData = { origin -> viewModel.clearOriginData(origin, activeWebView) },
            onDismiss = { viewModel.setSiteShieldVisible(false) }
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

    // UserScripts Screen Sheet
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
            onImportScript = { importedScript ->
                viewModel.importUserScript(importedScript)
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

    // Tampermonkey .user.js Auto-Install Dialog
    uiState.pendingInstallScript?.let { scriptToInstall ->
        UserScriptInstallDialog(
            script = scriptToInstall,
            onInstall = { script ->
                viewModel.confirmInstallPendingScript(script)
            },
            onDismiss = {
                viewModel.dismissInstallPendingScript()
            }
        )
    }

    // Script Store Sheet (Curated + GreasyFork Live Search)
    if (uiState.isScriptStoreVisible) {
        ScriptStoreSheet(
            installedScripts = allScripts,
            greasyForkResults = uiState.greasyForkResults,
            isSearchingGreasyFork = uiState.isSearchingGreasyFork,
            onSearchGreasyFork = { query ->
                viewModel.searchGreasyFork(query)
            },
            onInstallStoreScript = { storeScript ->
                viewModel.installStoreScript(storeScript)
            },
            onInstallGreasyForkScript = { greasyForkScript ->
                viewModel.installGreasyForkScript(greasyForkScript)
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
            isAdBlockerEnabled = uiState.isAdBlockerEnabled,
            onToggleAddressBarPosition = {
                viewModel.toggleAddressBarPosition()
            },
            onSelectSearchEngine = { engine ->
                viewModel.setSearchEngine(engine)
            },
            onSelectUserAgent = { uaType ->
                viewModel.setUserAgent(uaType)
            },
            onToggleAdBlocker = {
                viewModel.toggleAdBlocker()
            },
            onPrintToPdf = {
                onPrintWebPage()
            },
            onSaveMhtml = {
                onSaveMhtml()
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
