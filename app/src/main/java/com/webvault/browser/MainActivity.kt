package com.webvault.browser

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.webvault.browser.ui.theme.WebvaultTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

enum class BottomNavItem(val label: String) {
    Home("Home"),
    Downloads("Downloads"),
    Vault("Vault"),
    Settings("Settings")
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TabManager.initialize(applicationContext)
        enableEdgeToEdge()
        setContent {
            WebvaultTheme {
                WebvaultApp()
            }
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            val closeBehavior = runBlocking {
                AppPreferences.appCloseBehaviorFlow(applicationContext).first()
            }
            if (closeBehavior == "clear_tabs") {
                TabManager.clearPersistedTabs()
            }
        }
        super.onDestroy()
    }
}

@Composable
fun WebvaultApp() {
    val context = LocalContext.current
    val appOpenBehavior by AppPreferences.appOpenBehaviorFlow(context).collectAsState(initial = "home")
    var selectedItem by remember { mutableStateOf<BottomNavItem?>(BottomNavItem.Home) }
    var previousNonDownloadItem by remember { mutableStateOf<BottomNavItem?>(BottomNavItem.Home) }
    var lastBackPressAt by remember { mutableStateOf(0L) }
    var startupApplied by remember { mutableStateOf(false) }

    LaunchedEffect(appOpenBehavior) {
        if (!startupApplied) {
            selectedItem = if (appOpenBehavior == "last_tab" && TabManager.hasSavedTabs()) {
                null
            } else {
                BottomNavItem.Home
            }
            previousNonDownloadItem = selectedItem
            startupApplied = true
        }
    }

    BackHandler {
        when (selectedItem) {
            BottomNavItem.Downloads -> {
                selectedItem = previousNonDownloadItem ?: BottomNavItem.Home
            }

            BottomNavItem.Vault,
            BottomNavItem.Settings -> {
                selectedItem = previousNonDownloadItem ?: BottomNavItem.Home
            }

            BottomNavItem.Home,
            null -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressAt < 2_000L) {
                    (context as? FragmentActivity)?.finish()
                } else {
                    lastBackPressAt = now
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                BottomNavItem.entries.forEach { item ->
                    NavigationBarItem(
                        selected = selectedItem == item,
                        onClick = {
                            if (item == BottomNavItem.Downloads) {
                                previousNonDownloadItem = selectedItem
                            } else if (selectedItem != BottomNavItem.Downloads) {
                                previousNonDownloadItem = item
                            }
                            selectedItem = item
                        },
                        icon = {
                            when (item) {
                                BottomNavItem.Home -> Icon(Icons.Default.Home, contentDescription = item.label)
                                BottomNavItem.Downloads -> Text("↓")
                                BottomNavItem.Vault -> Icon(Icons.Default.Lock, contentDescription = item.label)
                                BottomNavItem.Settings -> Icon(Icons.Default.Settings, contentDescription = item.label)
                            }
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedItem) {
            BottomNavItem.Downloads -> DownloadsScreen(
                modifier = Modifier.padding(innerPadding),
                onNavigateBack = { selectedItem = previousNonDownloadItem ?: BottomNavItem.Home }
            )
            BottomNavItem.Vault -> VaultScreen(Modifier.padding(innerPadding))
            BottomNavItem.Settings -> SettingsScreen(Modifier.padding(innerPadding))
            BottomNavItem.Home, null -> BrowserScreen(
                modifier = Modifier.padding(innerPadding),
                showHomeOverlay = selectedItem == BottomNavItem.Home,
                onNavigateToBrowser = { selectedItem = null }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WebvaultAppPreview() {
    WebvaultTheme {
        WebvaultApp()
    }
}
