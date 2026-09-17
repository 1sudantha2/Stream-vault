package com.streamvault.player.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.streamVaultDataStore by preferencesDataStore(name = "stream_vault_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val autoplay = booleanPreferencesKey("autoplay")
        val speed = floatPreferencesKey("playback_speed")
        val subtitleDelay = longPreferencesKey("subtitle_delay_ms")
    }

    val settings: Flow<AppSettings> = context.streamVaultDataStore.data.map { p ->
        AppSettings(
            serverUrl = p[Keys.serverUrl].orEmpty(),
            keepScreenOn = p[Keys.keepScreenOn] ?: true,
            autoplay = p[Keys.autoplay] ?: true,
            defaultPlaybackSpeed = p[Keys.speed] ?: 1f,
            subtitleDelayMs = p[Keys.subtitleDelay] ?: 0L
        )
    }

    suspend fun setServerUrl(value: String) = context.streamVaultDataStore.edit { it[Keys.serverUrl] = value.trim().removeSuffix("/") }
    suspend fun setKeepScreenOn(value: Boolean) = context.streamVaultDataStore.edit { it[Keys.keepScreenOn] = value }
    suspend fun setAutoplay(value: Boolean) = context.streamVaultDataStore.edit { it[Keys.autoplay] = value }
    suspend fun setSpeed(value: Float) = context.streamVaultDataStore.edit { it[Keys.speed] = value }
    suspend fun setSubtitleDelay(value: Long) = context.streamVaultDataStore.edit { it[Keys.subtitleDelay] = value }

    suspend fun playbackPosition(videoId: String): Long = context.streamVaultDataStore.data
        .map { it[playbackKey(videoId)] ?: 0L }
        .first()

    suspend fun savePlaybackPosition(videoId: String, positionMs: Long, durationMs: Long) {
        context.streamVaultDataStore.edit {
            val key = playbackKey(videoId)
            if (positionMs <= 0L || (durationMs > 0L && positionMs >= (durationMs - 5_000L).coerceAtLeast(0L))) it.remove(key)
            else if (positionMs >= 2_000L) it[key] = positionMs
        }
    }

    suspend fun clearPlaybackPosition(videoId: String) = context.streamVaultDataStore.edit {
        it.remove(playbackKey(videoId))
    }

    private fun playbackKey(videoId: String) = longPreferencesKey("position_" + videoId.hashCode().toUInt().toString(16))
}
