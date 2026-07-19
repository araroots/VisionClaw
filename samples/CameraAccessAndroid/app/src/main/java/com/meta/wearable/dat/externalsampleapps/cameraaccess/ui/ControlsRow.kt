package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    onToggleSpeaker: () -> Unit,
    isSpeakerOn: Boolean,
    onToggleLive: () -> Unit,
    isLiveActive: Boolean,
    onToggleChat: () -> Unit,
    isChatOpen: Boolean,
    onToggleRecord: () -> Unit,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
) {
    // Local to this composable on purpose -- nothing outside ControlsRow needs to know whether
    // the secondary tray is open, so it doesn't need to be lifted into the session view model.
    var isMoreOpen by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(if (isMoreOpen) 180f else 0f, label = "moreChevron")

    Column(
        modifier = modifier.navigationBarsPadding().fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Primary row: the four actions used in nearly every session. AI gets extra height,
        // brand-blue color, and a text label -- it's the reason this screen exists, not just
        // another icon in the row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Button(
                onClick = onStopStream,
                modifier = Modifier.weight(0.85f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColor.Red),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Stop", tint = Color.White)
            }

            Button(
                onClick = onCapturePhoto,
                enabled = isCaptureEnabled,
                modifier = Modifier.weight(0.85f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Capture Photo",
                    tint = Color.Black,
                )
            }

            // AI toggle -- the featured button: bigger, brand blue, sparkle icon, "IA" label.
            Button(
                onClick = onToggleAI,
                modifier = Modifier.weight(1.3f).height(68.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAIActive) AppColor.Green else AppColor.DeepBlue,
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = if (isAIActive) "Stop AI" else "Start AI",
                        tint = Color.White,
                        modifier = Modifier.height(22.dp),
                    )
                    Text(
                        text = "IA",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                    )
                }
            }

            Button(
                onClick = onToggleLive,
                modifier = Modifier.weight(0.85f).height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLiveActive) AppColor.Red else AppColor.DeepBlue,
                ),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = if (isLiveActive) "Stop Live" else "Start Live",
                    tint = Color.White,
                )
            }
        }

        // "More options" toggle -- reveals the secondary tray below on tap.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Button(
                onClick = { isMoreOpen = !isMoreOpen },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = Color.White.copy(alpha = 0.85f),
                ),
                shape = RoundedCornerShape(999.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(text = "Mais opções", fontSize = 13.sp)
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (isMoreOpen) "Hide more options" else "Show more options",
                    modifier = Modifier.padding(start = 4.dp).height(16.dp).rotate(chevronRotation),
                )
            }
        }

        // Secondary tray: chip-style buttons (icon + label side by side) instead of bare
        // circular icons -- every control is named, not just guessed at from a glyph.
        AnimatedVisibility(
            visible = isMoreOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipButton(
                        label = "Câmera",
                        icon = Icons.Default.CameraAlt,
                        isOn = isCameraActive,
                        onColor = AppColor.Yellow,
                        onClick = onToggleCamera,
                        modifier = Modifier.weight(1f),
                    )
                    ChipButton(
                        label = "OpenClaw",
                        icon = Icons.Default.Bolt,
                        isOn = isOpenClawActive,
                        onColor = AppColor.Green,
                        onClick = onToggleOpenClaw,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipButton(
                        label = if (isMicMuted) "Mic Mudo" else "Mic",
                        icon = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        isOn = isMicMuted,
                        onColor = AppColor.Red,
                        onClick = onToggleMute,
                        modifier = Modifier.weight(1f),
                    )
                    ChipButton(
                        label = "Speaker",
                        icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        isOn = isSpeakerOn,
                        onColor = AppColor.Green,
                        onClick = onToggleSpeaker,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipButton(
                        label = "Chat",
                        icon = Icons.AutoMirrored.Filled.Chat,
                        isOn = isChatOpen,
                        onColor = AppColor.Green,
                        onClick = onToggleChat,
                        modifier = Modifier.weight(1f),
                    )
                    ChipButton(
                        label = "Gravar",
                        icon = Icons.Default.FiberManualRecord,
                        isOn = isRecording,
                        onColor = AppColor.Red,
                        onClick = onToggleRecord,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// Icon + label side by side, like a filter chip -- used for every control that moved off the
// always-visible primary row and into the "more options" tray.
@Composable
private fun ChipButton(
    label: String,
    icon: ImageVector,
    isOn: Boolean,
    onColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isOn) onColor else AppColor.DeepBlue,
        ),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.height(18.dp))
            Text(text = label, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}
