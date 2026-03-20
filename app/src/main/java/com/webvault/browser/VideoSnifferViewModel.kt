package com.webvault.browser

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

data class DetectedVideo(
    val url: String,
    val filename: String,
    val estimatedSize: Long = 0L,
    val format: String
)

class VideoSnifferViewModel : ViewModel() {
    private val known = linkedSetOf<String>()
    private val _videos = MutableStateFlow<List<DetectedVideo>>(emptyList())
    val videos: StateFlow<List<DetectedVideo>> = _videos.asStateFlow()

    fun onVideoDetected(url: String, type: String? = null, contentLength: Long = 0L) {
        if (url.isBlank()) return
        if (known.contains(url)) return
        known.add(url)

        val filename = parseFilename(url)
        val format = type ?: url.substringAfterLast('.', "video").lowercase()

        _videos.value = _videos.value + DetectedVideo(
            url = url,
            filename = filename,
            estimatedSize = contentLength,
            format = format
        )
    }

    fun removeVideo(url: String) {
        _videos.value = _videos.value.filterNot { it.url == url }
    }

    private fun parseFilename(url: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val raw = clean.substringAfterLast('/', "video_${UUID.randomUUID()}")
        return URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
    }
}
