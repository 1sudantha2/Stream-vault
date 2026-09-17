package com.streamvault.player.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.streamvault.player.data.ApiClient
import com.streamvault.player.data.VideoItem
import com.streamvault.player.util.formatDurationMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class PlayerDialog { NONE, OPTIONS, AUDIO, SUBTITLE, SPEED }

@Composable
fun PlayerScreen(
    video: VideoItem,
    serverUrl: String,
    player: Player?,
    externalSubtitleUri: Uri?,
    autoplay: Boolean,
    defaultSpeed: Float,
    subtitleDelayMs: Long = 0L,
    onOpenSubtitle: () -> Unit,
    onSetSpeed: suspend (Float) -> Unit = {},
    onSetSubtitleDelay: suspend (Long) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val source = remember(video, serverUrl) {
        if (video.isLocal) video.localUri
        else ApiClient.streamUrl(serverUrl, video.streamPath)
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var gestureText by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf(PlayerDialog.NONE) }
    var fullscreen by remember { mutableStateOf(false) }
    var seeking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackHandler {
        if (fullscreen) {
            fullscreen = false
            setFullscreen(activity, false)
        } else onBack()
    }

    LaunchedEffect(player, source, externalSubtitleUri) {
        if (player == null || source.isNullOrBlank()) return@LaunchedEffect
        val itemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(source))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(video.title).build())
        externalSubtitleUri?.let { uri ->
            itemBuilder.setSubtitleConfigurations(listOf(subtitleConfiguration(uri)))
        }
        player.setMediaItem(itemBuilder.build(), 0L)
        player.setPlaybackParameters(PlaybackParameters(defaultSpeed.coerceIn(.25f, 4f)))
        player.prepare()
        if (autoplay) player.play()
    }

    LaunchedEffect(player) {
        while (true) {
            if (player != null) {
                isPlaying = player.isPlaying
                if (!seeking) positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration.takeIf { it > 0 } ?: 0L
            }
            delay(250)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!fullscreen) {
                    PlayerTopBar(video.title, controlsVisible, onBack)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.weight(1f))
                        .background(Color.Black)
                        .onSizeChanged { viewSize = it }
                ) {
                    if (source.isNullOrBlank()) {
                        Text("No playable stream URL was returned by the server.", color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                }
                            },
                            update = { it.player = player },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { scaleX = zoom; scaleY = zoom }
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, _, scaleChange, _ ->
                                        zoom = (zoom * scaleChange).coerceIn(1f, 3f)
                                    }
                                }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(durationMs, positionMs, viewSize) {
                                    detectTapGestures(
                                        onTap = { controlsVisible = !controlsVisible },
                                        onDoubleTap = { offset ->
                                            if (viewSize.width > 0 && player != null) {
                                                val delta = when {
                                                    offset.x < viewSize.width * .4f -> -10_000L
                                                    offset.x > viewSize.width * .6f -> 10_000L
                                                    else -> 0L
                                                }
                                                if (delta == 0L) player.playWhenReady = !player.playWhenReady
                                                else player.seekTo((player.currentPosition + delta).coerceIn(0L, player.duration.coerceAtLeast(0L)))
                                                gestureText = if (delta < 0) "−10 seconds" else if (delta > 0) "+10 seconds" else null
                                                scope.launch { delay(700); gestureText = null }
                                            }
                                        }
                                    )
                                }
                                .pointerInput(durationMs, viewSize) {
                                    var startX = 0f
                                    var startPosition = 0L
                                    var dragMode = 0
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            startX = offset.x
                                            startPosition = player?.currentPosition ?: 0L
                                            dragMode = 0
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            if (player == null) return@detectDragGestures
                                            val horizontal = abs(change.position.x - startX) > abs(change.position.y - change.previousPosition.y)
                                            if (dragMode == 0 && (abs(change.position.x - startX) > 18 || abs(change.position.y - change.previousPosition.y) > 18)) {
                                                dragMode = if (horizontal) 1 else if (startX < viewSize.width / 2f) 2 else 3
                                            }
                                            when (dragMode) {
                                                1 -> {
                                                    val fraction = if (viewSize.width == 0) 0f else (change.position.x - startX) / viewSize.width
                                                    val target = (startPosition + (durationMs * fraction).toLong()).coerceIn(0L, durationMs)
                                                    player.seekTo(target)
                                                    positionMs = target
                                                    gestureText = formatDurationMs(target)
                                                }
                                                2 -> {
                                                    val brightness = updateBrightness(activity, -dragAmount.y / viewSize.height.coerceAtLeast(1))
                                                    gestureText = "Brightness ${((brightness * 100).roundToInt())}%"
                                                }
                                                3 -> {
                                                    val volume = updateVolume(context, -dragAmount.y / viewSize.height.coerceAtLeast(1))
                                                    gestureText = "Volume ${((volume * 100).roundToInt())}%"
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            scope.launch { delay(800); gestureText = null }
                                        }
                                    )
                                }
                        )

                        if (controlsVisible) {
                            PlayerOverlay(
                                title = video.title,
                                isPlaying = isPlaying,
                                positionMs = positionMs,
                                durationMs = durationMs,
                                zoom = zoom,
                                fullscreen = fullscreen,
                                onPlayPause = { if (player?.isPlaying == true) player.pause() else player?.play() },
                                onSeek = { player?.seekTo(it); positionMs = it },
                                onSkip = { seconds -> player?.seekTo((player.currentPosition + seconds * 1000L).coerceIn(0L, player.duration.coerceAtLeast(0L))) },
                                onOptions = { dialog = PlayerDialog.OPTIONS },
                                onPictureInPicture = { enterPip(activity) },
                                onFullscreen = {
                                    fullscreen = !fullscreen
                                    setFullscreen(activity, fullscreen)
                                },
                                onResetZoom = { zoom = 1f }
                            )
                        }
                        if (gestureText != null) {
                            Card(
                                modifier = Modifier.align(Alignment.Center),
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = .75f)),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text(gestureText ?: "", modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = Color.White) }
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        PlayerDialog.OPTIONS -> OptionsDialog(
            onDismiss = { dialog = PlayerDialog.NONE },
            onAudio = { dialog = PlayerDialog.AUDIO },
            onSubtitle = { dialog = PlayerDialog.SUBTITLE },
            onSpeed = { dialog = PlayerDialog.SPEED },
            onPip = { dialog = PlayerDialog.NONE; enterPip(activity) },
            onFullscreen = { dialog = PlayerDialog.NONE; fullscreen = !fullscreen; setFullscreen(activity, fullscreen) },
            onResetZoom = { dialog = PlayerDialog.NONE; zoom = 1f }
        )
        PlayerDialog.AUDIO -> TrackDialog(
            title = "Select audio track",
            player = player,
            trackType = C.TRACK_TYPE_AUDIO,
            onDismiss = { dialog = PlayerDialog.OPTIONS }
        )
        PlayerDialog.SUBTITLE -> SubtitleDialog(
            player = player,
            subtitleDelayMs = subtitleDelayMs,
            onDismiss = { dialog = PlayerDialog.OPTIONS },
            onOpenLocal = { dialog = PlayerDialog.NONE; onOpenSubtitle() },
            onSetDelay = { value -> scope.launch { onSetSubtitleDelay(value) } }
        )
        PlayerDialog.SPEED -> SpeedDialog(
            player = player,
            onDismiss = { dialog = PlayerDialog.OPTIONS },
            onSpeedSelected = { scope.launch { onSetSpeed(it) } }
        )
        PlayerDialog.NONE -> Unit
    }
}

@Composable
private fun PlayerTopBar(title: String, controlsVisible: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xF2080B0F)).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
        Text(title, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        if (controlsVisible) Icon(Icons.Rounded.MoreVert, "Player options", tint = VaultMuted, modifier = Modifier.padding(horizontal = 12.dp))
    }
}

@Composable
private fun PlayerOverlay(
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    zoom: Float,
    fullscreen: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onOptions: () -> Unit,
    onPictureInPicture: () -> Unit,
    onFullscreen: () -> Unit,
    onResetZoom: () -> Unit
) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(110.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color.Black.copy(.78f), Color.Transparent))))
        Box(modifier = Modifier.fillMaxWidth().height(150.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.9f)))))
        Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            IconButton(onClick = { onSkip(-10) }, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(35.dp)) }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(70.dp).clip(CircleShape).background(Cyan)) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Play", tint = Color(0xFF001F24), modifier = Modifier.size(42.dp))
            }
            IconButton(onClick = { onSkip(10) }, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.FastForward, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(35.dp)) }
        }
        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDurationMs(positionMs), color = Color.White, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Slider(
                    value = fraction,
                    onValueChange = { onSeek((it * durationMs).toLong()) },
                    modifier = Modifier.weight(1f).height(28.dp),
                    enabled = durationMs > 0
                )
                Spacer(Modifier.width(6.dp))
                Text(formatDurationMs(durationMs), color = Color.White, fontSize = 12.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VolumeUp, "Volume", tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOptions) { Icon(Icons.Rounded.Settings, null, tint = Color.White); Spacer(Modifier.width(4.dp)); Text("Options", color = Color.White) }
                IconButton(onClick = onPictureInPicture) { Icon(Icons.Rounded.PictureInPictureAlt, "Picture in picture", tint = Color.White) }
                IconButton(onClick = { if (zoom > 1.01f) onResetZoom() else onFullscreen() }) { Icon(if (zoom > 1.01f) Icons.Rounded.Close else if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, "Fullscreen / reset zoom", tint = Color.White) }
            }
        }
    }
}

@Composable
private fun OptionsDialog(
    onDismiss: () -> Unit,
    onAudio: () -> Unit,
    onSubtitle: () -> Unit,
    onSpeed: () -> Unit,
    onPip: () -> Unit,
    onFullscreen: () -> Unit,
    onResetZoom: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Player options") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OptionButton(Icons.Rounded.Audiotrack, "Audio track", onAudio)
                OptionButton(Icons.Rounded.Subtitles, "Subtitle track / local subtitle", onSubtitle)
                OptionButton(Icons.Rounded.Speed, "Playback speed", onSpeed)
                OptionButton(Icons.Rounded.PictureInPictureAlt, "Picture-in-picture", onPip)
                OptionButton(Icons.Rounded.Fullscreen, "Fullscreen", onFullscreen)
                OptionButton(Icons.Rounded.Close, "Reset zoom", onResetZoom)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun OptionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = Cyan)
        Spacer(Modifier.width(12.dp))
        Text(text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TrackDialog(title: String, player: Player?, trackType: Int, onDismiss: () -> Unit) {
    val groups = remember(player, trackType) { player?.currentTracks?.groups?.filter { it.type == trackType }.orEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (groups.isEmpty()) Text("No alternate tracks are available for this video.", color = VaultMuted)
                groups.forEach { group ->
                    for (index in 0 until group.length) {
                        val format = group.getTrackFormat(index)
                        val label = listOfNotNull(format.language?.uppercase(), format.label, format.sampleMimeType).joinToString(" • ").ifBlank { "Track ${index + 1}" }
                        TextButton(onClick = {
                            player?.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(trackType, false)
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
                                .build()
                            onDismiss()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(label, modifier = Modifier.weight(1f))
                            if (group.isTrackSelected(index)) Text("✓", color = Cyan)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun SubtitleDialog(
    player: Player?,
    subtitleDelayMs: Long,
    onDismiss: () -> Unit,
    onOpenLocal: () -> Unit,
    onSetDelay: (Long) -> Unit
) {
    val groups = remember(player) { player?.currentTracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT }.orEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select subtitle track") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    player?.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    onDismiss()
                }, modifier = Modifier.fillMaxWidth()) { Text("◉  Disable subtitles", modifier = Modifier.weight(1f)) }
                Button(onClick = onOpenLocal, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Open local subtitle") }
                groups.forEach { group ->
                    for (index in 0 until group.length) {
                        val format = group.getTrackFormat(index)
                        TextButton(onClick = {
                            player?.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
                                .build()
                            onDismiss()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(format.language?.uppercase() ?: format.label ?: "Subtitle ${index + 1}", modifier = Modifier.weight(1f))
                            if (group.isTrackSelected(index)) Text("✓", color = Cyan)
                        }
                    }
                }
                HorizontalDelayControl(subtitleDelayMs, onSetDelay)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun HorizontalDelayControl(delayMs: Long, onChange: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Subtitle delay", color = VaultMuted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onChange(delayMs - 250L) }) { Text("−") }
            Text("${delayMs / 1000.0}s", fontSize = 17.sp, color = Cyan)
            OutlinedButton(onClick = { onChange(delayMs + 250L) }) { Text("+") }
        }
    }
}

@Composable
private fun SpeedDialog(player: Player?, onDismiss: () -> Unit, onSpeedSelected: (Float) -> Unit) {
    val speeds = listOf(.25f, .5f, .75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f, 4f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select playback speed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                speeds.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEach { speed ->
                            OutlinedButton(onClick = { player?.setPlaybackParameters(PlaybackParameters(speed)); onSpeedSelected(speed); onDismiss() }, modifier = Modifier.weight(1f)) {
                                Text(speed.toString().removeSuffix(".0"))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun subtitleConfiguration(uri: Uri): MediaItem.SubtitleConfiguration {
    val name = uri.toString().lowercase()
    val mime = when {
        name.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        name.endsWith(".vtt") -> MimeTypes.TEXT_VTT
        name.endsWith(".ssa") || name.endsWith(".ass") -> MimeTypes.TEXT_SSA
        name.endsWith(".ttml") || name.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }
    return MediaItem.SubtitleConfiguration.Builder(uri)
        .setMimeType(mime)
        .setLanguage("und")
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()
}

private fun updateBrightness(activity: Activity?, delta: Float): Float {
    val window = activity?.window ?: return 0.5f
    val current = window.attributes.screenBrightness.takeIf { it > 0f } ?: .5f
    val next = (current + delta).coerceIn(.05f, 1f)
    window.attributes = window.attributes.apply { screenBrightness = next }
    return next
}

private fun updateVolume(context: Context, delta: Float): Float {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    val next = (current + (delta * max)).roundToInt().coerceIn(0, max)
    audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
    return next.toFloat() / max
}

private fun setFullscreen(activity: Activity?, enabled: Boolean) {
    activity ?: return
    WindowCompat.setDecorFitsSystemWindows(activity.window, !enabled)
    val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
    if (enabled) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}

private fun enterPip(activity: Activity?) {
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        activity.setPictureInPictureParams(
            PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
        )
    }
    activity.enterPictureInPictureMode(
        PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
    )
}
