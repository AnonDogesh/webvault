package com.webvault.browser

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.webvault.browser.ui.theme.AppBackground
import com.webvault.browser.ui.theme.AppSurface
import com.webvault.browser.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch
import java.util.UUID

private val httpOnlyDomains = setOf("neverssl.com", "example.com")
private val videoExtensions = listOf(".mp4", ".m3u8", ".webm", ".mkv", ".ts", ".avi", ".mov")
private val defaultSpeedDials = listOf(
    "Google" to "https://www.google.com",
    "YouTube" to "https://www.youtube.com",
    "Reddit" to "https://www.reddit.com",
    "GitHub" to "https://www.github.com",
    "Wikipedia" to "https://www.wikipedia.org",
    "Twitter" to "https://www.twitter.com",
    "Amazon" to "https://www.amazon.com",
    "Gmail" to "https://mail.google.com"
)
private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

data class HistorySite(val title: String, val url: String)

private enum class BrowserContextMenuType { Link, Image }

private data class BrowserContextMenu(
    val type: BrowserContextMenuType,
    val url: String
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    modifier: Modifier = Modifier,
    showHomeOverlay: Boolean = false,
    onNavigateToBrowser: () -> Unit = {},
    videoSnifferViewModel: VideoSnifferViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val tabs by TabManager.tabs.collectAsState()
    val activeTabId by TabManager.activeTabId.collectAsState()
    val activeTab = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    var addressBarText by remember(activeTabId) { mutableStateOf(activeTab.pendingUrl.ifBlank { activeTab.url }) }
    var isSecure by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var jsAllowed by remember { mutableStateOf(true) }
    var showTabsSheet by remember { mutableStateOf(false) }
    var showBrowserMenu by remember { mutableStateOf(false) }
    var showSearchEngineMenu by remember { mutableStateOf(false) }
    var showJsMenu by remember { mutableStateOf(false) }
    var showVideoListSheet by remember { mutableStateOf(false) }
    var browserContextMenu by remember { mutableStateOf<BrowserContextMenu?>(null) }
    val recentHistory = remember { mutableStateOf<List<HistorySite>>(emptyList()) }

    val adBlockEnabled by AppPreferences.adBlockEnabledFlow(context).collectAsState(initial = true)
    val httpsEverywhereEnabled by AppPreferences.httpsEverywhereEnabledFlow(context).collectAsState(initial = true)
    val videoSnifferEnabled by AppPreferences.videoSnifferEnabledFlow(context).collectAsState(initial = true)
    val defaultSearchEngineId by AppPreferences.defaultSearchEngineFlow(context).collectAsState(initial = defaultSearchEngine().id)
    val blockedCount by AdBlocker.blockedCount.collectAsState()
    val detectedVideos by videoSnifferViewModel.videos.collectAsState()

    val bridge = remember(videoSnifferViewModel) { VideoSnifferBridge(videoSnifferViewModel) }

    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var attachedTabId by remember { mutableStateOf(activeTabId) }

    LaunchedEffect(Unit) {
        AdBlocker.initialize(context)
    }

    LaunchedEffect(activeTabId) {
        val tab = TabManager.tabs.value.firstOrNull { it.id == activeTabId }
        addressBarText = tab?.pendingUrl?.ifBlank { tab.url }.orEmpty()
        isLoading = tab?.pendingUrl?.isNotBlank() == true
        isSecure = (tab?.url ?: "").startsWith("https://")
    }

    LaunchedEffect(activeTabId, defaultSearchEngineId) {
        if (
            activeTab.url.isBlank() &&
            activeTab.pendingUrl.isBlank() &&
            activeTab.searchEngineId != defaultSearchEngineId
        ) {
            TabManager.updateActiveTab(searchEngineId = defaultSearchEngineId)
        }
    }

    LaunchedEffect(activeTabId, activeTab.pendingUrl) {
        val pending = activeTab.pendingUrl
        if (pending.isNotBlank()) {
            webViewRef.value?.loadUrl(pending, userAgentHeaders(activeTab.isDesktopMode))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.let { webView ->
                val state = Bundle()
                webView.saveState(state)
                TabManager.saveState(attachedTabId, state)
            }
        }
    }

    val showHomePage = showHomeOverlay || (activeTab.url.isBlank() && activeTab.pendingUrl.isBlank())

    val onSubmitUrl: (String) -> Unit = { input ->
        val target = normalizeToUrl(input, activeTab.searchEngineId)
        if (target.isNotBlank()) {
            addressBarText = target
            isLoading = true
            loadProgress = 0.05f
            TabManager.submitUrl(target)
            onNavigateToBrowser()
        }
    }

    BackHandler(enabled = webViewRef.value?.canGoBack() == true) {
        webViewRef.value?.goBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AddressBar(
                url = addressBarText,
                isSecure = isSecure,
                tabCount = tabs.size,
                isLoading = isLoading,
                blockedCount = blockedCount,
                jsAllowed = jsAllowed,
                searchEngine = searchEngineById(activeTab.searchEngineId),
                searchEngineMenuExpanded = showSearchEngineMenu,
                onDismissSearchEngineMenu = { showSearchEngineMenu = false },
                onSelectSearchEngine = { engine ->
                    TabManager.updateActiveTab(searchEngineId = engine.id)
                    showSearchEngineMenu = false
                },
                onUrlChange = { addressBarText = it },
                onUrlSubmit = { onSubmitUrl(addressBarText) },
                onRefreshOrStop = {
                    webViewRef.value?.let { wv ->
                        if (isLoading) wv.stopLoading() else wv.reload()
                    }
                },
                onTabsClick = { showTabsSheet = true },
                onSearchEngineClick = { showSearchEngineMenu = true },
                onLockLongPress = { showJsMenu = true },
                onSetJavaScriptAllowed = { allowed ->
                    scope.launch {
                        val host = runCatching { Uri.parse(addressBarText).host.orEmpty() }.getOrDefault("")
                        AppPreferences.setJavaScriptAllowed(context, host, allowed)
                        jsAllowed = allowed
                        webViewRef.value?.settings?.javaScriptEnabled = allowed
                        webViewRef.value?.reload()
                    }
                },
                showJsMenu = showJsMenu,
                onDismissJsMenu = { showJsMenu = false }
            )

            if (isLoading) {
                LinearProgressIndicator(
                    progress = { loadProgress.coerceIn(0f, 1f) },
                    color = PrimaryBlue,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.domStorageEnabled = true
                            settings.javaScriptEnabled = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.useWideViewPort = false
                            settings.loadWithOverviewMode = false
                            settings.userAgentString = MOBILE_USER_AGENT
                            addJavascriptInterface(bridge, "Android")
                            setOnLongClickListener {
                                val result = hitTestResult ?: return@setOnLongClickListener false
                                val extra = result.extra?.takeIf { it.isNotBlank() } ?: return@setOnLongClickListener false
                                browserContextMenu = when (result.type) {
                                    WebView.HitTestResult.SRC_ANCHOR_TYPE,
                                    WebView.HitTestResult.EMAIL_TYPE,
                                    WebView.HitTestResult.PHONE_TYPE -> BrowserContextMenu(BrowserContextMenuType.Link, extra)

                                    WebView.HitTestResult.IMAGE_TYPE,
                                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> BrowserContextMenu(BrowserContextMenuType.Image, extra)

                                    else -> null
                                }
                                browserContextMenu != null
                            }
                            isLongClickable = true

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress / 100f
                                    if (newProgress == 100) isLoading = false
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank()) {
                                        TabManager.updateActiveTab(title = title)
                                    }
                                }

                                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                                    if (icon != null) {
                                        TabManager.updateActiveTab(favicon = icon)
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    loadProgress = 0.1f
                                    url?.let {
                                        addressBarText = it
                                        isSecure = it.startsWith("https://")
                                        TabManager.commitUrl(it)
                                    }
                                    scope.launch {
                                        val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
                                        jsAllowed = AppPreferences.isJavaScriptAllowed(context, host)
                                        view?.settings?.javaScriptEnabled = jsAllowed
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    loadProgress = 1f
                                    if (videoSnifferEnabled) {
                                        injectVideoSnifferScript(view)
                                    }
                                    url?.let {
                                        val title = runCatching { Uri.parse(it).host.orEmpty() }
                                            .getOrDefault(it)
                                            .removePrefix("www.")
                                            .ifBlank { it }
                                        recentHistory.value = (listOf(HistorySite(title, it)) + recentHistory.value)
                                            .distinctBy { site -> site.url }
                                            .take(5)
                                    }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val newUrl = request?.url?.toString().orEmpty()
                                    if (newUrl.isBlank()) return false
                                    val uri = Uri.parse(newUrl)
                                    if (
                                        httpsEverywhereEnabled &&
                                        uri.scheme == "http" &&
                                        !httpOnlyDomains.contains(uri.host.orEmpty())
                                    ) {
                                        view?.loadUrl(uri.buildUpon().scheme("https").build().toString())
                                        return true
                                    }
                                    return false
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): android.webkit.WebResourceResponse? {
                                    val requestUrl = request?.url?.toString().orEmpty()
                                    val lower = requestUrl.lowercase()
                                    val accept = request?.requestHeaders?.entries
                                        ?.firstOrNull { it.key.equals("Accept", true) }
                                        ?.value
                                        ?.lowercase()
                                        .orEmpty()
                                    if (
                                        videoSnifferEnabled &&
                                        (videoExtensions.any { lower.contains(it) } || accept.contains("video/"))
                                    ) {
                                        videoSnifferViewModel.onVideoDetected(
                                            requestUrl,
                                            type = lower.substringAfterLast('.', "video")
                                        )
                                    }
                                    if (adBlockEnabled) return AdBlocker.interceptIfBlocked(requestUrl)
                                    return null
                                }
                            }

                            webViewRef.value = this
                        }
                    },
                    update = { webView ->
                        webViewRef.value = webView

                        if (attachedTabId != activeTabId) {
                            val old = Bundle()
                            webView.saveState(old)
                            TabManager.saveState(attachedTabId, old)
                            attachedTabId = activeTabId

                            val restored = TabManager.getSavedState(activeTabId)
                            if (restored != null) {
                                webView.restoreState(restored)
                            }
                        }

                        val desiredUA = if (activeTab.isDesktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
                        if (webView.settings.useWideViewPort != activeTab.isDesktopMode) {
                            webView.settings.useWideViewPort = activeTab.isDesktopMode
                        }
                        if (webView.settings.loadWithOverviewMode != activeTab.isDesktopMode) {
                            webView.settings.loadWithOverviewMode = activeTab.isDesktopMode
                        }
                        if (webView.settings.userAgentString != desiredUA) {
                            webView.settings.userAgentString = desiredUA
                        }
                    }
                )

                if (isLoading && loadProgress < 0.15f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(AppBackground)
                    )
                }

                if (showHomePage) {
                    HomeScreen(
                        recentHistory = recentHistory.value,
                        onSpeedDialClick = onSubmitUrl,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AppBackground)
                    )
                }
            }
        }

        if (!showHomePage) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            ) {
                FloatingActionButton(
                    onClick = { showBrowserMenu = true },
                    containerColor = PrimaryBlue,
                    contentColor = Color.White
                ) {
                    Text("Menu")
                }

                DropdownMenu(
                    expanded = showBrowserMenu,
                    onDismissRequest = { showBrowserMenu = false },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Desktop site", modifier = Modifier.weight(1f))
                        Switch(
                            checked = activeTab.isDesktopMode,
                            onCheckedChange = { enabled ->
                                TabManager.updateActiveTab(desktopMode = enabled)
                                webViewRef.value?.apply {
                                    applyPresentationMode(enabled)
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showHomePage) {
            FloatingActionButton(
                onClick = { showTabsSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = PrimaryBlue,
                contentColor = Color.White
            ) {
                Text(
                    text = tabs.size.toString(),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        VideoDownloadBanner(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp),
            videos = detectedVideos,
            onDismiss = { url -> videoSnifferViewModel.removeVideo(url) },
            onOpenList = { showVideoListSheet = true },
            onDownload = { video ->
                enqueueDownload(context, video)
                videoSnifferViewModel.removeVideo(video.url)
            }
        )
    }

    if (showVideoListSheet) {
        VideoListSheet(
            videos = detectedVideos,
            onDismiss = { showVideoListSheet = false },
            onDownload = {
                enqueueDownload(context, it)
                videoSnifferViewModel.removeVideo(it.url)
            }
        )
    }

    if (showTabsSheet) {
        TabSheet(
            tabs = tabs,
            activeTabId = activeTabId,
            onSelectTab = { id ->
                TabManager.switchToTab(id)
                showTabsSheet = false
            },
            onCloseTab = { TabManager.closeTab(it) },
            onNewTab = {
                if (!TabManager.openNewTab()) {
                    Toast.makeText(context, "Maximum of 10 tabs reached", Toast.LENGTH_SHORT).show()
                } else {
                    AdBlocker.resetCount()
                    showTabsSheet = false
                }
            },
            onDismiss = { showTabsSheet = false }
        )
    }

    browserContextMenu?.let { menu ->
        BrowserContextMenuSheet(
            menu = menu,
            onDismiss = { browserContextMenu = null },
            onOpenCurrent = { url ->
                browserContextMenu = null
                onSubmitUrl(url)
            },
            onOpenNewTab = { url ->
                browserContextMenu = null
                if (!TabManager.openNewTab()) {
                    Toast.makeText(context, "Maximum of 10 tabs reached", Toast.LENGTH_SHORT).show()
                } else {
                    TabManager.submitUrl(url)
                    onNavigateToBrowser()
                }
            },
            onCopy = { url ->
                copyToClipboard(context, label = "Link", value = url)
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                browserContextMenu = null
            },
            onShare = { url ->
                shareUrl(context, url)
                browserContextMenu = null
            },
            onDownload = { url ->
                enqueueDirectDownload(context, url)
                Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
                browserContextMenu = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddressBar(
    url: String,
    isSecure: Boolean,
    tabCount: Int,
    isLoading: Boolean,
    blockedCount: Int,
    jsAllowed: Boolean,
    searchEngine: SearchEngine,
    searchEngineMenuExpanded: Boolean,
    onDismissSearchEngineMenu: () -> Unit,
    onSelectSearchEngine: (SearchEngine) -> Unit,
    onUrlChange: (String) -> Unit,
    onUrlSubmit: () -> Unit,
    onRefreshOrStop: () -> Unit,
    onTabsClick: () -> Unit,
    onSearchEngineClick: () -> Unit,
    onLockLongPress: () -> Unit,
    onSetJavaScriptAllowed: (Boolean) -> Unit,
    showJsMenu: Boolean,
    onDismissJsMenu: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var showClearButton by remember(url) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .shadow(elevation = 4.dp)
            .background(Color(0xFFF0F6FF))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Connection security",
                tint = if (isSecure) Color(0xFF2E7D32) else Color.Gray,
                modifier = Modifier
                    .size(20.dp)
                    .combinedClickable(onClick = {}, onLongClick = onLockLongPress)
            )
            DropdownMenu(
                expanded = showJsMenu,
                onDismissRequest = onDismissJsMenu,
                shape = RoundedCornerShape(20.dp)
            ) {
                DropdownMenuItem(
                    text = { Text(if (jsAllowed) "Block JavaScript on this site" else "Allow JavaScript") },
                    onClick = {
                        onSetJavaScriptAllowed(!jsAllowed)
                        onDismissJsMenu()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    showClearButton = focusState.isFocused && url.isNotBlank()
                },
            placeholder = { Text("Search or enter URL") },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            trailingIcon = {
                if (showClearButton) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear URL",
                        tint = Color.Gray,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                onUrlChange("")
                                showClearButton = false
                            }
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = Color(0xFFBBBBBB),
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = {
                    focusManager.clearFocus()
                    showClearButton = false
                    onUrlSubmit()
                }
            )
        )

        Spacer(modifier = Modifier.width(6.dp))

        Box {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(searchEngineAccentColor(searchEngine))
                    .clickable(onClick = onSearchEngineClick)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                SearchEngineMark(engine = searchEngine)
            }
            DropdownMenu(
                expanded = searchEngineMenuExpanded,
                onDismissRequest = onDismissSearchEngineMenu,
                shape = RoundedCornerShape(20.dp)
            ) {
                allSearchEngines().forEach { engine ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SearchEngineMark(engine = engine)
                                Text(engine.label)
                            }
                        },
                        onClick = { onSelectSearchEngine(engine) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(PrimaryBlue)
                .clickable(onClick = onTabsClick)
                .padding(0.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(tabCount.toString(), color = Color.White, style = MaterialTheme.typography.labelMedium)
        }

        Spacer(modifier = Modifier.width(6.dp))

        BlockedShieldBadge(count = blockedCount)

        Spacer(modifier = Modifier.width(6.dp))

        Icon(
            if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
            contentDescription = if (isLoading) "Stop loading" else "Refresh",
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onRefreshOrStop)
        )
    }
}

@Composable
private fun SearchEngineMark(engine: SearchEngine) {
    val label = when (engine.id) {
        "duckduckgo" -> "D"
        "startpage" -> "S"
        "bing" -> "B"
        "google" -> "G"
        else -> engine.label.take(1).uppercase()
    }

    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(searchEngineAccentColor(engine)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun BlockedShieldBadge(count: Int) {
    Box(
        modifier = Modifier.size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "🛡", fontSize = 16.sp)
        Text(
            text = count.toString(),
            color = PrimaryBlue,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun searchEngineAccentColor(engine: SearchEngine): Color {
    return when (engine.id) {
        "duckduckgo" -> Color(0xFFFF6B2C)
        "startpage" -> Color(0xFF6C63FF)
        "bing" -> Color(0xFF0AA5D8)
        "google" -> Color(0xFF4285F4)
        else -> PrimaryBlue
    }
}

private fun userAgentHeaders(desktopMode: Boolean): Map<String, String> {
    return mapOf("User-Agent" to if (desktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT)
}

private fun WebView.applyPresentationMode(desktopMode: Boolean) {
    settings.useWideViewPort = desktopMode
    settings.loadWithOverviewMode = desktopMode
    settings.userAgentString = if (desktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT

    val currentUrl = url
    if (!currentUrl.isNullOrBlank()) {
        loadUrl(currentUrl, userAgentHeaders(desktopMode))
    } else {
        reload()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserContextMenuSheet(
    menu: BrowserContextMenu,
    onDismiss: () -> Unit,
    onOpenCurrent: (String) -> Unit,
    onOpenNewTab: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onDownload: (String) -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isImage = menu.type == BrowserContextMenuType.Image
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (isImage) "Image options" else "Link options",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = menu.url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color.Gray
            )
            BrowserContextAction("Open") { onOpenCurrent(menu.url) }
            BrowserContextAction(if (isImage) "View in new tab" else "Open in new tab") { onOpenNewTab(menu.url) }
            BrowserContextAction(if (isImage) "Download image" else "Download link") { onDownload(menu.url) }
            BrowserContextAction(if (isImage) "Copy image link" else "Copy link") { onCopy(menu.url) }
            BrowserContextAction(if (isImage) "Share image link" else "Share link") { onShare(menu.url) }
        }
    }
}

@Composable
private fun BrowserContextAction(
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAFF)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabSheet(
    tabs: List<Tab>,
    activeTabId: Int,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            gridItems(tabs) { tab ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTab(tab.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (tab.id == activeTabId) Color(0xFFE3F2FD) else AppSurface
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (tab.faviconBitmap != null) {
                                Image(
                                    bitmap = tab.faviconBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("T", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                tab.title,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { onCloseTab(tab.id) }) {
                                Icon(Icons.Default.Close, contentDescription = "Close tab")
                            }
                        }
                        Text(
                            tab.pendingUrl.ifBlank { tab.url }.ifBlank { "New Tab" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        Button(
            onClick = onNewTab,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("+ New Tab")
        }
    }
}

@Composable
private fun HomeScreen(
    recentHistory: List<HistorySite>,
    onSpeedDialClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Webvault",
            color = PrimaryBlue,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp, bottom = 48.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            defaultSpeedDials.chunked(4).forEach { rowSites ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowSites.forEach { (name, url) ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(74.dp)
                                .clickable { onSpeedDialClick(url) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = AppSurface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(name.first().uppercaseChar().toString(), color = Color.White)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(name, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Recent History", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(recentHistory.take(5)) { site ->
                Card(
                    modifier = Modifier
                        .width(132.dp)
                        .height(72.dp)
                        .clickable { onSpeedDialClick(site.url) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurface)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(site.title, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(site.url, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoDownloadBanner(
    modifier: Modifier = Modifier,
    videos: List<DetectedVideo>,
    onDismiss: (String) -> Unit,
    onOpenList: () -> Unit,
    onDownload: (DetectedVideo) -> Unit
) {
    if (videos.isEmpty()) return
    val first = videos.first()

    if (videos.size > 1) {
        AssistChip(
            onClick = onOpenList,
            label = { Text("${videos.size} videos found") },
            modifier = modifier
        )
    }

    SwipeToDismissBox(
        state = androidx.compose.material3.rememberSwipeToDismissBoxState(
            positionalThreshold = { it * 0.5f },
            confirmValueChange = {
                onDismiss(first.url)
                true
            }
        ),
        backgroundContent = {}
    ) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PrimaryBlue)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                Text(
                    first.filename,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Button(onClick = { onDownload(first) }) {
                    Text("Download")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoListSheet(
    videos: List<DetectedVideo>,
    onDismiss: () -> Unit,
    onDownload: (DetectedVideo) -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            gridItems(videos) { video ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            video.filename,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Button(onClick = { onDownload(video) }) {
                            Text("Download")
                        }
                    }
                }
            }
        }
    }
}

private fun injectVideoSnifferScript(view: WebView?) {
    val script = """
        (function(){
          function send(u,t){ if(!u) return; try{ Android.onVideoDetected(u,t||'video'); }catch(e){} }
          const patterns=['.mp4','.m3u8','.webm','.mkv','.ts','.avi','.mov'];
          function check(u,t){ if(!u) return; const l=(u+'').toLowerCase(); if(patterns.some(p=>l.includes(p)) || (t&&t.indexOf('video')>=0)) send(u,t); }
          const oopen=XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open=function(m,u){ check(u,'xhr'); return oopen.apply(this,arguments); };
          const ofetch=window.fetch;
          window.fetch=function(input,init){ const u=(typeof input==='string')?input:(input&&input.url); check(u,'fetch'); return ofetch.apply(this,arguments); };
          document.querySelectorAll('video,source').forEach(function(el){ check(el.src||el.currentSrc,'tag'); });
          new MutationObserver(function(ms){ ms.forEach(function(m){ m.addedNodes.forEach(function(n){ if(!n) return; if(n.tagName==='VIDEO'||n.tagName==='SOURCE'){ check(n.src||n.currentSrc,'mutation'); } if(n.querySelectorAll){ n.querySelectorAll('video,source').forEach(function(el){ check(el.src||el.currentSrc,'mutation'); }); } }); }); }).observe(document.documentElement,{childList:true,subtree:true});
        })();
    """.trimIndent()
    view?.evaluateJavascript(script, null)
}

private fun enqueueDownload(context: Context, video: DetectedVideo) {
    val id = UUID.randomUUID().toString()
    val data = Data.Builder()
        .putString(DownloadWorker.KEY_URL, video.url)
        .putString(DownloadWorker.KEY_FILENAME, video.filename)
        .putString(DownloadWorker.KEY_ID, id)
        .build()
    val work = OneTimeWorkRequestBuilder<DownloadWorker>()
        .setInputData(data)
        .build()
    WorkManager.getInstance(context).enqueue(work)
}

private fun enqueueDirectDownload(context: Context, url: String) {
    val cleanUrl = url.substringBefore('#')
    val filename = cleanUrl.substringAfterLast('/').substringBefore('?').ifBlank {
        "download_${System.currentTimeMillis()}"
    }
    enqueueDownload(
        context = context,
        video = DetectedVideo(
            url = url,
            filename = filename,
            format = filename.substringAfterLast('.', "bin")
        )
    )
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun shareUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(intent, null))
}

private fun normalizeToUrl(input: String, searchEngineId: String): String {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return ""
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        return trimmed
    }

    val hasSchemeLikePrefix = "://" in trimmed
    val isLocalHost = trimmed.equals("localhost", ignoreCase = true) || trimmed.startsWith("localhost:", ignoreCase = true)
    val isIpAddress = trimmed.matches(Regex("""\d{1,3}(\.\d{1,3}){3}(:\d+)?([/?#].*)?"""))
    val hasDomainLikeHost = trimmed.contains('.') && !trimmed.contains(' ')
    val looksLikeUrl = !hasSchemeLikePrefix && (isLocalHost || isIpAddress || hasDomainLikeHost)

    return if (looksLikeUrl) {
        "https://$trimmed"
    } else {
        buildSearchUrl(trimmed, searchEngineId)
    }
}
