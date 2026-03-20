package com.webvault.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

data class HistorySite(val title: String, val url: String)

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

    var addressBarText by rememberSaveable(activeTabId) { mutableStateOf(activeTab.url) }
    var isSecure by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    val recentHistory = remember { mutableStateOf<List<HistorySite>>(emptyList()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var attachedTabId by remember { mutableStateOf(activeTabId) }
    var showTabsSheet by remember { mutableStateOf(false) }
    var showJsMenu by remember { mutableStateOf(false) }
    var showBrowserMenu by remember { mutableStateOf(false) }
    var showSearchEngineMenu by remember { mutableStateOf(false) }
    var jsAllowed by remember { mutableStateOf(true) }
    var showVideoListSheet by remember { mutableStateOf(false) }
    val blockedCount by AdBlocker.blockedCount.collectAsState()
    val adBlockEnabled by AppPreferences.adBlockEnabledFlow(context).collectAsState(initial = true)
    val httpsEverywhereEnabled by AppPreferences.httpsEverywhereEnabledFlow(context).collectAsState(initial = true)
    val videoSnifferEnabled by AppPreferences.videoSnifferEnabledFlow(context).collectAsState(initial = true)
    val defaultSearchEngineId by AppPreferences.defaultSearchEngineFlow(context).collectAsState(initial = defaultSearchEngine().id)
    val detectedVideos by videoSnifferViewModel.videos.collectAsState()

    val bridge = remember(videoSnifferViewModel) { VideoSnifferBridge(videoSnifferViewModel) }

    LaunchedEffect(Unit) { AdBlocker.initialize(context) }
    LaunchedEffect(activeTabId) {
        val url = TabManager.tabs.value.firstOrNull { it.id == activeTabId }?.url.orEmpty()
        addressBarText = url
    }

    val onSubmitUrl: (String) -> Unit = { input ->
        val target = normalizeToUrl(input, activeTab.searchEngineId)
        if (target.isNotBlank()) {
            addressBarText = target
            TabManager.updateActiveTab(url = target)
            onNavigateToBrowser()
            isLoading = true
            loadProgress = 0.05f
        }
    }


    LaunchedEffect(activeTabId, defaultSearchEngineId) {
        if (activeTab.url.isBlank() && activeTab.searchEngineId != defaultSearchEngineId) {
            TabManager.updateActiveTab(searchEngineId = defaultSearchEngineId)
        }
    }

    val showHomePage = showHomeOverlay || activeTab.url.isBlank()

    BackHandler(enabled = webViewRef?.canGoBack() == true) { webViewRef?.goBack() }

    Box(modifier = modifier.fillMaxSize().background(AppBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            AddressBar(
                url = addressBarText,
                isSecure = isSecure,
                tabCount = tabs.size,
                isLoading = isLoading,
                blockedCount = blockedCount,
                jsAllowed = jsAllowed,
                searchEngineLabel = searchEngineById(activeTab.searchEngineId).label,
                searchEngineMenuExpanded = showSearchEngineMenu,
                onDismissSearchEngineMenu = { showSearchEngineMenu = false },
                onSelectSearchEngine = { engine ->
                    TabManager.updateActiveTab(searchEngineId = engine.id)
                    showSearchEngineMenu = false
                },
                onUrlChange = { addressBarText = it },
                onUrlSubmit = { onSubmitUrl(addressBarText) },
                onRefreshOrStop = { webViewRef?.let { if (isLoading) it.stopLoading() else it.reload() } },
                onTabsClick = { showTabsSheet = true },
                onSearchEngineClick = { showSearchEngineMenu = true },
                onLockLongPress = { showJsMenu = true },
                onSetJavaScriptAllowed = { allowed ->
                    scope.launch {
                        val host = runCatching { Uri.parse(addressBarText).host.orEmpty() }.getOrDefault("")
                        AppPreferences.setJavaScriptAllowed(context, host, allowed)
                        jsAllowed = allowed
                        webViewRef?.settings?.javaScriptEnabled = allowed
                        webViewRef?.reload()
                    }
                },
                showJsMenu = showJsMenu,
                onDismissJsMenu = { showJsMenu = false }
            )

            if (isLoading) {
                LinearProgressIndicator(progress = { loadProgress.coerceIn(0f, 1f) }, color = PrimaryBlue, modifier = Modifier.fillMaxWidth())
            }

            if (showHomePage) {
                HomeScreen(
                    searchSessionKey = activeTabId,
                    recentHistory = recentHistory.value,
                    selectedSearchEngineLabel = searchEngineById(activeTab.searchEngineId).label,
                    searchEngineMenuExpanded = showSearchEngineMenu,
                    onDismissSearchEngineMenu = { showSearchEngineMenu = false },
                    onSelectSearchEngine = { engine ->
                        TabManager.updateActiveTab(searchEngineId = engine.id)
                        showSearchEngineMenu = false
                    },
                    onSearchEngineClick = { showSearchEngineMenu = true },
                    onSubmit = onSubmitUrl,
                    onSpeedDialClick = onSubmitUrl
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppBackground)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.domStorageEnabled = true
                                settings.javaScriptEnabled = true
                                settings.useWideViewPort = activeTab.isDesktopMode
                                settings.loadWithOverviewMode = activeTab.isDesktopMode
                                settings.userAgentString = if (activeTab.isDesktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
                                addJavascriptInterface(bridge, "Android")

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) { loadProgress = newProgress / 100f }
                                    override fun onReceivedTitle(view: WebView?, title: String?) { if (!title.isNullOrBlank()) TabManager.updateActiveTab(title = title) }
                                    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) { if (icon != null) TabManager.updateActiveTab(favicon = icon) }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isLoading = true
                                        url?.let {
                                            TabManager.updateActiveTab(url = it)
                                            addressBarText = it
                                            isSecure = it.startsWith("https://")
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
                                        injectVideoSnifferScript(view)
                                        url?.let {
                                            val title = runCatching { Uri.parse(it).host.orEmpty() }.getOrDefault(it).removePrefix("www.").ifBlank { it }
                                            recentHistory.value = (listOf(HistorySite(title, it)) + recentHistory.value).distinctBy { site -> site.url }.take(5)
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val newUrl = request?.url?.toString().orEmpty()
                                        if (newUrl.isBlank()) return false
                                        val uri = Uri.parse(newUrl)
                                        if (httpsEverywhereEnabled && uri.scheme == "http" && !httpOnlyDomains.contains(uri.host.orEmpty())) {
                                            view?.loadUrl(uri.buildUpon().scheme("https").build().toString())
                                            return true
                                        }
                                        return false
                                    }

                                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                                        val requestUrl = request?.url?.toString().orEmpty()
                                        val lower = requestUrl.lowercase()
                                        val contentTypeHint = request?.requestHeaders?.entries?.firstOrNull { it.key.equals("Accept", true) }?.value?.lowercase().orEmpty()
                                        if (videoSnifferEnabled && (videoExtensions.any { lower.contains(it) } || contentTypeHint.contains("video/"))) {
                                            videoSnifferViewModel.onVideoDetected(requestUrl, type = lower.substringAfterLast('.', "video"))
                                        }
                                        if (adBlockEnabled) return AdBlocker.interceptIfBlocked(requestUrl)
                                        return null
                                    }
                                }
                                webViewRef = this
                            }
                        }
                        },
                        update = { webView ->
                            webViewRef = webView
                            if (attachedTabId != activeTabId) {
                                val old = Bundle(); webView.saveState(old); TabManager.saveState(attachedTabId, old)
                                attachedTabId = activeTabId
                                val restored = TabManager.getSavedState(activeTabId)
                                if (restored != null) webView.restoreState(restored)
                                else if (activeTab.url.isNotBlank()) webView.loadUrl(activeTab.url)
                            } else if (activeTab.url.isNotBlank() && activeTab.url != webView.url && !isLoading) {
                                webView.loadUrl(activeTab.url)
                            }
                            val desiredUserAgent = if (activeTab.isDesktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
                            if (webView.settings.useWideViewPort != activeTab.isDesktopMode) webView.settings.useWideViewPort = activeTab.isDesktopMode
                            if (webView.settings.loadWithOverviewMode != activeTab.isDesktopMode) webView.settings.loadWithOverviewMode = activeTab.isDesktopMode
                            if (webView.settings.userAgentString != desiredUserAgent) webView.settings.userAgentString = desiredUserAgent
                        }
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
                DropdownMenu(expanded = showBrowserMenu, onDismissRequest = { showBrowserMenu = false }) {
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
                                webViewRef?.settings?.useWideViewPort = enabled
                                webViewRef?.settings?.loadWithOverviewMode = enabled
                                webViewRef?.settings?.userAgentString = if (enabled) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
                                webViewRef?.reload()
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
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
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
            onSelectTab = { id -> TabManager.switchToTab(id); showTabsSheet = false },
            onCloseTab = { TabManager.closeTab(it) },
            onNewTab = {
                if (!TabManager.openNewTab()) Toast.makeText(context, "Maximum of 10 tabs reached", Toast.LENGTH_SHORT).show()
                else { AdBlocker.resetCount(); showTabsSheet = false }
            },
            onDismiss = { showTabsSheet = false }
        )
    }

    LaunchedEffect(addressBarText) {
        val host = withContext(Dispatchers.Default) { runCatching { Uri.parse(addressBarText).host.orEmpty() }.getOrDefault("") }
        jsAllowed = AppPreferences.isJavaScriptAllowed(context, host)
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

private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private fun enqueueDownload(context: android.content.Context, video: DetectedVideo) {
    val id = UUID.randomUUID().toString()
    val data = Data.Builder()
        .putString(DownloadWorker.KEY_URL, video.url)
        .putString(DownloadWorker.KEY_FILENAME, video.filename)
        .putString(DownloadWorker.KEY_ID, id)
        .build()
    val work = OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(data).build()
    WorkManager.getInstance(context).enqueue(work)
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
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                Text(first.filename, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Button(onClick = { onDownload(first) }) { Text("Download") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoListSheet(videos: List<DetectedVideo>, onDismiss: () -> Unit, onDownload: (DetectedVideo) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        LazyVerticalGrid(columns = GridCells.Fixed(1), modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            gridItems(videos) { video ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(video.filename, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Button(onClick = { onDownload(video) }) { Text("Download") }
                    }
                }
            }
        }
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
    searchEngineLabel: String,
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
    Row(Modifier.fillMaxWidth().background(AppSurface).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Icon(Icons.Default.Lock, contentDescription = "Connection security", tint = if (isSecure) Color(0xFF2E7D32) else Color.Gray, modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLockLongPress))
            DropdownMenu(expanded = showJsMenu, onDismissRequest = onDismissJsMenu) {
                DropdownMenuItem(text = { Text(if (jsAllowed) "Block JavaScript on this site" else "Allow JavaScript") }, onClick = { onSetJavaScriptAllowed(!jsAllowed); onDismissJsMenu() })
            }
        }
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(url, onUrlChange, modifier = Modifier.weight(1f), placeholder = { Text("Search or enter URL") }, singleLine = true, shape = RoundedCornerShape(22.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = Color.LightGray), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { focusManager.clearFocus(); onUrlSubmit() }))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.clip(CircleShape).background(PrimaryBlue).clickable(onClick = onTabsClick).padding(horizontal = 10.dp, vertical = 6.dp)) { Text(tabCount.toString(), color = Color.White, style = MaterialTheme.typography.labelMedium) }
        Spacer(Modifier.width(6.dp))
        Box {
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3F7FF)).clickable(onClick = onSearchEngineClick).padding(horizontal = 8.dp, vertical = 5.dp)) {
                Text(searchEngineLabel, style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
            }
            DropdownMenu(expanded = searchEngineMenuExpanded, onDismissRequest = onDismissSearchEngineMenu) {
                allSearchEngines().forEach { engine ->
                    DropdownMenuItem(text = { Text(engine.label) }, onClick = { onSelectSearchEngine(engine) })
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFE3F2FD)).padding(horizontal = 8.dp, vertical = 5.dp)) { Text("Blocked: $blockedCount", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue) }
        Spacer(Modifier.width(8.dp))
        Icon(if (isLoading) Icons.Default.Close else Icons.Default.Refresh, contentDescription = if (isLoading) "Stop loading" else "Refresh", modifier = Modifier.size(24.dp).clickable(onClick = onRefreshOrStop))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabSheet(tabs: List<Tab>, activeTabId: Int, onSelectTab: (Int) -> Unit, onCloseTab: (Int) -> Unit, onNewTab: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxWidth().height(320.dp).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            gridItems(tabs) { tab ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onSelectTab(tab.id) }, colors = CardDefaults.cardColors(containerColor = if (tab.id == activeTabId) Color(0xFFE3F2FD) else AppSurface)) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (tab.faviconBitmap != null) {
                                androidx.compose.foundation.Image(bitmap = tab.faviconBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(18.dp))
                            } else {
                                Box(Modifier.size(18.dp).clip(CircleShape).background(PrimaryBlue), contentAlignment = Alignment.Center) { Text("T", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(tab.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { onCloseTab(tab.id) }) { Icon(Icons.Default.Close, contentDescription = "Close tab") }
                        }
                        Text(tab.url.ifBlank { "New Tab" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Button(onClick = onNewTab, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("+ New Tab") }
    }
}

@Composable
fun HomeScreen(
    searchSessionKey: Int,
    recentHistory: List<HistorySite>,
    selectedSearchEngineLabel: String,
    searchEngineMenuExpanded: Boolean,
    onDismissSearchEngineMenu: () -> Unit,
    onSelectSearchEngine: (SearchEngine) -> Unit,
    onSearchEngineClick: () -> Unit,
    onSubmit: (String) -> Unit,
    onSpeedDialClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchInput by rememberSaveable(searchSessionKey) { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Webvault", color = PrimaryBlue, fontSize = 38.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 48.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = AppSurface)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryBlue)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(searchInput, onValueChange = { searchInput = it }, modifier = Modifier.weight(1f), placeholder = { Text("Search or enter URL") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); onSubmit(searchInput) }), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent))
                Spacer(Modifier.width(8.dp))
                Box {
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3F7FF)).clickable(onClick = onSearchEngineClick).padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(selectedSearchEngineLabel, style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                    }
                    DropdownMenu(expanded = searchEngineMenuExpanded, onDismissRequest = onDismissSearchEngineMenu) {
                        allSearchEngines().forEach { engine ->
                            DropdownMenuItem(text = { Text(engine.label) }, onClick = { onSelectSearchEngine(engine) })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            defaultSpeedDials.chunked(4).forEach { rowSites ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowSites.forEach { (name, url) ->
                        Card(modifier = Modifier.weight(1f).height(74.dp).clickable { onSpeedDialClick(url) }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppSurface)) {
                            Column(modifier = Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Box(Modifier.size(24.dp).clip(CircleShape).background(PrimaryBlue), contentAlignment = Alignment.Center) { Text(name.first().uppercaseChar().toString(), color = Color.White) }
                                Spacer(Modifier.height(6.dp)); Text(name, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Recent History", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(recentHistory.take(5)) { site ->
                Card(modifier = Modifier.width(132.dp).height(72.dp).clickable { onSubmit(site.url) }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppSurface)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(site.title, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(site.url, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
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
