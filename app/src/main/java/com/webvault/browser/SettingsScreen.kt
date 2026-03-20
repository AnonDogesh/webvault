package com.webvault.browser

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val SettingsBlue = Color(0xFF2D8CDB)
private val SettingsLabelBlue = Color(0xFF3367C1)
private val SettingsCardBorder = Color(0xFFE2D8C9)
private val SettingsIconBackground = Color(0xFFF2F6FB)

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adBlockEnabled by AppPreferences.adBlockEnabledFlow(context).collectAsState(initial = true)
    val httpsEverywhereEnabled by AppPreferences.httpsEverywhereEnabledFlow(context).collectAsState(initial = true)
    val videoSnifferEnabled by AppPreferences.videoSnifferEnabledFlow(context).collectAsState(initial = true)
    val biometricLockEnabled by AppPreferences.biometricLockEnabledFlow(context).collectAsState(initial = true)
    val homepage by AppPreferences.homepageFlow(context).collectAsState(initial = "google.com")
    val downloadQuality by AppPreferences.downloadQualityFlow(context).collectAsState(initial = "Prefer 1080p")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        SettingsHeader()
        Spacer(Modifier.height(18.dp))
        AppInfoCard(onClick = {
            Toast.makeText(context, "AxBrowser Pro Version 1.0.0", Toast.LENGTH_SHORT).show()
        })

        Spacer(Modifier.height(12.dp))
        SettingsSectionLabel("BROWSER")
        SettingsGroupCard {
            SettingsToggleRow(
                title = "Ad Blocker",
                subtitle = "EasyList + custom rules",
                icon = { SettingsIconBubble("⊕") },
                checked = adBlockEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { AppPreferences.setAdBlockEnabled(context, enabled) }
                }
            )
            SettingsDivider()
            SettingsToggleRow(
                title = "HTTPS Everywhere",
                subtitle = "Force secure connections",
                icon = { SettingsRowIcon(Icons.Default.Shield) },
                checked = httpsEverywhereEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { AppPreferences.setHttpsEverywhereEnabled(context, enabled) }
                }
            )
            SettingsDivider()
            SettingsActionRow(
                title = "Homepage",
                subtitle = homepage,
                icon = { SettingsRowIcon(Icons.Default.Home) },
                onClick = {
                    scope.launch {
                        val nextHomePage = if (homepage == "google.com") "startpage.com" else "google.com"
                        AppPreferences.setHomepage(context, nextHomePage)
                        Toast.makeText(context, "Homepage set to $nextHomePage", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        Spacer(Modifier.height(12.dp))
        SettingsSectionLabel("DOWNLOADS")
        SettingsGroupCard {
            SettingsToggleRow(
                title = "Video sniffer",
                subtitle = "Detect videos on pages",
                icon = { SettingsRowIcon(Icons.Default.Download) },
                checked = videoSnifferEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { AppPreferences.setVideoSnifferEnabled(context, enabled) }
                }
            )
            SettingsDivider()
            SettingsActionRow(
                title = "Download quality",
                subtitle = downloadQuality,
                icon = { SettingsRowIcon(Icons.Default.Videocam) },
                onClick = {
                    scope.launch {
                        val nextQuality = if (downloadQuality == "Prefer 1080p") "Prefer 720p" else "Prefer 1080p"
                        AppPreferences.setDownloadQuality(context, nextQuality)
                        Toast.makeText(context, "Download quality: $nextQuality", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        Spacer(Modifier.height(12.dp))
        SettingsSectionLabel("VAULT")
        SettingsGroupCard {
            SettingsToggleRow(
                title = "Biometric lock",
                subtitle = "Fingerprint / Face ID",
                icon = { SettingsRowIcon(Icons.Default.Lock) },
                checked = biometricLockEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { AppPreferences.setBiometricLockEnabled(context, enabled) }
                }
            )
            SettingsDivider()
            SettingsActionRow(
                title = "Change PIN",
                subtitle = "4-digit passcode",
                icon = { SettingsRowIcon(Icons.Default.Security) },
                onClick = {
                    Toast.makeText(context, "Open Vault to change PIN", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun SettingsHeader() {
    Text(
        text = "Settings",
        color = Color.Black,
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun AppInfoCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SettingsCardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SettingsBlue),
                contentAlignment = Alignment.Center
            ) {
                Text("AX", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("AxBrowser Pro", fontWeight = FontWeight.SemiBold)
                Text("Version 1.0.0", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "App information",
                tint = Color(0xFFB7B7B7)
            )
        }
    }
}

@Composable
private fun SettingsSectionLabel(label: String) {
    Text(
        text = label,
        color = SettingsLabelBlue,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SettingsCardBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = title,
            tint = Color(0xFFB7B7B7)
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFF2F2F2))
    )
}

@Composable
private fun SettingsRowIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SettingsIconBackground),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = SettingsBlue, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SettingsIconBubble(text: String) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SettingsIconBackground),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = SettingsBlue, fontWeight = FontWeight.Bold)
    }
}
