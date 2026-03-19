package com.webvault.browser

import android.annotation.SuppressLint
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.webvault.browser.ui.theme.AppBackground
import com.webvault.browser.ui.theme.AppSurface
import com.webvault.browser.ui.theme.PrimaryBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val httpOnlyDomains = setOf("neverssl.com", "example.com")

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

data class HistorySite(
    val title: String,
    val url: String
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(modifier: Modifier = Modifier) {
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
    var jsAllowed by remember { mutableStateOf(true) }
    val blockedCount by AdBlocker.blockedCount.collectAsState()
    val adBlockEnabled by AppPreferences.adBlockEnabledFlow(context).collectAsState(initial = true)

    LaunchedEffect(Unit) {
        AdBlocker.initialize(context)
    }

    LaunchedEffect(activeTabId) {
        addressBarText = activeTab.url
    }

    val onSubmitUrl: (String) -> Unit = { input ->
        val target = normalizeToUrl(input)
        if (target.isNotBlank()) {
            addressBarText = target
            TabManager.updateActiveTab(url = target)
        }
    }

    BackHandler(enabled = webViewRef?.canGoBack() == true) {
        webViewRef?.goBack()
    }

    Column(modifier = modifier.fillMaxSize().background(AppBackground)) {
        if (activeTab.url.isBlank()) {
            HomeScreen(
                recentHistory = recentHistory.value,
                onSubmit = { onSubmitUrl(it) },
                onSpeedDialClick = { onSubmitUrl(it) }
            )
        } else {
            AddressBar(
                url = addressBarText,
                isSecure = isSecure,
                tabCount = tabs.size,
                isLoading = isLoading,
                blockedCount = blockedCount,
                jsAllowed = jsAllowed,
                onUrlChange = { addressBarText = it },
                onUrlSubmit = { onSubmitUrl(addressBarText) },
                onRefreshOrStop = {
                    webViewRef?.let { webView ->
                        if (isLoading) webView.stopLoading() else webView.reload()
                    }
                },
                onTabsClick = { showTabsSheet = true },
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
                LinearProgressIndicator(
                    progress = { loadProgress.coerceIn(0f, 1f) },
                    color = PrimaryBlue,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.domStorageEnabled = true
                        settings.userAgentString = "Webvault/1.0 Mobile"

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress / 100f
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) TabManager.updateActiveTab(title = title)
                            }

                            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                                if (icon != null) TabManager.updateActiveTab(favicon = icon)
                            }
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
                                url?.let {
                                    val title = runCatching { Uri.parse(it).host.orEmpty() }
                                        .getOrDefault(it)
                                        .removePrefix("www.")
                                        .ifBlank { it }
                                    recentHistory.value = (listOf(HistorySite(title = title, url = it)) + recentHistory.value)
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
                                if (uri.scheme == "http" && !httpOnlyDomains.contains(uri.host.orEmpty())) {
                                    val secureUrl = uri.buildUpon().scheme("https").build().toString()
                                    view?.loadUrl(secureUrl)
                                    return true
                                }

                                TabManager.updateActiveTab(url = newUrl)
                                addressBarText = newUrl
                                isSecure = newUrl.startsWith("https://")
                                return false
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ) = if (adBlockEnabled) {
                                AdBlocker.interceptIfBlocked(request?.url?.toString())
                            } else {
                                null
                            }
                        }

                        webViewRef = this
                    }
                },
                update = { webView ->
                    webViewRef = webView

                    if (attachedTabId != activeTabId) {
                        val oldState = Bundle()
                        webView.saveState(oldState)
                        TabManager.saveState(attachedTabId, oldState)

                        attachedTabId = activeTabId
                        val restored = TabManager.getSavedState(activeTabId)
                        if (restored != null) {
                            webView.restoreState(restored)
                        } else if (activeTab.url.isNotBlank()) {
                            webView.loadUrl(activeTab.url)
                        }
                    } else if (activeTab.url.isNotBlank() && activeTab.url != webView.url) {
                        webView.loadUrl(activeTab.url)
                    }
                }
            )
        }
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

    LaunchedEffect(addressBarText) {
        val host = withContext(Dispatchers.Default) {
            runCatching { Uri.parse(addressBarText).host.orEmpty() }.getOrDefault("")
        }
        jsAllowed = AppPreferences.isJavaScriptAllowed(context, host)
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
    onUrlChange: (String) -> Unit,
    onUrlSubmit: () -> Unit,
    onRefreshOrStop: () -> Unit,
    onTabsClick: () -> Unit,
    onLockLongPress: () -> Unit,
    onSetJavaScriptAllowed: (Boolean) -> Unit,
    showJsMenu: Boolean,
    onDismissJsMenu: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Connection security",
                tint = if (isSecure) Color(0xFF2E7D32) else Color.Gray,
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = onLockLongPress
                )
            )
            DropdownMenu(expanded = showJsMenu, onDismissRequest = onDismissJsMenu) {
                DropdownMenuItem(
                    text = { Text(if (jsAllowed) "Block JavaScript on this site" else "Allow JavaScript") },
                    onClick = {
                        onSetJavaScriptAllowed(!jsAllowed)
                        onDismissJsMenu()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search or enter URL") },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = Color.LightGray
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                focusManager.clearFocus()
                onUrlSubmit()
            })
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(PrimaryBlue)
                .clickable(onClick = onTabsClick)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(text = tabCount.toString(), color = Color.White, style = MaterialTheme.typography.labelMedium)
        }

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE3F2FD))
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text(
                text = "Blocked: $blockedCount",
                style = MaterialTheme.typography.labelSmall,
                color = PrimaryBlue
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = if (isLoading) Icons.Default.Stop else Icons.Default.Refresh,
            contentDescription = if (isLoading) "Stop loading" else "Refresh",
            modifier = Modifier.size(24.dp).clickable(onClick = onRefreshOrStop)
        )
    }
}

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
            items(tabs) { tab ->
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
                                androidx.compose.foundation.Image(
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
                                text = tab.title,
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
                            text = tab.url.ifBlank { "New Tab" },
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
fun HomeScreen(
    recentHistory: List<HistorySite>,
    onSubmit: (String) -> Unit,
    onSpeedDialClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchInput by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Webvault",
            color = PrimaryBlue,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp, bottom = 48.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppSurface)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = PrimaryBlue)
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search or enter URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        onSubmit(searchInput)
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            defaultSpeedDials.chunked(4).forEach { rowSites ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowSites.forEach { (name, url) ->
                        Card(
                            modifier = Modifier.weight(1f).height(74.dp).clickable { onSpeedDialClick(url) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = AppSurface)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier.size(24.dp).clip(CircleShape).background(PrimaryBlue),
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

        Text(
            text = "Recent History",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(recentHistory.take(5)) { site ->
                Card(
                    modifier = Modifier.width(132.dp).height(72.dp).clickable { onSubmit(site.url) },
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

private fun normalizeToUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return ""

    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed

    return if (' ' in trimmed) {
        "https://www.google.com/search?q=${Uri.encode(trimmed)}"
    } else {
        "https://$trimmed"
    }
}
