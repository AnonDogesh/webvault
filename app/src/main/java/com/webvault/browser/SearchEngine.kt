package com.webvault.browser

import android.net.Uri

data class SearchEngine(
    val id: String,
    val label: String,
    val queryUrlPrefix: String
)

private val searchEngines = listOf(
    SearchEngine(id = "duckduckgo", label = "DuckDuckGo", queryUrlPrefix = "https://duckduckgo.com/?q="),
    SearchEngine(id = "startpage", label = "Startpage", queryUrlPrefix = "https://www.startpage.com/do/dsearch?query="),
    SearchEngine(id = "bing", label = "Bing", queryUrlPrefix = "https://www.bing.com/search?q="),
    SearchEngine(id = "google", label = "Google", queryUrlPrefix = "https://www.google.com/search?q=")
)

fun allSearchEngines(): List<SearchEngine> = searchEngines

fun defaultSearchEngine(): SearchEngine = searchEngines.first()

fun searchEngineById(id: String): SearchEngine = searchEngines.firstOrNull { it.id == id } ?: defaultSearchEngine()

fun buildSearchUrl(query: String, searchEngineId: String): String {
    val engine = searchEngineById(searchEngineId)
    return engine.queryUrlPrefix + Uri.encode(query)
}
