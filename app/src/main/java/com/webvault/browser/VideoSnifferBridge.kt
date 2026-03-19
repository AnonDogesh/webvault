package com.webvault.browser

import android.webkit.JavascriptInterface

class VideoSnifferBridge(
    private val vm: VideoSnifferViewModel
) {
    @JavascriptInterface
    fun onVideoDetected(url: String, type: String?) {
        vm.onVideoDetected(url = url, type = type)
    }
}
