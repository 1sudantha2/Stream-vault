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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
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
import androidx.compose.material3.Dialog
import androidx.compose.material3.DialogProperties
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.media3.common.PlaybackException
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
private enum class VerticalGesture { BRIGHTNESS, VOLUME }

/**
 * A NextPlayer-inspired player surface: independent gesture detectors, a
 * persistent top/bottom control chrome, a lock state, side options sheet and
 * presentation feedback overlays. Playback itself remains a single Media3
 * player owned by PlaybackService.
 */
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
        if (video.isLocal) video.localUri else ApiClient.streamUrl(serverUrl, video.streamPath)
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsLocked by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var gestureText by remember { mutableStateOf<String?>(null) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var verticalGesture by remember { mutableStateOf<VerticalGesture?>(null) }
    var verticalValue by remember { mutableFloatStateOf(0f) }
    var dialog by remember { mutableStateOf(PlayerDialog.NONE) }
    var fullscreen by remember { mutableStateOf(false) }
    var fitVideo by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler {
        when {
            dialog != PlayerDialog.NONE -> dialog = PlayerDialog.NONE
            fullscreen -> {
                fullscreen = false
                setFullscreen(activity, false)
            }
            else -> onBack()
        }
    }

    LaunchedEffect(player, source, externalSubtitleUri) {
        if (player == null || source.isNullOrBlank()) return@LaunchedEffect
        val sameSource = player.currentMediaItem?.localConfiguration?.uri?.toString() == source
        val previousPosition = if (sameSource) player.currentPosition.coerceAtLeast(0L) else 0L
        val wasPlaying = player.isPlaying
        val builder = MediaItem.Builder()
            .setUri(Uri.parse(source))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(video.title).build())
        externalSubtitleUri?.let { builder.setSubtitleConfigurations(listOf(subtitleConfiguration(it))) }
        player.setMediaItem(builder.build(), previousPosition)
        player.setPlaybackParameters(PlaybackParameters(defaultSpeed.coerceIn(.25f, 4f)))
        player.prepare()
        if (wasPlaying || (!sameSource && autoplay)) player.play()
    }

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                errorText = error.message ?: "The stream could not be played."
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) errorText = null
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            player?.let {
                isPlaying = it.isPlaying
                isBuffering = it.playbackState == Player.STATE_BUFFERING
                positionMs = it.currentPosition.coerceAtLeast(0L)
                durationMs = it.duration.takeIf { value -> value > 0L } ?: 0L
            }
            delay(250)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, controlsLocked) {
        if (controlsVisible && isPlaying && !controlsLocked) {
            delay(3500)
            controlsVisible = false
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (!fullscreen) {
                    PlayerChromeTop(
                        title = video.title,
                        controlsVisible = controlsVisible,
                        onBack = onBack,
                        onAudio = { dialog = PlayerDialog.AUDIO },
                        onSubtitle = { dialog = PlayerDialog.SUBTITLE },
                        onSpeed = { dialog = PlayerDialog.SPEED },
                        onOptions = { dialog = PlayerDialog.OPTIONS },
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.weight(1f))
                        .background(Color.Black)
                ) {
                    if (source.isNullOrBlank()) {
                        Text("No playable stream URL was returned by the server.", color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                }
                            },
                            update = {
                                it.player = player
                                it.resizeMode = if (fitVideo) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { scaleX = zoom; scaleY = zoom }
                        )

                        PlayerGestures(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { viewSize = it },
                            locked = controlsLocked,
                            durationMs = durationMs,
                            player = player,
                            onTap = { if (!controlsLocked) controlsVisible = !controlsVisible },
                            onDoubleTap = { offset ->
                                if (player != null && !controlsLocked) {
                                    val delta = when {
                                        offset.x < viewSize.width * .4f -> -10_000L
                                        offset.x > viewSize.width * .6f -> 10_000L
                                        else -> 0L
                                    }
                                    if (delta == 0L) {
                                        if (player.isPlaying) player.pause() else player.play()
                                    } else {
                                        player.seekTo((player.currentPosition + delta).coerceIn(0L, player.duration.coerceAtLeast(0L)))
                                        seekFeedback = if (delta < 0) "−10 seconds" else "+10 seconds"
                                        scope.launch { delay(800); seekFeedback = null }
                                    }
                                }
                            },
                            onHorizontalDragStart = { controlsVisible = true },
                            onHorizontalDrag = { change, startX, startPosition ->
                                if (player != null && durationMs > 0L && viewSize.width > 0) {
                                    change.consume()
                                    val fraction = (change.position.x - startX) / viewSize.width.toFloat()
                                    val target = (startPosition + durationMs * fraction).toLong().coerceIn(0L, durationMs)
                                    player.seekTo(target)
                                    positionMs = target
                                    gestureText = formatDurationMs(target)
                                }
                            },
                            onHorizontalDragEnd = { scope.launch { delay(700); gestureText = null } },
                            onVerticalDragStart = { x -> verticalGesture = if (x < viewSize.width / 2f) VerticalGesture.BRIGHTNESS else VerticalGesture.VOLUME },
                            onVerticalDrag = { amount ->
                                verticalGesture?.let { side ->
                                    val delta = -amount / viewSize.height.coerceAtLeast(1).toFloat()
                                    verticalValue = when (side) {
                                        VerticalGesture.BRIGHTNESS -> updateBrightness(activity, delta)
                                        VerticalGesture.VOLUME -> updateVolume(context, delta)
                                    }
                                }
                            },
                            onVerticalDragEnd = {
                                scope.launch { delay(800); verticalGesture = null }
                            },
                            onZoom = { zoomChange -> zoom = (zoom * zoomChange).coerceIn(1f, 3f) },
                        )

                        if (controlsLocked) {
                            IconButton(
                                onClick = { controlsLocked = false; controlsVisible = true },
                                modifier = Modifier.align(Alignment.TopStart).padding(14.dp).clip(CircleShape).background(Color.Black.copy(.58f))
                            ) { Icon(Icons.Rounded.LockOpen, "Unlock controls", tint = Color.White) }
                        }
                        if (controlsVisible && !controlsLocked) {
                            PlayerChromeOverlay(
                                isPlaying = isPlaying,
                                positionMs = positionMs,
                                durationMs = durationMs,
                                zoom = zoom,
                                fullscreen = fullscreen,
                                fitVideo = fitVideo,
                                onPlayPause = { if (player?.isPlaying == true) player.pause() else player?.play() },
                                onSeek = { player?.seekTo(it); positionMs = it },
                                onSkip = { seconds -> player?.seekTo((player?.currentPosition ?: 0L) + seconds * 1000L) },
                                onLock = { controlsLocked = true; controlsVisible = false },
                                onFit = { fitVideo = !fitVideo; zoom = 1f },
                                onOptions = { dialog = PlayerDialog.OPTIONS },
                                onPictureInPicture = { enterPip(activity) },
                                onFullscreen = { fullscreen = !fullscreen; setFullscreen(activity, fullscreen) },
                            )
                        }
                        AnimatedVisibility(visible = isBuffering, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
                            CircularBuffering()
                        }
                        seekFeedback?.let { SeekFeedback(it, modifier = Modifier.align(Alignment.Center)) }
                        gestureText?.let { SeekFeedback(it, modifier = Modifier.align(Alignment.Center)) }
                        verticalGesture?.let { side ->
                            VerticalGestureFeedback(side, verticalValue, Modifier.align(Alignment.Center))
                        }
                        errorText?.let { error ->
                            PlaybackErrorOverlay(error, onRetry = {
                                errorText = null
                                player?.prepare()
                                if (autoplay) player?.play()
                            }, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        PlayerDialog.OPTIONS -> OptionsPanel(
            onDismiss = { dialog = PlayerDialog.NONE },
            onAudio = { dialog = PlayerDialog.AUDIO },
            onSubtitle = { dialog = PlayerDialog.SUBTITLE },
            onSpeed = { dialog = PlayerDialog.SPEED },
            onPip = { dialog = PlayerDialog.NONE; enterPip(activity) },
            onFullscreen = { dialog = PlayerDialog.NONE; fullscreen = !fullscreen; setFullscreen(activity, fullscreen) },
            onFit = { dialog = PlayerDialog.NONE; fitVideo = !fitVideo; zoom = 1f },
        )
        PlayerDialog.AUDIO -> TrackDialog("Select audio track", player, C.TRACK_TYPE_AUDIO) { dialog = PlayerDialog.OPTIONS }
        PlayerDialog.SUBTITLE -> SubtitleDialog(
            player = player,
            subtitleDelayMs = subtitleDelayMs,
            onDismiss = { dialog = PlayerDialog.OPTIONS },
            onOpenLocal = { dialog = PlayerDialog.NONE; onOpenSubtitle() },
            onSetDelay = { value -> scope.launch { onSetSubtitleDelay(value) } },
        )
        PlayerDialog.SPEED -> SpeedDialog(player, { dialog = PlayerDialog.OPTIONS }) { scope.launch { onSetSpeed(it) } }
        PlayerDialog.NONE -> Unit
    }
}

@Composable
private fun PlayerChromeTop(
    title: String,
    controlsVisible: Boolean,
    onBack: () -> Unit,
    onAudio: () -> Unit,
    onSubtitle: () -> Unit,
    onSpeed: () -> Unit,
    onOptions: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xF2080B0F)).padding(WindowInsets.systemBars.asPaddingValues()).padding(horizontal = 4.dp).padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
        Text(title, color = Color.White, maxLines = 2, modifier = Modifier.weight(1f), fontSize = 16.sp)
        Text("AUTO", color = Cyan, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
        if (controlsVisible) {
            IconButton(onClick = onSpeed) { Icon(Icons.Rounded.Speed, "Playback speed", tint = Color.White) }
            IconButton(onClick = onAudio) { Icon(Icons.Rounded.Audiotrack, "Audio track", tint = Color.White) }
            IconButton(onClick = onSubtitle) { Icon(Icons.Rounded.Subtitles, "Subtitle track", tint = Color.White) }
            IconButton(onClick = onOptions) { Icon(Icons.Rounded.MoreVert, "More options", tint = Color.White) }
        }
    }
}

@Composable
private fun PlayerChromeOverlay(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    zoom: Float,
    fullscreen: Boolean,
    fitVideo: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onLock: () -> Unit,
    onFit: () -> Unit,
    onOptions: () -> Unit,
    onPictureInPicture: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 52.dp).fillMaxHeight(.26f).background(Brush.verticalGradient(listOf(Color.Black.copy(.5f), Color.Transparent))))
        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).fillMaxHeight(.36f).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.92f)))))
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            IconButton(onClick = { onSkip(-10) }, Modifier.size(52.dp)) { Icon(Icons.Rounded.Replay10, "Back 10 seconds", tint = Color.White, Modifier.size(36.dp)) }
            IconButton(onClick = onPlayPause, Modifier.size(72.dp).clip(CircleShape).background(Cyan)) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Play", tint = Color(0xFF001F24), Modifier.size(44.dp))
            }
            IconButton(onClick = { onSkip(10) }, Modifier.size(52.dp)) { Icon(Icons.Rounded.Replay10, "Forward 10 seconds", tint = Color.White, Modifier.size(36.dp).graphicsLayer { scaleX = -1f }) }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDurationMs(positionMs), color = Color.White, fontSize = 12.sp)
                Slider(value = fraction, onValueChange = { onSeek((it * durationMs).toLong()) }, enabled = durationMs > 0, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                Text(formatDurationMs(durationMs), color = Color.White, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onLock) { Icon(Icons.Rounded.Lock, "Lock controls", tint = Color.White) }
                IconButton(onClick = onFit) { Icon(Icons.Rounded.Fullscreen, if (fitVideo) "Zoom video" else "Fit video", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOptions) { Icon(Icons.Rounded.Settings, null, tint = Color.White); Spacer(Modifier.width(4.dp)); Text("Options", color = Color.White) }
                IconButton(onClick = onPictureInPicture) { Icon(Icons.Rounded.PictureInPictureAlt, "Picture in picture", tint = Color.White) }
                IconButton(onClick = onFullscreen) { Icon(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, "Fullscreen", tint = Color.White) }
            }
        }
    }
}

@Composable
private fun PlayerGestures(
    modifier: Modifier,
    locked: Boolean,
    durationMs: Long,
    player: Player?,
    onTap: () -> Unit,
    onDoubleTap: (androidx.compose.ui.geometry.Offset) -> Unit,
    onHorizontalDragStart: () -> Unit,
    onHorizontalDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Float, Long) -> Unit,
    onHorizontalDragEnd: () -> Unit,
    onVerticalDragStart: (Float) -> Unit,
    onVerticalDrag: (Float) -> Unit,
    onVerticalDragEnd: () -> Unit,
    onZoom: (Float) -> Unit,
) {
    var startX = 0f
    var startPosition = 0L
    Box(
        modifier = modifier
            .pointerInput(locked) {
                if (!locked) detectTapGestures(onTap = { onTap() }, onDoubleTap = onDoubleTap)
            }
            .pointerInput(locked, durationMs) {
                if (!locked) detectHorizontalDragGestures(
                    onDragStart = { offset -> startX = offset.x; startPosition = player?.currentPosition ?: 0L; onHorizontalDragStart() },
                    onHorizontalDrag = { change, _ -> onHorizontalDrag(change, startX, startPosition) },
                    onDragEnd = onHorizontalDragEnd,
                )
            }
            .pointerInput(locked) {
                if (!locked) detectVerticalDragGestures(
                    onDragStart = { onVerticalDragStart(it.x) },
                    onVerticalDrag = { change, amount -> change.consume(); onVerticalDrag(amount) },
                    onDragEnd = onVerticalDragEnd,
                )
            }
            .pointerInput(locked) {
                if (!locked) detectTransformGestures { _, _, zoomChange, _ -> onZoom(zoomChange) }
            }
    )
}

@Composable
private fun CircularBuffering() {
    androidx.compose.material3.CircularProgressIndicator(color = Cyan, modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
}

@Composable
private fun SeekFeedback(text: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.Black.copy(.72f)), shape = RoundedCornerShape(12.dp)) {
        Text(text, color = Color.White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp), fontSize = 14.sp)
    }
}

@Composable
private fun VerticalGestureFeedback(side: VerticalGesture, value: Float, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(if (side == VerticalGesture.BRIGHTNESS) Icons.Rounded.BrightnessHigh else Icons.Rounded.VolumeUp, null, tint = Color.White, modifier = Modifier.size(26.dp))
        Text("${(value * 100).roundToInt()}%", color = Color.White, fontSize = 13.sp)
        Box(Modifier.size(width = 5.dp, height = 120.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(.22f))) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(.02f, 1f)).align(Alignment.BottomCenter).background(Cyan))
        }
    }
}

@Composable
private fun PlaybackErrorOverlay(error: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.padding(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xF20D1117)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Playback error", color = Color.White, fontSize = 18.sp)
            Text(error, color = VaultMuted, maxLines = 3)
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun OptionsPanel(
    onDismiss: () -> Unit,
    onAudio: () -> Unit,
    onSubtitle: () -> Unit,
    onSpeed: () -> Unit,
    onPip: () -> Unit,
    onFullscreen: () -> Unit,
    onFit: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(vertical = 8.dp, horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
            Surface(Modifier.fillMaxWidth(.9f).fillMaxHeight(.92f), shape = RoundedCornerShape(22.dp), color = Color(0xFF10141C)) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Player options", color = Color.White, fontSize = 24.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
                    }
                    Text("Playback", color = Cyan, fontSize = 11.sp)
                    HorizontalDivider(color = VaultBorder)
                    OptionRow(Icons.Rounded.Audiotrack, "Audio track", onAudio)
                    OptionRow(Icons.Rounded.Subtitles, "Subtitle track", onSubtitle)
                    OptionRow(Icons.Rounded.Speed, "Playback speed", onSpeed)
                    OptionRow(Icons.Rounded.PictureInPictureAlt, "Picture-in-picture", onPip)
                    OptionRow(Icons.Rounded.Fullscreen, "Fullscreen", onFullscreen)
                    OptionRow(Icons.Rounded.Fullscreen, "Fit / zoom video", onFit)
                    Spacer(Modifier.weight(1f))
                    Text("NextPlayer-style controls", color = VaultMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun OptionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = Cyan)
        Spacer(Modifier.width(14.dp))
        Text(title, color = Color.White, modifier = Modifier.weight(1f))
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
                        }, Modifier.fillMaxWidth()) {
                            Text(label, modifier = Modifier.weight(1f))
                            if (group.isTrackSelected(index)) Text("✓", color = Cyan)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun SubtitleDialog(
    player: Player?,
    subtitleDelayMs: Long,
    onDismiss: () -> Unit,
    onOpenLocal: () -> Unit,
    onSetDelay: (Long) -> Unit,
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
                }, Modifier.fillMaxWidth()) { Text("◉  Disable subtitles", modifier = Modifier.weight(1f)) }
                Button(onClick = onOpenLocal, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Open local subtitle") }
                groups.forEach { group ->
                    for (index in 0 until group.length) {
                        val format = group.getTrackFormat(index)
                        TextButton(onClick = {
                            player?.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
                                .build()
                            onDismiss()
                        }, Modifier.fillMaxWidth()) {
                            Text(format.language?.uppercase() ?: format.label ?: "Subtitle ${index + 1}", modifier = Modifier.weight(1f))
                            if (group.isTrackSelected(index)) Text("✓", color = Cyan)
                        }
                    }
                }
                Text("Subtitle delay", color = VaultMuted, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { onSetDelay(subtitleDelayMs - 250L) }) { Text("−") }
                    Text("${subtitleDelayMs / 1000.0}s", color = Cyan, fontSize = 17.sp)
                    OutlinedButton(onClick = { onSetDelay(subtitleDelayMs + 250L) }) { Text("+") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
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
                            OutlinedButton(onClick = { player?.setPlaybackParameters(PlaybackParameters(speed)); onSpeedSelected(speed); onDismiss() }, Modifier.weight(1f)) {
                                Text(speed.toString().removeSuffix(".0"))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
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
    val window = activity?.window ?: return .5f
    val current = window.attributes.screenBrightness.takeIf { it > 0f } ?: .5f
    val next = (current + delta).coerceIn(.05f, 1f)
    window.attributes = window.attributes.apply { screenBrightness = next }
    return next
}

private fun updateVolume(context: Context, delta: Float): Float {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val next = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + delta * max).roundToInt().coerceIn(0, max)
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
    activity.setPictureInPictureParams(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
    activity.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
}

