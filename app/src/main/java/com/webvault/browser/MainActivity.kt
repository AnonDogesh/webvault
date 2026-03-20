package com.webvault.browser

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.webvault.browser.ui.theme.WebvaultTheme

enum class BottomNavItem(val label: String) {
    Home("Home"),
    Browser("Browser"),
    Downloads("Downloads"),
    Vault("Vault"),
    Settings("Settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WebvaultTheme {
                WebvaultApp()
            }
        }
    }
}

@Composable
fun WebvaultApp() {
    var selectedItem by remember { mutableStateOf(BottomNavItem.Home) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                BottomNavItem.entries.forEach { item ->
                    NavigationBarItem(
                        selected = selectedItem == item,
                        onClick = { selectedItem = item },
                        icon = {
                            Icon(
                                imageVector = when (item) {
                                    BottomNavItem.Home -> Icons.Default.Home
                                    BottomNavItem.Browser -> Icons.Default.Search
                                    BottomNavItem.Downloads -> Icons.Default.Refresh
                                    BottomNavItem.Vault -> Icons.Default.Lock
                                    BottomNavItem.Settings -> Icons.Default.Settings
                                },
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedItem) {
            BottomNavItem.Home -> BrowserScreen(
                modifier = Modifier.padding(innerPadding),
                forceHomePage = true,
                onNavigateToBrowser = { selectedItem = BottomNavItem.Browser }
            )
            BottomNavItem.Browser -> BrowserScreen(Modifier.padding(innerPadding))
            BottomNavItem.Downloads -> DownloadsScreen(Modifier.padding(innerPadding))
            BottomNavItem.Vault -> VaultScreen(Modifier.padding(innerPadding))
            BottomNavItem.Settings -> SettingsScreen(Modifier.padding(innerPadding))
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
