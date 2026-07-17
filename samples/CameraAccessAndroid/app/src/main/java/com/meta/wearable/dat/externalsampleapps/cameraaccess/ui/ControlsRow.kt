package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ControlsRow(
    onStopStream: () -> Unit,
    onCapturePhoto: () -> Unit,
    isCaptureEnabled: Boolean = true,
    onToggleCamera: () -> Unit,
    isCameraActive: Boolean,
    onToggleAI: () -> Unit,
    isAIActive: Boolean,
    onToggleOpenClaw: () -> Unit,
    isOpenClawActive: Boolean,
    onToggleMute: () -> Unit,
    isMicMuted: Boolean,
    onToggleLive: () -> Unit,
    isLiveActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.navigationBarsPadding().fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Secondary row: feature toggles
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Camera toggle button
            Button(
                onClick = onToggleCamera,
                modifier = Modifier.aspectRatio(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCameraActive) AppColor.Yellow else AppColor.DeepBlue,
                ),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = if (isCameraActive) "Stop Camera" else "Start Camera",
                    tint = Color.White,
                )
            }

            // OpenClaw toggle button
            Button(
                onClick = onToggleOpenClaw,
                modifier = Modifier.aspectRatio(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOpenClawActive) AppColor.Green else AppColor.DeepBlue,
                ),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = if (isOpenClawActive) "Disable OpenClaw" else "Enable OpenClaw",
                    tint = Color.White,
                )
            }

            // Mic mute toggle button
            Button(
                onClick = onToggleMute,
                modifier = Modifier.aspectRatio(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMicMuted) AppColor.Red else AppColor.DeepBlue,
                ),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isMicMuted) "Unmute Mic" else "Mute Mic",
                    tint = Color.White,
                )
            }
        }

        // Primary row: stop, capture, AI, live
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Stop/close button
            Button(
                onClick = onStopStream,
                modifier = Modifier.aspectRatio(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AppColor.Red),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Stop",
                    tint = Color.White,
                )
            }

            CaptureButton(
                onClick = onCapturePhoto,
                enabled = isCaptureEnabled,
            )

            // AI toggle button
            Button(
                onClick = onToggleAI,
                modifier = Modifier.aspectRatio(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAIActive) AppColor.Green else AppColor.DeepBlue,
                ),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = if (isAIActive) "Stop AI" else "Start AI",
                    tint = Color.White,
                )
            }

            // Live toggle button
            Button(
                onClick = onToggleLive,
                modifier = Modifier.aspectRatio(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLiveActive) AppColor.Red else AppColor.DeepBlue,
                ),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = if (isLiveActive) "Stop Live" else "Start Live",
                    tint = Color.White,
                )
            }
        }
    }
}
