package com.webvault.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.webvault.browser.ui.theme.AppBackground
import com.webvault.browser.ui.theme.AppSurface
import com.webvault.browser.ui.theme.PrimaryBlue

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
    var currentUrl by rememberSaveable { mutableStateOf("") }
    var addressBarText by rememberSaveable { mutableStateOf("") }
    var isSecure by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var tabCount by rememberSaveable { mutableIntStateOf(1) }
    val recentHistory = remember { mutableStateListOf<HistorySite>() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val onSubmitUrl: (String) -> Unit = { input ->
        val target = normalizeToUrl(input)
        currentUrl = target
        addressBarText = target
    }

    BackHandler(enabled = webViewRef?.canGoBack() == true) {
        webViewRef?.goBack()
    }

    Column(modifier = modifier.fillMaxSize().background(AppBackground)) {
        if (currentUrl.isBlank()) {
            HomeScreen(
                recentHistory = recentHistory,
                onSubmit = { onSubmitUrl(it) },
                onSpeedDialClick = { onSubmitUrl(it) }
            )
        } else {
            AddressBar(
                url = addressBarText,
                isSecure = isSecure,
                tabCount = tabCount,
                isLoading = isLoading,
                onUrlChange = { addressBarText = it },
                onUrlSubmit = { onSubmitUrl(addressBarText) },
                onRefreshOrStop = {
                    webViewRef?.let { webView ->
                        if (isLoading) webView.stopLoading() else webView.reload()
                    }
                }
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
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = "Webvault/1.0 Mobile"

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress / 100f
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                url?.let {
                                    currentUrl = it
                                    addressBarText = it
                                    isSecure = it.startsWith("https://")
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
                                    recentHistory.removeAll { site -> site.url == it }
                                    recentHistory.add(0, HistorySite(title = title, url = it))
                                    while (recentHistory.size > 5) {
                                        recentHistory.removeAt(recentHistory.lastIndex)
                                    }
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val newUrl = request?.url?.toString().orEmpty()
                                if (newUrl.isNotBlank()) {
                                    currentUrl = newUrl
                                    addressBarText = newUrl
                                    isSecure = newUrl.startsWith("https://")
                                }
                                return false
                            }
                        }

                        loadUrl(currentUrl)
                        webViewRef = this
                    }
                },
                update = { webView ->
                    if (currentUrl.isNotBlank() && currentUrl != webView.url) {
                        webView.loadUrl(currentUrl)
                    }
                    webViewRef = webView
                    tabCount = 1
                }
            )
        }
    }

    LaunchedEffect(currentUrl) {
        if (currentUrl.isNotBlank()) {
            isSecure = currentUrl.startsWith("https://")
        }
    }
}

@Composable
private fun AddressBar(
    url: String,
    isSecure: Boolean,
    tabCount: Int,
    isLoading: Boolean,
    onUrlChange: (String) -> Unit,
    onUrlSubmit: () -> Unit,
    onRefreshOrStop: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Connection security",
            tint = if (isSecure) Color(0xFF2E7D32) else Color.Gray
        )

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
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = tabCount.toString(),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = if (isLoading) Icons.Default.Stop else Icons.Default.Refresh,
            contentDescription = if (isLoading) "Stop loading" else "Refresh",
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onRefreshOrStop)
        )
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
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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

        Text(
            text = "Recent History",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
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

    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return trimmed
    }

    return if (' ' in trimmed) {
        "https://www.google.com/search?q=${Uri.encode(trimmed)}"
    } else {
        "https://$trimmed"
    }
}
