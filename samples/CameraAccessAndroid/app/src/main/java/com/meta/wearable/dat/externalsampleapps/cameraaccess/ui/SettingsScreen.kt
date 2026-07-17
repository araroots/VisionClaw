package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.AIProvider
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var aiProvider by remember { mutableStateOf(SettingsManager.aiProvider) }
    var geminiAPIKey by remember { mutableStateOf(SettingsManager.geminiAPIKey) }
    var openaiAPIKey by remember { mutableStateOf(SettingsManager.openaiAPIKey) }
    var systemPrompt by remember { mutableStateOf(SettingsManager.geminiSystemPrompt) }
    var openClawHost by remember { mutableStateOf(SettingsManager.openClawHost) }
    var openClawPort by remember { mutableStateOf(SettingsManager.openClawPort.toString()) }
    var openClawHookToken by remember { mutableStateOf(SettingsManager.openClawHookToken) }
    var openClawGatewayToken by remember { mutableStateOf(SettingsManager.openClawGatewayToken) }
    var webrtcSignalingURL by remember { mutableStateOf(SettingsManager.webrtcSignalingURL) }
    var videoStreamingEnabled by remember { mutableStateOf(SettingsManager.videoStreamingEnabled) }
    var proactiveNotificationsEnabled by remember { mutableStateOf(SettingsManager.proactiveNotificationsEnabled) }
    var wakeWordEnabled by remember { mutableStateOf(SettingsManager.wakeWordEnabled) }
    var wakePhrase by remember { mutableStateOf(SettingsManager.wakePhrase) }
    var openClawWakeWordEnabled by remember { mutableStateOf(SettingsManager.openClawWakeWordEnabled) }
    var openClawWakePhrase by remember { mutableStateOf(SettingsManager.openClawWakePhrase) }
    var continuousConversationEnabled by remember { mutableStateOf(SettingsManager.continuousConversationEnabled) }
    var videoQuality by remember { mutableStateOf(SettingsManager.videoQuality) }
    var videoFrameRate by remember { mutableStateOf(SettingsManager.videoFrameRate.toString()) }
    var imageBrightness by remember { mutableStateOf(SettingsManager.imageBrightness) }
    var imageContrast by remember { mutableStateOf(SettingsManager.imageContrast) }
    var imageSaturation by remember { mutableStateOf(SettingsManager.imageSaturation) }
    var showResetDialog by remember { mutableStateOf(false) }

    fun save() {
        SettingsManager.aiProvider = aiProvider
        SettingsManager.geminiAPIKey = geminiAPIKey.trim()
        SettingsManager.openaiAPIKey = openaiAPIKey.trim()
        SettingsManager.geminiSystemPrompt = systemPrompt.trim()
        SettingsManager.openClawHost = openClawHost.trim()
        openClawPort.trim().toIntOrNull()?.let { SettingsManager.openClawPort = it }
        SettingsManager.openClawHookToken = openClawHookToken.trim()
        SettingsManager.openClawGatewayToken = openClawGatewayToken.trim()
        SettingsManager.webrtcSignalingURL = webrtcSignalingURL.trim()
        SettingsManager.videoStreamingEnabled = videoStreamingEnabled
        SettingsManager.proactiveNotificationsEnabled = proactiveNotificationsEnabled
        SettingsManager.wakeWordEnabled = wakeWordEnabled
        SettingsManager.wakePhrase = wakePhrase.trim().ifEmpty { SettingsManager.DEFAULT_WAKE_PHRASE }
        SettingsManager.openClawWakeWordEnabled = openClawWakeWordEnabled
        SettingsManager.openClawWakePhrase = openClawWakePhrase.trim().ifEmpty { SettingsManager.DEFAULT_OPENCLAW_WAKE_PHRASE }
        SettingsManager.continuousConversationEnabled = continuousConversationEnabled
        SettingsManager.videoQuality = videoQuality
        videoFrameRate.trim().toIntOrNull()?.let { SettingsManager.videoFrameRate = it }
        SettingsManager.imageBrightness = imageBrightness
        SettingsManager.imageContrast = imageContrast
        SettingsManager.imageSaturation = imageSaturation
    }

    fun reload() {
        aiProvider = SettingsManager.aiProvider
        geminiAPIKey = SettingsManager.geminiAPIKey
        openaiAPIKey = SettingsManager.openaiAPIKey
        systemPrompt = SettingsManager.geminiSystemPrompt
        openClawHost = SettingsManager.openClawHost
        openClawPort = SettingsManager.openClawPort.toString()
        openClawHookToken = SettingsManager.openClawHookToken
        openClawGatewayToken = SettingsManager.openClawGatewayToken
        webrtcSignalingURL = SettingsManager.webrtcSignalingURL
        videoStreamingEnabled = SettingsManager.videoStreamingEnabled
        proactiveNotificationsEnabled = SettingsManager.proactiveNotificationsEnabled
        wakeWordEnabled = SettingsManager.wakeWordEnabled
        wakePhrase = SettingsManager.wakePhrase
        openClawWakeWordEnabled = SettingsManager.openClawWakeWordEnabled
        openClawWakePhrase = SettingsManager.openClawWakePhrase
        continuousConversationEnabled = SettingsManager.continuousConversationEnabled
        videoQuality = SettingsManager.videoQuality
        videoFrameRate = SettingsManager.videoFrameRate.toString()
        imageBrightness = SettingsManager.imageBrightness
        imageContrast = SettingsManager.imageContrast
        imageSaturation = SettingsManager.imageSaturation
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = {
                    save()
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // AI provider section
            SectionHeader("AI Provider")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AIProvider.entries.forEach { provider ->
                    val selected = aiProvider == provider
                    Button(
                        onClick = { aiProvider = provider },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text(if (provider == AIProvider.GEMINI) "Gemini" else "ChatGPT")
                    }
                }
            }

            if (aiProvider == AIProvider.GEMINI) {
                SectionHeader("Gemini API")
                MonoTextField(
                    value = geminiAPIKey,
                    onValueChange = { geminiAPIKey = it },
                    label = "API Key",
                    placeholder = "Enter Gemini API key",
                )
            } else {
                SectionHeader("OpenAI API")
                MonoTextField(
                    value = openaiAPIKey,
                    onValueChange = { openaiAPIKey = it },
                    label = "API Key",
                    placeholder = "Enter OpenAI API key",
                )
            }

            SectionHeader("System Prompt")
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("System prompt") },
                modifier = Modifier.fillMaxWidth().height(200.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )

            // OpenClaw section
            SectionHeader("OpenClaw")
            MonoTextField(
                value = openClawHost,
                onValueChange = { openClawHost = it },
                label = "Host",
                placeholder = "http://your-mac.local",
                keyboardType = KeyboardType.Uri,
            )
            MonoTextField(
                value = openClawPort,
                onValueChange = { openClawPort = it },
                label = "Port",
                placeholder = "18789",
                keyboardType = KeyboardType.Number,
            )
            MonoTextField(
                value = openClawHookToken,
                onValueChange = { openClawHookToken = it },
                label = "Hook Token",
                placeholder = "Hook token",
            )
            MonoTextField(
                value = openClawGatewayToken,
                onValueChange = { openClawGatewayToken = it },
                label = "Gateway Token",
                placeholder = "Gateway auth token",
            )

            // WebRTC section
            SectionHeader("WebRTC")
            MonoTextField(
                value = webrtcSignalingURL,
                onValueChange = { webrtcSignalingURL = it },
                label = "Signaling URL",
                placeholder = "wss://your-server.example.com",
                keyboardType = KeyboardType.Uri,
            )

            // Video
            SectionHeader("Video")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column {
                    Text("Video Streaming", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Disable to save battery. Audio remains active.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = videoStreamingEnabled,
                    onCheckedChange = { videoStreamingEnabled = it },
                )
            }

            // Notifications
            SectionHeader("Notifications")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column {
                    Text("Proactive Notifications", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Receive updates from OpenClaw spoken through glasses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = proactiveNotificationsEnabled,
                    onCheckedChange = { proactiveNotificationsEnabled = it },
                )
            }

            // Wake Word
            SectionHeader("Wake Word")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column {
                    Text("Wake Word", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Say the phrase below to start the AI hands-free (only while this screen is open).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = wakeWordEnabled,
                    onCheckedChange = { wakeWordEnabled = it },
                )
            }
            if (wakeWordEnabled) {
                MonoTextField(
                    value = wakePhrase,
                    onValueChange = { wakePhrase = it },
                    label = "Wake Phrase",
                    placeholder = SettingsManager.DEFAULT_WAKE_PHRASE,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Continuous Conversation", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Keep listening for a few seconds after each reply, without repeating the wake phrase.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = continuousConversationEnabled,
                        onCheckedChange = { continuousConversationEnabled = it },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column {
                    Text("OpenClaw Wake Word", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Say the phrase below to turn OpenClaw on hands-free, separate from the AI wake word.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = openClawWakeWordEnabled,
                    onCheckedChange = { openClawWakeWordEnabled = it },
                )
            }
            if (openClawWakeWordEnabled) {
                MonoTextField(
                    value = openClawWakePhrase,
                    onValueChange = { openClawWakePhrase = it },
                    label = "OpenClaw Wake Phrase",
                    placeholder = SettingsManager.DEFAULT_OPENCLAW_WAKE_PHRASE,
                )
            }

            // Video Quality
            SectionHeader("Video Quality")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VideoQuality.entries.forEach { quality ->
                    val selected = videoQuality == quality
                    Button(
                        onClick = { videoQuality = quality },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text(quality.name)
                    }
                }
            }
            MonoTextField(
                value = videoFrameRate,
                onValueChange = { videoFrameRate = it },
                label = "Frame Rate (fps)",
                placeholder = SettingsManager.DEFAULT_FRAME_RATE.toString(),
                keyboardType = KeyboardType.Number,
            )

            // Image adjustments (applied to the live preview only)
            SectionHeader("Image")
            LabeledSlider(
                label = "Brightness",
                value = imageBrightness,
                onValueChange = { imageBrightness = it },
                valueRange = -1f..1f,
            )
            LabeledSlider(
                label = "Contrast",
                value = imageContrast,
                onValueChange = { imageContrast = it },
                valueRange = 0.5f..2f,
            )
            LabeledSlider(
                label = "Saturation",
                value = imageSaturation,
                onValueChange = { imageSaturation = it },
                valueRange = 0f..2f,
            )

            // Reset
            TextButton(onClick = { showResetDialog = true }) {
                Text("Reset to Defaults", color = Color.Red)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Settings") },
            text = { Text("This will reset all settings to the values built into the app.") },
            confirmButton = {
                TextButton(onClick = {
                    SettingsManager.resetAll()
                    reload()
                    showResetDialog = false
                }) {
                    Text("Reset", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun MonoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                String.format("%.2f", value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}
