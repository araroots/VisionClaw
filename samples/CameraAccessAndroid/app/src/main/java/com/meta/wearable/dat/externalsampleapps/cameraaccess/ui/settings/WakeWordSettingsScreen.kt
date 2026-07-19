package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager

// Every spoken-phrase trigger in the app in one place: the AI wake word, the OpenClaw wake word,
// the AI stop phrase, and the camera/recording start-stop phrases.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeWordSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var wakeWordEnabled by remember { mutableStateOf(SettingsManager.wakeWordEnabled) }
    var wakePhrase by remember { mutableStateOf(SettingsManager.wakePhrase) }
    var openClawWakeWordEnabled by remember { mutableStateOf(SettingsManager.openClawWakeWordEnabled) }
    var openClawWakePhrase by remember { mutableStateOf(SettingsManager.openClawWakePhrase) }
    var aiStopPhraseEnabled by remember { mutableStateOf(SettingsManager.aiStopPhraseEnabled) }
    var aiStopPhrase by remember { mutableStateOf(SettingsManager.aiStopPhrase) }
    var cameraWakeWordEnabled by remember { mutableStateOf(SettingsManager.cameraWakeWordEnabled) }
    var cameraStartPhrase by remember { mutableStateOf(SettingsManager.cameraStartPhrase) }
    var cameraStopPhrase by remember { mutableStateOf(SettingsManager.cameraStopPhrase) }
    var recordingWakeWordEnabled by remember { mutableStateOf(SettingsManager.recordingWakeWordEnabled) }
    var recordingStartPhrase by remember { mutableStateOf(SettingsManager.recordingStartPhrase) }
    var recordingStopPhrase by remember { mutableStateOf(SettingsManager.recordingStopPhrase) }

    fun save() {
        SettingsManager.wakeWordEnabled = wakeWordEnabled
        SettingsManager.wakePhrase = wakePhrase.trim().ifEmpty { SettingsManager.DEFAULT_WAKE_PHRASE }
        SettingsManager.openClawWakeWordEnabled = openClawWakeWordEnabled
        SettingsManager.openClawWakePhrase = openClawWakePhrase.trim().ifEmpty { SettingsManager.DEFAULT_OPENCLAW_WAKE_PHRASE }
        SettingsManager.aiStopPhraseEnabled = aiStopPhraseEnabled
        SettingsManager.aiStopPhrase = aiStopPhrase.trim().ifEmpty { SettingsManager.DEFAULT_AI_STOP_PHRASE }
        SettingsManager.cameraWakeWordEnabled = cameraWakeWordEnabled
        SettingsManager.cameraStartPhrase = cameraStartPhrase.trim().ifEmpty { SettingsManager.DEFAULT_CAMERA_START_PHRASE }
        SettingsManager.cameraStopPhrase = cameraStopPhrase.trim().ifEmpty { SettingsManager.DEFAULT_CAMERA_STOP_PHRASE }
        SettingsManager.recordingWakeWordEnabled = recordingWakeWordEnabled
        SettingsManager.recordingStartPhrase = recordingStartPhrase.trim().ifEmpty { SettingsManager.DEFAULT_RECORDING_START_PHRASE }
        SettingsManager.recordingStopPhrase = recordingStopPhrase.trim().ifEmpty { SettingsManager.DEFAULT_RECORDING_STOP_PHRASE }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Wake Words & Voice Control") },
            navigationIcon = {
                IconButton(onClick = { save(); onBack() }) {
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
            SwitchRow(
                title = "Wake Word",
                description = "Say the phrase below to start the AI hands-free (only while this screen is open).",
                checked = wakeWordEnabled,
                onCheckedChange = { wakeWordEnabled = it },
            )
            if (wakeWordEnabled) {
                MonoTextField(
                    value = wakePhrase,
                    onValueChange = { wakePhrase = it },
                    label = "Wake Phrase",
                    placeholder = SettingsManager.DEFAULT_WAKE_PHRASE,
                )
            }

            SwitchRow(
                title = "OpenClaw Wake Word",
                description = "Say the phrase below to turn OpenClaw on hands-free, separate from the AI wake word.",
                checked = openClawWakeWordEnabled,
                onCheckedChange = { openClawWakeWordEnabled = it },
            )
            if (openClawWakeWordEnabled) {
                MonoTextField(
                    value = openClawWakePhrase,
                    onValueChange = { openClawWakePhrase = it },
                    label = "OpenClaw Wake Phrase",
                    placeholder = SettingsManager.DEFAULT_OPENCLAW_WAKE_PHRASE,
                )
            }

            SwitchRow(
                title = "AI Stop Phrase",
                description = "Say the phrase below during a conversation to end it hands-free -- useful with Continuous Conversation, which never closes on its own.",
                checked = aiStopPhraseEnabled,
                onCheckedChange = { aiStopPhraseEnabled = it },
            )
            if (aiStopPhraseEnabled) {
                MonoTextField(
                    value = aiStopPhrase,
                    onValueChange = { aiStopPhrase = it },
                    label = "Stop Phrase",
                    placeholder = SettingsManager.DEFAULT_AI_STOP_PHRASE,
                )
            }

            SwitchRow(
                title = "Camera Wake Word",
                description = "Say these phrases to start/stop the camera hands-free (only while the AI is off).",
                checked = cameraWakeWordEnabled,
                onCheckedChange = { cameraWakeWordEnabled = it },
            )
            if (cameraWakeWordEnabled) {
                MonoTextField(
                    value = cameraStartPhrase,
                    onValueChange = { cameraStartPhrase = it },
                    label = "Camera Start Phrase",
                    placeholder = SettingsManager.DEFAULT_CAMERA_START_PHRASE,
                )
                MonoTextField(
                    value = cameraStopPhrase,
                    onValueChange = { cameraStopPhrase = it },
                    label = "Camera Stop Phrase",
                    placeholder = SettingsManager.DEFAULT_CAMERA_STOP_PHRASE,
                )
            }

            SwitchRow(
                title = "Recording Wake Word",
                description = "Say these phrases to start/stop saving the camera stream to a video file (only while the AI is off).",
                checked = recordingWakeWordEnabled,
                onCheckedChange = { recordingWakeWordEnabled = it },
            )
            if (recordingWakeWordEnabled) {
                MonoTextField(
                    value = recordingStartPhrase,
                    onValueChange = { recordingStartPhrase = it },
                    label = "Recording Start Phrase",
                    placeholder = SettingsManager.DEFAULT_RECORDING_START_PHRASE,
                )
                MonoTextField(
                    value = recordingStopPhrase,
                    onValueChange = { recordingStopPhrase = it },
                    label = "Recording Stop Phrase",
                    placeholder = SettingsManager.DEFAULT_RECORDING_STOP_PHRASE,
                )
            }
        }
    }
}
