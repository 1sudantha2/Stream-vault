package com.streamvault.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderManager
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

/**
 * NextPlayer-style playback service. One Media3 player is kept alive while the
 * Activity is backgrounded. NextLib uses the device hardware codec first and
 * provides Android software/FFmpeg fallback modes when requested or required.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var decoderManager: DecoderManager? = null

    override fun onCreate() {
        super.onCreate()
        val manager = DecoderManager()
        decoderManager = manager
        val renderersFactory = NextRenderersFactory(this).setDecoderManager(manager)
        val builtPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { it.setWakeMode(C.WAKE_MODE_NETWORK) }
        manager.attach(builtPlayer)
        player = builtPlayer

        val launchIntent = Intent(this, MainActivity::class.java)
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, builtPlayer)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        decoderManager?.detach()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        decoderManager = null
        super.onDestroy()
    }
}
