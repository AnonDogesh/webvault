package com.webvault.browser

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val filename: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: String,
    val filePath: String
)
