package com.webvault.browser

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.webvaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "webvault_prefs")

object AppPreferences {
    private val adBlockEnabledKey = booleanPreferencesKey("ad_block_enabled")
    private val httpsEverywhereEnabledKey = booleanPreferencesKey("https_everywhere_enabled")
    private val videoSnifferEnabledKey = booleanPreferencesKey("video_sniffer_enabled")
    private val biometricLockEnabledKey = booleanPreferencesKey("biometric_lock_enabled")
    private val defaultSearchEngineKey = stringPreferencesKey("default_search_engine")
    private val downloadQualityKey = stringPreferencesKey("download_quality")
    private val appCloseBehaviorKey = stringPreferencesKey("app_close_behavior")
    private val appOpenBehaviorKey = stringPreferencesKey("app_open_behavior")
    private val jsRulesKey = stringSetPreferencesKey("js_rules")

    fun adBlockEnabledFlow(context: Context): Flow<Boolean> = context.webvaultDataStore.data.map {
        it[adBlockEnabledKey] ?: true
    }

    fun httpsEverywhereEnabledFlow(context: Context): Flow<Boolean> = context.webvaultDataStore.data.map {
        it[httpsEverywhereEnabledKey] ?: true
    }

    fun videoSnifferEnabledFlow(context: Context): Flow<Boolean> = context.webvaultDataStore.data.map {
        it[videoSnifferEnabledKey] ?: true
    }

    fun biometricLockEnabledFlow(context: Context): Flow<Boolean> = context.webvaultDataStore.data.map {
        it[biometricLockEnabledKey] ?: true
    }

    fun defaultSearchEngineFlow(context: Context): Flow<String> = context.webvaultDataStore.data.map {
        it[defaultSearchEngineKey] ?: defaultSearchEngine().id
    }

    fun downloadQualityFlow(context: Context): Flow<String> = context.webvaultDataStore.data.map {
        it[downloadQualityKey] ?: "Prefer 1080p"
    }

    fun appCloseBehaviorFlow(context: Context): Flow<String> = context.webvaultDataStore.data.map {
        it[appCloseBehaviorKey] ?: "save_tabs"
    }

    fun appOpenBehaviorFlow(context: Context): Flow<String> = context.webvaultDataStore.data.map {
        it[appOpenBehaviorKey] ?: "home"
    }

    suspend fun setAdBlockEnabled(context: Context, enabled: Boolean) {
        context.webvaultDataStore.edit { prefs ->
            prefs[adBlockEnabledKey] = enabled
        }
    }

    suspend fun setHttpsEverywhereEnabled(context: Context, enabled: Boolean) {
        context.webvaultDataStore.edit { prefs ->
            prefs[httpsEverywhereEnabledKey] = enabled
        }
    }

    suspend fun setVideoSnifferEnabled(context: Context, enabled: Boolean) {
        context.webvaultDataStore.edit { prefs ->
            prefs[videoSnifferEnabledKey] = enabled
        }
    }

    suspend fun setBiometricLockEnabled(context: Context, enabled: Boolean) {
        context.webvaultDataStore.edit { prefs ->
            prefs[biometricLockEnabledKey] = enabled
        }
    }

    suspend fun setDefaultSearchEngine(context: Context, searchEngineId: String) {
        context.webvaultDataStore.edit { prefs ->
            prefs[defaultSearchEngineKey] = searchEngineId
        }
    }

    suspend fun setDownloadQuality(context: Context, quality: String) {
        context.webvaultDataStore.edit { prefs ->
            prefs[downloadQualityKey] = quality
        }
    }

    suspend fun setAppCloseBehavior(context: Context, behavior: String) {
        context.webvaultDataStore.edit { prefs ->
            prefs[appCloseBehaviorKey] = behavior
        }
    }

    suspend fun setAppOpenBehavior(context: Context, behavior: String) {
        context.webvaultDataStore.edit { prefs ->
            prefs[appOpenBehaviorKey] = behavior
        }
    }

    suspend fun isAdBlockEnabled(context: Context): Boolean = adBlockEnabledFlow(context).first()

    suspend fun setJavaScriptAllowed(context: Context, domain: String, allowed: Boolean) {
        if (domain.isBlank()) return
        context.webvaultDataStore.edit { prefs ->
            val current = prefs[jsRulesKey].orEmpty().toMutableSet()
            current.removeAll { it.startsWith("$domain|") }
            current.add("$domain|${if (allowed) "allow" else "block"}")
            prefs[jsRulesKey] = current
        }
    }

    suspend fun isJavaScriptAllowed(context: Context, domain: String): Boolean {
        if (domain.isBlank()) return true
        val rules = context.webvaultDataStore.data.first()[jsRulesKey].orEmpty()
        val rule = rules.firstOrNull { it.startsWith("$domain|") }
        return rule?.endsWith("allow") ?: true
    }
}
