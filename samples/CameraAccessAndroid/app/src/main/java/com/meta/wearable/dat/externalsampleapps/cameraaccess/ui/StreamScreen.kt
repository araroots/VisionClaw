/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini.GeminiSessionViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.tr
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamingMode
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.webrtc.WebRTCSessionViewModel

// Combines brightness (-1..1), contrast (0.5..2) and saturation (0..2) into a single matrix
// so the live preview can be adjusted without touching the frames sent to the AI/OpenClaw/WebRTC.
private fun imageAdjustmentColorMatrix(brightness: Float, contrast: Float, saturation: Float): ColorMatrix {
    val saturationMatrix = ColorMatrix().apply { setToSaturation(saturation) }
    val translate = (1 - contrast) * 0.5f * 255f + brightness * 255f
    val contrastBrightnessMatrix = ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f,
        )
    )
    saturationMatrix.timesAssign(contrastBrightnessMatrix)
    return saturationMatrix
}

@Composable
fun StreamScreen(
    wearablesViewModel: WearablesViewModel,
    isPhoneMode: Boolean = false,
    modifier: Modifier = Modifier,
    streamViewModel: StreamViewModel =
        viewModel(
            factory =
                StreamViewModel.Factory(
                    application = (LocalActivity.current as ComponentActivity).application,
                    wearablesViewModel = wearablesViewModel,
                ),
        ),
    geminiViewModel: GeminiSessionViewModel = viewModel(),
    webrtcViewModel: WebRTCSessionViewModel = viewModel(),
) {
    val streamUiState by streamViewModel.uiState.collectAsStateWithLifecycle()
    val geminiUiState by geminiViewModel.uiState.collectAsStateWithLifecycle()
    val webrtcUiState by webrtcViewModel.uiState.collectAsStateWithLifecycle()
    val conversationHistory by geminiViewModel.conversationHistory.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var isChatPanelOpen by remember { mutableStateOf(false) }
    // Measured live off ControlsRow itself (rather than a hardcoded offset) so the chat panel
    // always clears it -- ControlsRow's height now varies with whether the "more options" tray
    // is expanded, and a fixed padding either overlapped it when open or left a wide gap when
    // closed.
    val density = LocalDensity.current
    var controlsHeight by remember { mutableStateOf(0.dp) }

    // Wire Gemini VM to Stream VM for frame forwarding
    LaunchedEffect(geminiViewModel) {
        streamViewModel.geminiViewModel = geminiViewModel
    }

    // Wire Stream VM to Gemini VM so the idle wake-word listener can trigger camera/recording
    LaunchedEffect(streamViewModel) {
        geminiViewModel.streamViewModel = streamViewModel
    }

    // Wire WebRTC VM to Stream VM for frame forwarding
    LaunchedEffect(webrtcViewModel) {
        streamViewModel.webrtcViewModel = webrtcViewModel
    }

    // Start phone camera automatically; glasses camera is started manually via the camera toggle
    LaunchedEffect(isPhoneMode) {
        if (isPhoneMode) {
            geminiViewModel.streamingMode = StreamingMode.PHONE
            streamViewModel.startPhoneCamera(lifecycleOwner)
        } else {
            geminiViewModel.streamingMode = StreamingMode.GLASSES
        }
    }

    // Wake word only listens while this screen is on-screen; clean up everything on exit
    DisposableEffect(Unit) {
        geminiViewModel.onScreenActive()
        onDispose {
            geminiViewModel.onScreenInactive()
            if (geminiUiState.isGeminiActive) {
                geminiViewModel.stopSession()
            }
            if (webrtcUiState.isActive) {
                webrtcViewModel.stopSession()
            }
        }
    }

    // Show errors as toasts
    LaunchedEffect(geminiUiState.errorMessage) {
        geminiUiState.errorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            geminiViewModel.clearError()
        }
    }
    LaunchedEffect(webrtcUiState.errorMessage) {
        webrtcUiState.errorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            webrtcViewModel.clearError()
        }
    }

    val imageColorMatrix = remember {
        imageAdjustmentColorMatrix(
            brightness = SettingsManager.imageBrightness,
            contrast = SettingsManager.imageContrast,
            saturation = SettingsManager.imageSaturation,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Background shown while waiting for the first video frame (instead of blank white) --
        // a real AI-generated 360-degree turntable spin of the glasses (see RotatingGlassesVideo.kt),
        // centered and slightly above the vertical middle of the screen.
        if (streamUiState.videoFrame == null) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
                RotatingGlassesVideo(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-60).dp)
                        .fillMaxWidth(0.8f),
                )
            }
        }

        // Video feed
        streamUiState.videoFrame?.let { videoFrame ->
            Image(
                bitmap = videoFrame.asImageBitmap(),
                contentDescription = tr("Transmissão ao vivo", "Live stream"),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(imageColorMatrix),
            )
        }

        if (streamUiState.streamSessionState == StreamSessionState.STARTING) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Overlays + controls
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // Top overlays (below status bar)
            Column(modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 8.dp)) {
                // Gemini overlay
                if (geminiUiState.isGeminiActive) {
                    GeminiOverlay(uiState = geminiUiState)
                }

                // WebRTC overlay
                if (webrtcUiState.isActive) {
                    Spacer(modifier = Modifier.height(4.dp))
                    WebRTCOverlay(uiState = webrtcUiState)
                }
            }

            // Settings shortcut (top-right) -- StreamScreen had no way to reach Settings without
            // first stopping the session. Dark scrim like the "Mais opções" pill so it stays
            // legible over both the white idle background and the live video feed.
            IconButton(
                onClick = { wearablesViewModel.showSettings() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 4.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = tr("Configurações", "Settings"),
                    tint = Color.White,
                )
            }

            // Chat panel (voice + typed history) -- visibility is independent of isGeminiActive
            // so the log stays reviewable/usable even while the AI toggle is off. Kept compact
            // and pinned just above the controls row so it does not cover most of the camera
            // view when the camera is active (it used to take half the screen height).
            if (isChatPanelOpen) {
                ChatPanel(
                    history = conversationHistory,
                    onSend = { geminiViewModel.sendChatMessage(it) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = controlsHeight + 16.dp)
                        .fillMaxWidth(0.9f)
                        .heightIn(max = 240.dp),
                )
            }

            // Controls at bottom
            ControlsRow(
                onStopStream = {
                    if (geminiUiState.isGeminiActive) geminiViewModel.stopSession()
                    if (webrtcUiState.isActive) webrtcViewModel.stopSession()
                    streamViewModel.stopStream()
                    wearablesViewModel.navigateToDeviceSelection()
                },
                onCapturePhoto = { streamViewModel.capturePhoto() },
                isCaptureEnabled = isPhoneMode || streamUiState.streamSessionState == StreamSessionState.STREAMING,
                onToggleCamera = {
                    if (streamUiState.streamSessionState == StreamSessionState.STOPPED) {
                        // Restarting in phone mode must go back through startPhoneCamera() --
                        // the plain startStream() call is glasses-only, which is why this button
                        // used to bring the glasses stream back instead of the phone camera.
                        if (isPhoneMode) {
                            streamViewModel.startPhoneCamera(lifecycleOwner)
                        } else {
                            streamViewModel.startStream()
                        }
                    } else {
                        streamViewModel.stopStream()
                    }
                },
                isCameraActive = streamUiState.streamSessionState != StreamSessionState.STOPPED,
                onToggleAI = {
                    if (geminiUiState.isGeminiActive) {
                        geminiViewModel.stopSession()
                    } else {
                        geminiViewModel.startSession()
                    }
                },
                isAIActive = geminiUiState.isGeminiActive,
                onToggleOpenClaw = { geminiViewModel.toggleOpenClaw() },
                isOpenClawActive = geminiUiState.isOpenClawActive,
                onToggleMute = { geminiViewModel.toggleMicMute() },
                isMicMuted = geminiUiState.isMicMuted,
                onToggleSpeaker = { geminiViewModel.toggleSpeaker() },
                isSpeakerOn = geminiUiState.isSpeakerOn,
                onToggleLive = {
                    if (webrtcUiState.isActive) {
                        webrtcViewModel.stopSession()
                    } else {
                        webrtcViewModel.startSession()
                    }
                },
                isLiveActive = webrtcUiState.isActive,
                onToggleChat = { isChatPanelOpen = !isChatPanelOpen },
                isChatOpen = isChatPanelOpen,
                onToggleRecord = {
                    if (streamUiState.isRecording) {
                        streamViewModel.stopRecording()
                    } else {
                        streamViewModel.startRecording()
                    }
                },
                isRecording = streamUiState.isRecording,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { controlsHeight = with(density) { it.height.toDp() } },
            )
        }
    }

    // Share photo dialog
    streamUiState.capturedPhoto?.let { photo ->
        if (streamUiState.isShareDialogVisible) {
            SharePhotoDialog(
                photo = photo,
                onDismiss = { streamViewModel.hideShareDialog() },
                onShare = { bitmap ->
                    streamViewModel.sharePhoto(bitmap)
                    streamViewModel.hideShareDialog()
                },
            )
        }
    }

    // Share video dialog
    streamUiState.recordedVideoFile?.let { file ->
        if (streamUiState.isShareVideoDialogVisible) {
            ShareVideoDialog(
                file = file,
                onDismiss = { streamViewModel.hideShareVideoDialog() },
                onShare = {
                    streamViewModel.shareVideo(file)
                    streamViewModel.hideShareVideoDialog()
                },
            )
        }
    }
}
