package com.webvault.browser

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.webvaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "webvault_prefs")

object AppPreferences {
    private val adBlockEnabledKey = booleanPreferencesKey("ad_block_enabled")
    private val jsRulesKey = stringSetPreferencesKey("js_rules")

    fun adBlockEnabledFlow(context: Context): Flow<Boolean> = context.webvaultDataStore.data.map {
        it[adBlockEnabledKey] ?: true
    }

    suspend fun setAdBlockEnabled(context: Context, enabled: Boolean) {
        context.webvaultDataStore.edit { prefs ->
            prefs[adBlockEnabledKey] = enabled
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
