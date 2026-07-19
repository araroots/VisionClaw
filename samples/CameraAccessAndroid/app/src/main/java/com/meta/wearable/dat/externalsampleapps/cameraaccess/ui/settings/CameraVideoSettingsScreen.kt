package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.tr

// Streaming quality/frame rate, the WebRTC transport used to view the feed remotely, and live
// image adjustments.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraVideoSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var videoStreamingEnabled by remember { mutableStateOf(SettingsManager.videoStreamingEnabled) }
    var videoQuality by remember { mutableStateOf(SettingsManager.videoQuality) }
    var videoFrameRate by remember { mutableStateOf(SettingsManager.videoFrameRate.toString()) }
    var webrtcSignalingURL by remember { mutableStateOf(SettingsManager.webrtcSignalingURL) }
    var imageBrightness by remember { mutableStateOf(SettingsManager.imageBrightness) }
    var imageContrast by remember { mutableStateOf(SettingsManager.imageContrast) }
    var imageSaturation by remember { mutableStateOf(SettingsManager.imageSaturation) }

    fun save() {
        SettingsManager.videoStreamingEnabled = videoStreamingEnabled
        SettingsManager.videoQuality = videoQuality
        videoFrameRate.trim().toIntOrNull()?.let { SettingsManager.videoFrameRate = it }
        SettingsManager.webrtcSignalingURL = webrtcSignalingURL.trim()
        SettingsManager.imageBrightness = imageBrightness
        SettingsManager.imageContrast = imageContrast
        SettingsManager.imageSaturation = imageSaturation
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(tr("Câmera & Vídeo", "Camera & Video")) },
            navigationIcon = {
                IconButton(onClick = { save(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Voltar", "Back"))
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
            SectionHeader(tr("Vídeo", "Video"))
            SwitchRow(
                title = tr("Transmissão de Vídeo", "Video Streaming"),
                description = tr("Desative para economizar bateria. O áudio continua ativo.", "Disable to save battery. Audio remains active."),
                checked = videoStreamingEnabled,
                onCheckedChange = { videoStreamingEnabled = it },
            )
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
                label = tr("Taxa de Quadros (fps)", "Frame Rate (fps)"),
                placeholder = SettingsManager.DEFAULT_FRAME_RATE.toString(),
                keyboardType = KeyboardType.Number,
            )

            SectionHeader("WebRTC")
            MonoTextField(
                value = webrtcSignalingURL,
                onValueChange = { webrtcSignalingURL = it },
                label = tr("URL de Sinalização", "Signaling URL"),
                placeholder = "wss://your-server.example.com",
                keyboardType = KeyboardType.Uri,
            )

            SectionHeader(tr("Imagem", "Image"))
            LabeledSlider(
                label = tr("Brilho", "Brightness"),
                value = imageBrightness,
                onValueChange = { imageBrightness = it },
                valueRange = -1f..1f,
            )
            LabeledSlider(
                label = tr("Contraste", "Contrast"),
                value = imageContrast,
                onValueChange = { imageContrast = it },
                valueRange = 0.5f..2f,
            )
            LabeledSlider(
                label = tr("Saturação", "Saturation"),
                value = imageSaturation,
                onValueChange = { imageSaturation = it },
                valueRange = 0f..2f,
            )
        }
    }
}
