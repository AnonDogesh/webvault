package com.webvault.browser

import android.graphics.Bitmap
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class Tab(
    val id: Int,
    val url: String,
    val title: String,
    val faviconBitmap: Bitmap? = null
)

object TabManager {
    private const val MAX_TABS = 10
    private var nextTabId = 2

    private val _tabs = MutableStateFlow(listOf(Tab(id = 1, url = "", title = "New Tab")))
    val tabs: StateFlow<List<Tab>> = _tabs

    private val _activeTabId = MutableStateFlow(1)
    val activeTabId: StateFlow<Int> = _activeTabId

    private val savedStates = mutableMapOf<Int, Bundle>()

    fun maxTabs(): Int = MAX_TABS

    fun getSavedState(tabId: Int): Bundle? = savedStates[tabId]

    fun saveState(tabId: Int, bundle: Bundle) {
        savedStates[tabId] = bundle
    }

    fun openNewTab(): Boolean {
        if (_tabs.value.size >= MAX_TABS) return false
        val tab = Tab(id = nextTabId++, url = "", title = "New Tab")
        _tabs.update { it + tab }
        _activeTabId.value = tab.id
        return true
    }

    fun switchToTab(tabId: Int) {
        if (_tabs.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
        }
    }

    fun closeTab(tabId: Int) {
        val currentTabs = _tabs.value
        if (currentTabs.size <= 1) return

        val newTabs = currentTabs.filterNot { it.id == tabId }
        _tabs.value = newTabs
        savedStates.remove(tabId)

        if (_activeTabId.value == tabId) {
            _activeTabId.value = newTabs.first().id
        }
    }

    fun updateActiveTab(
        url: String? = null,
        title: String? = null,
        favicon: Bitmap? = null
    ) {
        val activeId = _activeTabId.value
        _tabs.update { tabs ->
            tabs.map { tab ->
                if (tab.id == activeId) {
                    tab.copy(
                        url = url ?: tab.url,
                        title = title ?: tab.title,
                        faviconBitmap = favicon ?: tab.faviconBitmap
                    )
                } else tab
            }
        }
    }
}
