package com.streamvault.player.data

/** A video returned by the Stream-Vault web API, or a local SAF document. */
data class VideoItem(
    val id: String,
    val title: String,
    val durationSeconds: Double? = null,
    val sizeBytes: Long? = null,
    val createdAt: String? = null,
    val thumbnail: String? = null,
    val streamPath: String? = null,
    val isLocal: Boolean = false,
    val localUri: String? = null
)

data class AppSettings(
    val serverUrl: String = "",
    val keepScreenOn: Boolean = true,
    val preferHardwareDecoding: Boolean = true,
    val autoplay: Boolean = true,
    val defaultPlaybackSpeed: Float = 1f,
    val subtitleDelayMs: Long = 0L
)
