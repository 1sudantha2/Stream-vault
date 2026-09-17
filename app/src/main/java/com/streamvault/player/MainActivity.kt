package com.streamvault.player

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.streamvault.player.data.AppSettings
import com.streamvault.player.data.SettingsRepository
import com.streamvault.player.data.VideoItem
import com.streamvault.player.ui.HomeScreen
import com.streamvault.player.ui.PlayerScreen
import com.streamvault.player.ui.SettingsScreen
import com.streamvault.player.ui.StreamVaultTheme
import com.streamvault.player.util.guessTitleFromUri
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {
    private lateinit var settingsRepository: SettingsRepository
    private var mediaController by mutableStateOf<MediaController?>(null)
    private var selectedVideo by mutableStateOf<VideoItem?>(null)
    private var subtitleUri by mutableStateOf<Uri?>(null)

    private val localVideoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        persistPermission(uri)
        selectedVideo = VideoItem(
            id = uri.toString(),
            title = guessTitleFromUri(uri.toString()),
            isLocal = true,
            localUri = uri.toString()
        )
    }

    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        persistPermission(uri)
        subtitleUri = uri
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(applicationContext)

        val controllerFuture: ListenableFuture<MediaController> = MediaController.Builder(
            this,
            SessionToken(this, ComponentName(this, PlaybackService::class.java))
        ).buildAsync()
        controllerFuture.addListener(
            { mediaController = runCatching { controllerFuture.get() }.getOrNull() },
            mainExecutor
        )

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            StreamVaultTheme {
                StreamVaultRoot(
                    settings = settings,
                    controller = mediaController,
                    selectedVideo = selectedVideo,
                    subtitleUri = subtitleUri,
                    page = page,
                    onOpenSettings = { page = Page.SETTINGS },
                    onPlayVideo = {
                        selectedVideo = it
                        subtitleUri = null
                        page = Page.PLAYER
                    },
                    onPlayUrl = { url, title ->
                        selectedVideo = VideoItem(id = url, title = title.ifBlank { guessTitleFromUri(url) }, streamPath = url)
                        subtitleUri = null
                        page = Page.PLAYER
                    },
                    onOpenLocalVideo = { localVideoPicker.launch(arrayOf("video/*")) },
                    onOpenSubtitle = {
                        subtitleUri = null
                        subtitlePicker.launch(arrayOf("text/*", "application/x-subrip", "application/ttml+xml", "text/vtt"))
                    },
                    onSaveServer = { value -> settingsRepository.setServerUrl(value) },
                    onSetKeepScreenOn = { settingsRepository.setKeepScreenOn(it) },
                    onSetHardware = { settingsRepository.setPreferHardware(it) },
                    onSetAutoplay = { settingsRepository.setAutoplay(it) },
                    onSetSpeed = { settingsRepository.setSpeed(it) },
                    onSetSubtitleDelay = { settingsRepository.setSubtitleDelay(it) },
                    onBackFromPlayer = {
                        selectedVideo = null
                        subtitleUri = null
                        page = Page.HOME
                    },
                    onBackFromSettings = { page = Page.HOME }
                )
            }
        }
    }

    private var page by mutableStateOf(Page.HOME)

    private fun persistPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers do not offer persistable permissions; the current grant is enough.
        }
    }

    override fun onDestroy() {
        mediaController?.release()
        mediaController = null
        super.onDestroy()
    }
}

private enum class Page { HOME, PLAYER, SETTINGS }

@androidx.compose.runtime.Composable
private fun StreamVaultRoot(
    settings: AppSettings,
    controller: MediaController?,
    selectedVideo: VideoItem?,
    subtitleUri: Uri?,
    page: Page,
    onOpenSettings: () -> Unit,
    onPlayVideo: (VideoItem) -> Unit,
    onPlayUrl: (String, String) -> Unit,
    onOpenLocalVideo: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onSaveServer: suspend (String) -> Unit,
    onSetKeepScreenOn: suspend (Boolean) -> Unit,
    onSetHardware: suspend (Boolean) -> Unit,
    onSetAutoplay: suspend (Boolean) -> Unit,
    onSetSpeed: suspend (Float) -> Unit,
    onSetSubtitleDelay: suspend (Long) -> Unit,
    onBackFromPlayer: () -> Unit,
    onBackFromSettings: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(settings.keepScreenOn) {
        val activity = context as? ComponentActivity ?: return@LaunchedEffect
        if (settings.keepScreenOn) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    when {
        selectedVideo != null -> {
            PlayerScreen(
                video = selectedVideo,
                serverUrl = settings.serverUrl,
                player = controller,
                externalSubtitleUri = subtitleUri,
                autoplay = settings.autoplay,
                defaultSpeed = settings.defaultPlaybackSpeed,
                subtitleDelayMs = settings.subtitleDelayMs,
                onOpenSubtitle = onOpenSubtitle,
                onSetSpeed = onSetSpeed,
                onSetSubtitleDelay = onSetSubtitleDelay,
                onBack = onBackFromPlayer
            )
        }
        else -> when (page) {
            Page.HOME -> HomeScreen(
                serverUrl = settings.serverUrl,
                onOpenSettings = onOpenSettings,
                onPlayVideo = onPlayVideo,
                onPlayUrl = onPlayUrl,
                onOpenLocalVideo = onOpenLocalVideo
            )
            Page.SETTINGS -> SettingsScreen(
                settings = settings,
                onBack = onBackFromSettings,
                onSaveServer = onSaveServer,
                onSetKeepScreenOn = onSetKeepScreenOn,
                onSetHardware = onSetHardware,
                onSetAutoplay = onSetAutoplay
            )
            Page.PLAYER -> HomeScreen(
                serverUrl = settings.serverUrl,
                onOpenSettings = onOpenSettings,
                onPlayVideo = onPlayVideo,
                onPlayUrl = onPlayUrl,
                onOpenLocalVideo = onOpenLocalVideo
            )
        }
    }
}

