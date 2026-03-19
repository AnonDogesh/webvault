package com.webvault.browser

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

object AdBlocker {
    private val initialized = AtomicBoolean(false)
    private val blockedDomains = mutableSetOf<String>()

    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount

    fun initialize(context: Context) {
        if (initialized.get()) return
        synchronized(this) {
            if (initialized.get()) return
            runCatching {
                context.assets.open("blocklist.txt").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            blockedDomains.add(trimmed.lowercase())
                        }
                    }
                }
            }
            initialized.set(true)
        }
    }

    fun resetCount() {
        _blockedCount.value = 0
    }

    fun interceptIfBlocked(url: String?): WebResourceResponse? {
        val host = runCatching { Uri.parse(url).host.orEmpty().lowercase() }.getOrDefault("")
        if (host.isBlank()) return null

        val blocked = blockedDomains.contains(host) || blockedDomains.any { host.endsWith(".$it") }
        if (!blocked) return null

        _blockedCount.value = _blockedCount.value + 1
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
