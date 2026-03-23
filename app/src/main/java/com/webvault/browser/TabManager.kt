package com.webvault.browser

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class Tab(
    val id: Int,
    val url: String,
    val pendingUrl: String,
    val title: String,
    val faviconBitmap: Bitmap? = null,
    val isDesktopMode: Boolean = false,
    val searchEngineId: String = defaultSearchEngine().id
)

object TabManager {
    private const val MAX_TABS = 10
    private const val PREFS_NAME = "webvault_tabs"
    private const val KEY_ACTIVE_TAB_ID = "active_tab_id"
    private const val KEY_NEXT_TAB_ID = "next_tab_id"
    private const val KEY_TABS = "tabs"
    private var nextTabId = 2
    private var appContext: Context? = null
    private var restoredFromDisk = false

    private val _tabs = MutableStateFlow(listOf(Tab(id = 1, url = "", pendingUrl = "", title = "New Tab")))
    val tabs: StateFlow<List<Tab>> = _tabs

    private val _activeTabId = MutableStateFlow(1)
    val activeTabId: StateFlow<Int> = _activeTabId

    private val savedStates = mutableMapOf<Int, Bundle>()

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (!restoredFromDisk) {
            restorePersistedTabs()
            restoredFromDisk = true
        }
    }

    fun maxTabs() = MAX_TABS

    fun getSavedState(tabId: Int): Bundle? = savedStates[tabId]

    fun saveState(tabId: Int, bundle: Bundle) {
        savedStates[tabId] = bundle
    }

    fun openNewTab(): Boolean {
        if (_tabs.value.size >= MAX_TABS) return false
        val tab = Tab(id = nextTabId++, url = "", pendingUrl = "", title = "New Tab")
        _tabs.update { it + tab }
        _activeTabId.value = tab.id
        persistTabs()
        return true
    }

    fun switchToTab(tabId: Int) {
        if (_tabs.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
            persistTabs()
        }
    }

    fun closeTab(tabId: Int) {
        val current = _tabs.value
        if (current.size <= 1) return
        val newTabs = current.filterNot { it.id == tabId }
        _tabs.value = newTabs
        savedStates.remove(tabId)
        if (_activeTabId.value == tabId) _activeTabId.value = newTabs.first().id
        persistTabs()
    }

    fun submitUrl(url: String) {
        val activeId = _activeTabId.value
        _tabs.update { tabs ->
            tabs.map { if (it.id == activeId) it.copy(pendingUrl = url) else it }
        }
        persistTabs()
    }

    fun commitUrl(url: String) {
        val activeId = _activeTabId.value
        _tabs.update { tabs ->
            tabs.map { if (it.id == activeId) it.copy(url = url, pendingUrl = "") else it }
        }
        persistTabs()
    }

    fun updateActiveTab(
        title: String? = null,
        favicon: Bitmap? = null,
        desktopMode: Boolean? = null,
        searchEngineId: String? = null
    ) {
        val activeId = _activeTabId.value
        _tabs.update { tabs ->
            tabs.map { tab ->
                if (tab.id == activeId) {
                    tab.copy(
                        title = title ?: tab.title,
                        faviconBitmap = favicon ?: tab.faviconBitmap,
                        isDesktopMode = desktopMode ?: tab.isDesktopMode,
                        searchEngineId = searchEngineId ?: tab.searchEngineId
                    )
                } else {
                    tab
                }
            }
        }
        persistTabs()
    }

    fun clearPersistedTabs() {
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
    }

    fun hasSavedTabs(): Boolean {
        val current = _tabs.value
        return current.any { it.url.isNotBlank() || it.pendingUrl.isNotBlank() } || current.size > 1
    }

    private fun persistTabs() {
        val context = appContext ?: return
        val tabsJson = JSONArray()
        _tabs.value.forEach { tab ->
            tabsJson.put(
                JSONObject()
                    .put("id", tab.id)
                    .put("url", tab.url)
                    .put("pendingUrl", tab.pendingUrl)
                    .put("title", tab.title)
                    .put("isDesktopMode", tab.isDesktopMode)
                    .put("searchEngineId", tab.searchEngineId)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TABS, tabsJson.toString())
            .putInt(KEY_ACTIVE_TAB_ID, _activeTabId.value)
            .putInt(KEY_NEXT_TAB_ID, nextTabId)
            .apply()
    }

    private fun restorePersistedTabs() {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val tabsString = prefs.getString(KEY_TABS, null).orEmpty()
        if (tabsString.isBlank()) return

        val restoredTabs = buildList {
            val jsonArray = JSONArray(tabsString)
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                add(
                    Tab(
                        id = item.optInt("id", index + 1),
                        url = item.optString("url", ""),
                        pendingUrl = item.optString("pendingUrl", ""),
                        title = item.optString("title", "New Tab"),
                        isDesktopMode = item.optBoolean("isDesktopMode", false),
                        searchEngineId = item.optString("searchEngineId", defaultSearchEngine().id)
                    )
                )
            }
        }.ifEmpty {
            listOf(Tab(id = 1, url = "", pendingUrl = "", title = "New Tab"))
        }

        _tabs.value = restoredTabs
        _activeTabId.value = prefs.getInt(KEY_ACTIVE_TAB_ID, restoredTabs.first().id)
            .takeIf { activeId -> restoredTabs.any { it.id == activeId } }
            ?: restoredTabs.first().id
        nextTabId = prefs.getInt(
            KEY_NEXT_TAB_ID,
            restoredTabs.maxOfOrNull { it.id + 1 } ?: 2
        ).coerceAtLeast((restoredTabs.maxOfOrNull { it.id } ?: 1) + 1)
    }
}
