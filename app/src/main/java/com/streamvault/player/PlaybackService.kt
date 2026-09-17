package com.streamvault.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderManager
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
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

    private companion object {
        const val DECODER_MODE_KEY = "decoder_mode"
        val VIDEO_DECODER_COMMAND = SessionCommand("SET_VIDEO_DECODER_MODE", Bundle.EMPTY)
        val AUDIO_DECODER_COMMAND = SessionCommand("SET_AUDIO_DECODER_MODE", Bundle.EMPTY)
    }

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnectAsync(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.ConnectionResult> = Futures.immediateFuture(
            MediaSession.ConnectionResult.accept(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon()
                    .addSessionCommands(listOf(VIDEO_DECODER_COMMAND, AUDIO_DECODER_COMMAND))
                    .build(),
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
            ),
        )

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val mode = DecoderMode.entries.firstOrNull { it.name == args.getString(DECODER_MODE_KEY) }
                ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
            val manager = decoderManager
                ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))
            val currentPlayer = player
            when (customCommand.customAction) {
                VIDEO_DECODER_COMMAND.customAction -> {
                    manager.selectVideoDecoder(mode)
                    currentPlayer?.applyDecoderMode("media_metadata_video_decoder_mode", mode)
                }
                AUDIO_DECODER_COMMAND.customAction -> {
                    manager.selectAudioDecoder(mode)
                    currentPlayer?.applyDecoderMode("media_metadata_audio_decoder_mode", mode)
                }
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

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
        // Hardware is the low-CPU default; NextLib can still fall back when a codec is unavailable.
        manager.selectVideoDecoder(DecoderMode.HARDWARE)
        manager.selectAudioDecoder(DecoderMode.HARDWARE)
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
            .setCallback(mediaSessionCallback)
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

private fun ExoPlayer.applyDecoderMode(key: String, mode: DecoderMode) {
    val item = currentMediaItem ?: return
    val wasPlaying = isPlaying
    val position = currentPosition
    val extras = Bundle(item.mediaMetadata.extras ?: Bundle()).apply { putString(key, mode.name) }
    val updated = item.buildUpon()
        .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build())
        .build()
    replaceMediaItem(currentMediaItemIndex, updated)
    seekTo(position)
    if (wasPlaying) play()
}
