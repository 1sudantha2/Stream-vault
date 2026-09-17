package com.streamvault.player.data

data class DownloadJob(
    val id: String,
    val title: String,
    val url: String,
    val status: String,
    val downloadPercent: Float = 0f,
    val transcodePercent: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadSpeed: Long = 0L,
    val etaSeconds: Long = 0L,
    val error: String? = null,
)
