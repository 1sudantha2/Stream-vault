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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamvault.player.data.AppSettings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSaveServer: suspend (String) -> Unit,
    onSetKeepScreenOn: suspend (Boolean) -> Unit,
    onSetHardware: suspend (Boolean) -> Unit,
    onSetAutoplay: suspend (Boolean) -> Unit
) {
    BackHandler(onBack = onBack)
    var serverUrl by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080B0F)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xEB080B0F)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                LogoMark(Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text("Settings", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingSection(title = "SERVER", icon = { Icon(Icons.Rounded.Cloud, null, tint = Cyan) }) {
                    Text("Backend URL", color = VaultMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it; saved = false },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("https://your-server.example.com") },
                        supportingText = { Text("The app calls /api/videos and uses the returned stream path.") }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {
                            scope.launch { onSaveServer(serverUrl); saved = true }
                        }) { Text("Save server") }
                        if (saved) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                            Text("Saved", color = Color(0xFF10B981), fontSize = 13.sp)
                        }
                    }
                }

                SettingSection(title = "PLAYBACK", icon = { Icon(Icons.Rounded.PlayCircle, null, tint = Cyan) }) {
                    SettingToggle(
                        title = "Autoplay videos",
                        description = "Start playback as soon as the stream is ready.",
                        checked = settings.autoplay,
                        onCheckedChange = { scope.launch { onSetAutoplay(it) } }
                    )
                    HorizontalDivider(color = VaultBorder)
                    SettingToggle(
                        title = "Keep screen on",
                        description = "Prevent sleep while the app is open.",
                        checked = settings.keepScreenOn,
                        onCheckedChange = { scope.launch { onSetKeepScreenOn(it) } }
                    )
                }

                SettingSection(title = "DECODER", icon = { Icon(Icons.Rounded.Memory, null, tint = Cyan) }) {
                    SettingToggle(
                        title = "Prefer device hardware decoder",
                        description = "Hardware-first rendering reduces CPU, battery and RAM use. Media3 falls back when a device cannot decode the format.",
                        checked = settings.preferHardwareDecoding,
                        onCheckedChange = { scope.launch { onSetHardware(it) } }
                    )
                    Text("Media3 / ExoPlayer • H.264 • H.265 / HEVC • VP9 • AV1 (device dependent)", color = VaultMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 16.sp)
                }

                SettingSection(title = "ABOUT", icon = { Icon(Icons.Rounded.Settings, null, tint = Cyan) }) {
                    Text("Stream-Vault 1.0.0", fontWeight = FontWeight.Bold)
                    Text("Native Kotlin + Jetpack Compose + Android Media3. No ads. No storage permission: local media is selected through Android's secure document picker.", color = VaultMuted, lineHeight = 20.sp)
                    Text("Supports Android phones, tablets and Android TV layouts.", color = VaultMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun SettingSection(title: String, icon: @Composable () -> Unit, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                icon()
                Text(title, color = Cyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
            }
            content()
        }
    }
}

@Composable
private fun SettingToggle(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = VaultMuted, lineHeight = 18.sp, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
