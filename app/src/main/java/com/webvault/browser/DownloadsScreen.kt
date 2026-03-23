package com.webvault.browser

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val dao = remember { WebvaultDatabase.get(context).downloadDao() }
    val downloads by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val refreshingState = remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = refreshingState.value,
        onRefresh = {
            scope.launch {
                refreshingState.value = true
                delay(600)
                refreshingState.value = false
            }
        }
    )

    BackHandler(onBack = onNavigateBack)

    Box(modifier = modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Downloads",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(downloads) { d ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = d.filename)
                            if (d.status == "COMPLETE" || d.status == "VAULTED") {
                                AssistChip(onClick = {}, label = { Text(if (d.status == "VAULTED") "In Vault" else "Complete") })
                                if (d.status != "VAULTED") {
                                    Button(onClick = {
                                        scope.launch {
                                            val moved = VaultManager.moveDownloadToVault(context, d)
                                            if (moved.isSuccess) {
                                                dao.upsert(d.copy(status = "VAULTED", filePath = moved.getOrNull()?.absolutePath.orEmpty()))
                                                Toast.makeText(context, "Moved to Vault", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Vault move failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }) { Text("→ Vault") }
                                }
                            } else {
                                val progress = if (d.totalBytes > 0) d.downloadedBytes.toFloat() / d.totalBytes.toFloat() else 0f
                                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(d.status)
                                    Text("${(progress * 100).toInt()}%")
                                }
                            }
                        }
                    }
                }
            }
        }

        PullRefreshIndicator(refreshing = refreshingState.value, state = pullRefreshState, modifier = Modifier.align(Alignment.TopCenter))
    }
}
