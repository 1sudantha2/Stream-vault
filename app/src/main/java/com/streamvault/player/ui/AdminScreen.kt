package com.streamvault.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamvault.player.data.AdminClient
import com.streamvault.player.data.DownloadJob
import com.streamvault.player.util.formatBytes

@Composable
fun AdminScreen(serverUrl: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val jobs = remember { mutableStateMapOf<String, DownloadJob>() }
    val client = remember(serverUrl) { AdminClient(serverUrl) }

    DisposableEffect(client) {
        if (serverUrl.isNotBlank()) {
            client.connect(
                onUpdate = { job, done ->
                    if (job.id.isNotBlank()) jobs[job.id] = job
                    if (done) message = "Job finished: ${job.title}"
                },
                onError = { message = it }
            )
        } else message = "Set a backend URL in Settings first."
        onDispose { client.close() }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF080B0F)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xEB080B0F)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                LogoMark(Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dashboard", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Text("JOB CONTROL", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                Icon(Icons.Rounded.Cloud, "Live updates", tint = Color(0xFF10B981))
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AddJobCard(
                        url = url,
                        title = title,
                        submitting = submitting,
                        onUrlChange = { url = it; message = null },
                        onTitleChange = { title = it },
                        onSubmit = {
                            if (url.isNotBlank() && !submitting) {
                                submitting = true
                                message = null
                                client.addJob(url.trim(), title.trim(),
                                    onSuccess = {
                                        submitting = false
                                        url = ""
                                        title = ""
                                        message = "Job added. Live progress will appear below."
                                    },
                                    onError = { submitting = false; message = it }
                                )
                            }
                        }
                    )
                }
                message?.let { text ->
                    item { Text(text, color = if (text.contains("fail", true) || text.contains("error", true)) Color(0xFFFF8A80) else Cyan, fontSize = 12.sp) }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("ACTIVE JOBS", color = VaultMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.weight(1f))
                        Text("${jobs.size} jobs", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
                if (jobs.isEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = VaultSurface), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.CloudOff, null, tint = VaultMuted, modifier = Modifier.size(36.dp))
                                Text("No active jobs", fontWeight = FontWeight.Bold)
                                Text("Paste a direct link above to start.", color = VaultMuted, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    jobs.values.sortedByDescending { it.id }.forEach { job ->
                        item(key = job.id) {
                            JobCard(job, onCancel = { id ->
                                client.cancelJob(id, onSuccess = { message = "Cancellation requested." }, onError = { message = it })
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddJobCard(
    url: String,
    title: String,
    submitting: Boolean,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = VaultSurface), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("NEW DOWNLOAD JOB", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            OutlinedTextField(url, onUrlChange, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Direct URL") }, placeholder = { Text("https://example.com/video.mp4") })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, onTitleChange, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Title (optional)") })
                Button(onClick = onSubmit, enabled = url.isNotBlank() && !submitting) {
                    if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add job")
                }
            }
        }
    }
}

@Composable
private fun JobCard(job: DownloadJob, onCancel: (String) -> Unit) {
    val active = job.status in setOf("pending", "downloading", "transcoding", "thumbnailing")
    val download = (job.downloadPercent / 100f).coerceIn(0f, 1f)
    val transcode = (job.transcodePercent / 100f).coerceIn(0f, 1f)
    Card(colors = CardDefaults.cardColors(containerColor = VaultSurface), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(job.title.ifBlank { job.id }, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(job.url, color = VaultMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(job.status.uppercase(), color = statusColor(job.status), fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                if (active) IconButton(onClick = { onCancel(job.id) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Rounded.StopCircle, "Cancel job", tint = Color(0xFFFF8A80)) }
            }
            ProgressLine("DOWNLOAD", job.downloadPercent, download, Cyan)
            ProgressLine("TRANSCODE", job.transcodePercent, transcode, Purple)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${formatBytes(job.downloadedBytes)} / ${formatBytes(job.totalBytes)}", color = VaultMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                if (job.downloadSpeed > 0) Text("${formatBytes(job.downloadSpeed)}/s", color = VaultMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                if (job.etaSeconds > 0) Text("ETA ${formatEta(job.etaSeconds)}", color = VaultMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            job.error?.let { Text(it, color = Color(0xFFFF8A80), fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
        }
    }
}

@Composable
private fun ProgressLine(label: String, percent: Float, progress: Float, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = VaultMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp, modifier = Modifier.width(70.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).height(5.dp), color = color, trackColor = VaultSurface2)
        Text("${percent.coerceIn(0f, 100f).toInt()}%", color = color, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.width(42.dp).padding(start = 6.dp))
    }
}

private fun statusColor(status: String): Color = when (status) {
    "done" -> Color(0xFF10B981)
    "failed" -> Color(0xFFFF8A80)
    "transcoding" -> Purple
    "downloading" -> Cyan
    else -> VaultMuted
}

private fun formatEta(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds}s"
}
