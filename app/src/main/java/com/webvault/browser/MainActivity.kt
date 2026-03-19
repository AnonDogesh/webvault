package com.webvault.browser

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Web
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

class MainActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
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
                                    BottomNavItem.Browser -> Icons.Default.Web
                                    BottomNavItem.Downloads -> Icons.Default.Download
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
            BottomNavItem.Home -> PlaceholderScreen("Home", Modifier.padding(innerPadding))
            BottomNavItem.Browser -> PlaceholderScreen("Browser", Modifier.padding(innerPadding))
            BottomNavItem.Downloads -> PlaceholderScreen("Downloads", Modifier.padding(innerPadding))
            BottomNavItem.Vault -> PlaceholderScreen("Vault", Modifier.padding(innerPadding))
            BottomNavItem.Settings -> PlaceholderScreen("Settings", Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
        androidx.compose.material3.Text(text = "$name Screen")
    }
}

@Preview(showBackground = true)
@Composable
private fun WebvaultAppPreview() {
    WebvaultTheme {
        WebvaultApp()
    }
}
