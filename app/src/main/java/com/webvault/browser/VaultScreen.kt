package com.webvault.browser

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VaultScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var setupMode by remember { mutableStateOf(!VaultManager.hasPin(context)) }
    var wrongAttempts by remember { mutableIntStateOf(0) }
    var lockoutSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(lockoutSeconds) {
        while (lockoutSeconds > 0) {
            delay(1000)
            lockoutSeconds -= 1
        }
    }

    if (unlocked) {
        VaultUnlockedScreen(modifier)
    } else {
        LockedVaultScreen(
            modifier = modifier,
            pinInput = pinInput,
            setupMode = setupMode,
            confirmPinInput = confirmPinInput,
            lockoutSeconds = lockoutSeconds,
            onDigit = { d ->
                if (lockoutSeconds > 0) return@LockedVaultScreen
                if (setupMode) {
                    if (pinInput.length < 4) pinInput += d
                    else if (confirmPinInput.length < 4) confirmPinInput += d

                    if (pinInput.length == 4 && confirmPinInput.length == 4) {
                        if (pinInput == confirmPinInput) {
                            VaultManager.setPin(context, pinInput)
                            setupMode = false
                            unlocked = true
                            pinInput = ""
                            confirmPinInput = ""
                        } else {
                            Toast.makeText(context, "PINs do not match", Toast.LENGTH_SHORT).show()
                            pinInput = ""
                            confirmPinInput = ""
                        }
                    }
                } else {
                    if (pinInput.length < 4) pinInput += d
                    if (pinInput.length == 4) {
                        val ok = VaultManager.verifyPin(context, pinInput)
                        if (ok) {
                            unlocked = true
                            wrongAttempts = 0
                        } else {
                            wrongAttempts += 1
                            pinInput = ""
                            if (wrongAttempts >= 5) {
                                lockoutSeconds = 30
                                wrongAttempts = 0
                            }
                        }
                    }
                }
            },
            onDelete = {
                if (setupMode && confirmPinInput.isNotEmpty()) confirmPinInput = confirmPinInput.dropLast(1)
                else if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
            },
            onBiometric = {
                val activity = context as? androidx.fragment.app.FragmentActivity
                if (activity == null) {
                    Toast.makeText(context, "Biometric unlock is unavailable on this screen", Toast.LENGTH_SHORT).show()
                    return@LockedVaultScreen
                }
                val biometricManager = BiometricManager.from(context)
                val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
                    Toast.makeText(context, "No enrolled phone biometrics found", Toast.LENGTH_SHORT).show()
                    return@LockedVaultScreen
                }
                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(context),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            unlocked = true
                        }
                    }
                )
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Unlock Private Vault")
                        .setSubtitle("Use the fingerprint or biometric enrolled on this phone")
                        .setNegativeButtonText("Cancel")
                        .build()
                )
            }
        )
    }
}

@Composable
private fun LockedVaultScreen(
    modifier: Modifier,
    pinInput: String,
    setupMode: Boolean,
    confirmPinInput: String,
    lockoutSeconds: Int,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onBiometric: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Box(
            modifier = Modifier.size(72.dp).background(Color(0xFF2C82C9), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Lock, null, tint = Color.White)
        }
        Spacer(Modifier.height(14.dp))
        Text("Private Vault", fontWeight = FontWeight.Bold)
        Text("Your files are encrypted and hidden", color = Color.Gray)
        if (setupMode) {
            Text(if (pinInput.length < 4) "Set a new 4-digit PIN" else "Confirm your PIN", modifier = Modifier.padding(top = 6.dp))
        }
        if (lockoutSeconds > 0) {
            Text("Try again in ${lockoutSeconds}s", color = Color.Red, modifier = Modifier.padding(top = 6.dp))
        }

        Spacer(Modifier.height(16.dp))
        val dots = if (setupMode && pinInput.length >= 4) confirmPinInput.length else pinInput.length
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) { idx ->
                Box(
                    modifier = Modifier.size(12.dp).background(if (idx < dots) Color(0xFF2C82C9) else Color.LightGray, CircleShape)
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth().height(280.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(keys) { key ->
                if (key.isEmpty()) {
                    Spacer(Modifier)
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth().height(62.dp).clickable {
                            if (key == "⌫") onDelete() else onDigit(key)
                        },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(key) }
                    }
                }
            }
        }

        Button(onClick = onBiometric) { Text("Use fingerprint instead") }
    }
}

@Composable
private fun VaultUnlockedScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val files = remember { mutableStateListOf<VaultFile>() }
    var selectedTab by remember { mutableIntStateOf(0) }

    fun refresh() {
        files.clear()
        files.addAll(VaultManager.listVaultFiles(context))
    }

    val addFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = VaultManager.importUriToVault(context, uri)
            if (imported.isSuccess) {
                refresh()
                Toast.makeText(context, "Added to Vault", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    imported.exceptionOrNull()?.message ?: "Unable to add file to Vault",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val filtered = when (selectedTab) {
        1 -> files.filter { it.type == VaultType.VIDEO }
        2 -> files.filter { it.type == VaultType.PHOTO }
        else -> files
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("My Vault", fontWeight = FontWeight.Bold)
                val totalBytes = files.sumOf { it.sizeBytes }
                Text("${files.size} files • ${formatBytes(totalBytes)}", color = Color.Gray)
            }
            Button(onClick = { addFileLauncher.launch(arrayOf("*/*")) }) {
                Text("+ Add")
            }
        }

        Spacer(Modifier.height(8.dp))
        TabRow(selectedTabIndex = selectedTab) {
            listOf("All", "Videos", "Photos").forEachIndexed { idx, label ->
                Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(label) })
            }
        }

        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp)) {
            items(filtered) { vf ->
                Card(modifier = Modifier.fillMaxWidth().height(98.dp)) {
                    when (vf.type) {
                        VaultType.VIDEO -> Box(
                            modifier = Modifier.fillMaxSize().background(Color(0xFF0D47A1)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.PlayArrow, null, tint = Color.White) }
                        VaultType.PHOTO -> {
                            val bmp = remember(vf.id) { VaultManager.decryptImagePreview(context, vf) }
                            if (bmp != null) {
                                androidx.compose.foundation.Image(bitmap = bmp.asImageBitmap(), contentDescription = vf.originalName, modifier = Modifier.fillMaxSize())
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Img") }
                            }
                        }
                        VaultType.OTHER -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("File") }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Lock, null, tint = Color(0xFF2E7D32))
            Text("AES-256 encrypted")
            Text("•")
            Text("Hidden from gallery")
            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32))
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.1f GB".format(mb / 1024.0)
}
