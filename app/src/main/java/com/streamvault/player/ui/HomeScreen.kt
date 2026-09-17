package com.streamvault.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamvault.player.data.ApiClient
import com.streamvault.player.data.VideoItem
import com.streamvault.player.util.formatBytes
import com.streamvault.player.util.formatDate
import com.streamvault.player.util.formatDuration
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    serverUrl: String,
    onOpenSettings: () -> Unit,
    onPlayVideo: (VideoItem) -> Unit,
    onPlayUrl: (String, String) -> Unit,
    onOpenLocalVideo: () -> Unit
) {
    var videos by remember(serverUrl) { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember(serverUrl) { mutableStateOf(false) }
    var error by remember(serverUrl) { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var showUrlDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        if (serverUrl.isBlank()) {
            videos = emptyList()
            error = null
            return
        }
        scope.launch {
            isLoading = true
            error = null
            runCatching { ApiClient.videos(serverUrl) }
                .onSuccess { videos = it }
                .onFailure { error = it.message ?: "Could not load videos" }
            isLoading = false
        }
    }

    LaunchedEffect(serverUrl) { refresh() }

    val filtered = remember(videos, query) {
        if (query.isBlank()) videos
        else videos.filter { it.title.contains(query, ignoreCase = true) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080B0F)) {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeTopBar(
                query = query,
                onQueryChange = { query = it },
                onOpenSettings = onOpenSettings,
                onRefresh = ::refresh,
                onOpenUrl = { showUrlDialog = true },
                onOpenLocal = onOpenLocalVideo
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(6.dp))
                if (serverUrl.isBlank()) {
                    ConnectServerCard(onOpenSettings)
                } else {
                    StatsRow(videos)
                    if (error != null) {
                        ErrorCard(error ?: "", onRetry = ::refresh)
                    }
                    when {
                        isLoading && videos.isEmpty() -> LoadingState()
                        !isLoading && filtered.isEmpty() -> EmptyState(query, onOpenSettings)
                        else -> VideoGrid(filtered, onPlayVideo)
                    }
                }
            }
        }
    }

    if (showUrlDialog) {
        PlayUrlDialog(
            onDismiss = { showUrlDialog = false },
            onPlay = { url, title ->
                showUrlDialog = false
                onPlayUrl(url, title)
            }
        )
    }
}

@Composable
private fun HomeTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onOpenUrl: () -> Unit,
    onOpenLocal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xEB080B0F))
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark(Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Stream-Vault", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text("MEDIA LIBRARY", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.sp)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Refresh") }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Settings") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = VaultMuted) },
                placeholder = { Text("Search videos…", color = VaultMuted) },
                shape = RoundedCornerShape(10.dp)
            )
            IconButton(onClick = onOpenUrl, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Link, "Play URL", tint = Cyan)
            }
            IconButton(onClick = onOpenLocal, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.FolderOpen, "Open local video", tint = Cyan)
            }
        }
    }
}

@Composable
fun LogoMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.linearGradient(listOf(Cyan, Purple))),
        contentAlignment = Alignment.Center
    ) {
        Text("▶", color = Color(0xFF080B0F), fontSize = 17.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ConnectServerCard(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.CloudOff, null, tint = Cyan, modifier = Modifier.size(30.dp))
            Text("Connect your Stream-Vault server", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Set the backend URL in Settings to load your gallery. The app talks to the same /api/videos endpoints as the web templates.", color = VaultMuted, lineHeight = 20.sp)
            Button(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, null); Spacer(Modifier.width(8.dp)); Text("Open settings") }
        }
    }
}

@Composable
private fun StatsRow(videos: List<VideoItem>) {
    val totalSize = videos.sumOf { it.sizeBytes ?: 0L }
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(vertical = 2.dp)) {
        Stat(value = videos.size.toString(), label = "VIDEOS")
        Stat(value = formatBytes(totalSize).ifBlank { "0 B" }, label = "TOTAL SIZE")
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(value, color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(label, color = VaultMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun VideoGrid(videos: List<VideoItem>, onPlayVideo: (VideoItem) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 250.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(videos, key = { it.id }) { video -> VideoCard(video, onClick = { onPlayVideo(video) }) }
    }
}

@Composable
private fun VideoCard(video: VideoItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder)
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(148.dp).background(VaultSurface2),
                contentAlignment = Alignment.Center
            ) {
                if (!video.thumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = video.thumbnail,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Rounded.VideoLibrary, null, tint = Color(0xFF334155), modifier = Modifier.size(50.dp))
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = .78f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) { Text(formatDuration(video.durationSeconds), fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
            }
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(video.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDate(video.createdAt), color = VaultMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    Text(formatBytes(video.sizeBytes), color = VaultMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Cyan) }
}

@Composable
private fun EmptyState(query: String, onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (query.isBlank()) "No videos yet" else "No matching videos", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(if (query.isBlank()) "Add videos from the web Dashboard." else "Try a different search.", color = VaultMuted)
            if (query.isBlank()) TextButton(onClick = onOpenSettings) { Text("Server settings", color = Cyan) }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF28151A)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = Color(0xFFFFB4AB), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onRetry) { Text("Retry", color = Cyan) }
        }
    }
}

@Composable
private fun PlayUrlDialog(onDismiss: () -> Unit, onPlay: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Play from URL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Direct MP4, HLS or DASH links are supported by Media3.", color = VaultMuted)
                OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("Video URL") }, placeholder = { Text("https://example.com/video.mp4") })
                OutlinedTextField(title, { title = it }, singleLine = true, label = { Text("Title (optional)") })
            }
        },
        confirmButton = {
            Button(onClick = { if (url.isNotBlank()) onPlay(url.trim(), title.trim()) }, enabled = url.isNotBlank()) { Text("Play") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
