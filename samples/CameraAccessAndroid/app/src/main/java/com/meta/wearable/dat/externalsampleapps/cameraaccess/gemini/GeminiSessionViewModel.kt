package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.OpenClawBridge
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.OpenClawEventClient
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.AIProvider
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.OpenClawConnectionState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.ToolCallRouter
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.ToolCallStatus
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.ToolResult
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamingMode
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wakeword.WakeWordListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class GeminiUiState(
    val isGeminiActive: Boolean = false,
    val isMicMuted: Boolean = false,
    val isOpenClawActive: Boolean = false,
    val connectionState: GeminiConnectionState = GeminiConnectionState.Disconnected,
    val isModelSpeaking: Boolean = false,
    val errorMessage: String? = null,
    val userTranscript: String = "",
    val aiTranscript: String = "",
    val toolCallStatus: ToolCallStatus = ToolCallStatus.Idle,
    val openClawConnectionState: OpenClawConnectionState = OpenClawConnectionState.NotConfigured,
)

class GeminiSessionViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "GeminiSessionVM"
        private const val STRICT_FOLLOWUP_WINDOW_MS = 2000L
        private const val CONTINUOUS_FOLLOWUP_WINDOW_MS = 4000L
    }

    private val _uiState = MutableStateFlow(GeminiUiState())
    val uiState: StateFlow<GeminiUiState> = _uiState.asStateFlow()

    private var activeService: RealtimeAIService? = null
    private val openClawBridge = OpenClawBridge()
    private var toolCallRouter: ToolCallRouter? = null
    private val audioManager = AudioManager()
    private val eventClient = OpenClawEventClient()
    private var lastVideoFrameTime: Long = 0
    private var stateObservationJob: Job? = null

    private val wakeWordListener = WakeWordListener(application)
    private var followUpTimeoutJob: Job? = null
    private var isScreenActive = false

    var streamingMode: StreamingMode = StreamingMode.GLASSES

    init {
        wakeWordListener.onWakePhraseDetected = {
            Log.d(TAG, "Wake phrase detected, starting session")
            startSession()
        }
    }

    // Wake word only listens while the containing screen is on-screen and the AI isn't
    // already active -- call from the composable's mount/dispose so it never runs unattended.
    fun onScreenActive() {
        isScreenActive = true
        refreshWakeWordListening()
    }

    fun onScreenInactive() {
        isScreenActive = false
        wakeWordListener.stop()
    }

    private fun refreshWakeWordListening() {
        if (isScreenActive && SettingsManager.wakeWordEnabled && !_uiState.value.isGeminiActive) {
            wakeWordListener.start(SettingsManager.wakePhrase)
        } else {
            wakeWordListener.stop()
        }
    }

    // After each AI response, give the user a short window to keep talking without repeating
    // the wake phrase; if nothing comes in, close the session and go back to wake-word listening.
    private fun scheduleFollowUpTimeout() {
        followUpTimeoutJob?.cancel()
        val windowMs = if (SettingsManager.continuousConversationEnabled) {
            CONTINUOUS_FOLLOWUP_WINDOW_MS
        } else {
            STRICT_FOLLOWUP_WINDOW_MS
        }
        followUpTimeoutJob = viewModelScope.launch {
            delay(windowMs)
            Log.d(TAG, "No follow-up within ${windowMs}ms, closing session")
            stopSession()
        }
    }

    fun startSession() {
        if (_uiState.value.isGeminiActive) return
        wakeWordListener.stop()

        val provider = SettingsManager.aiProvider
        val configured = when (provider) {
            AIProvider.GEMINI -> GeminiConfig.isConfigured
            AIProvider.OPENAI -> OpenAIConfig.isConfigured
        }
        if (!configured) {
            val msg = when (provider) {
                AIProvider.GEMINI -> "Gemini API key not configured. Open Settings and add your key from https://aistudio.google.com/apikey"
                AIProvider.OPENAI -> "OpenAI API key not configured. Open Settings and add your key from https://platform.openai.com/api-keys"
            }
            _uiState.value = _uiState.value.copy(errorMessage = msg)
            refreshWakeWordListening()
            return
        }

        val service: RealtimeAIService = when (provider) {
            AIProvider.GEMINI -> GeminiLiveService()
            AIProvider.OPENAI -> OpenAIRealtimeService()
        }
        activeService = service

        _uiState.value = _uiState.value.copy(isGeminiActive = true)

        // Wire audio callbacks
        audioManager.onAudioCaptured = lambda@{ data ->
            if (_uiState.value.isMicMuted) return@lambda
            // Phone mode: mute mic while model speaks to prevent echo
            if (streamingMode == StreamingMode.PHONE && service.isModelSpeaking.value) return@lambda
            service.sendAudio(data)
        }

        service.onAudioReceived = { data ->
            audioManager.playAudio(data)
        }

        service.onInterrupted = {
            audioManager.stopPlayback()
        }

        service.onTurnComplete = {
            _uiState.value = _uiState.value.copy(userTranscript = "")
            // Don't start the silence countdown while a tool call is still running in the
            // background (e.g. an OpenClaw task) -- the model's final spoken confirmation is
            // still coming and can take much longer than the follow-up window.
            if (SettingsManager.wakeWordEnabled && !_uiState.value.toolCallStatus.isActive) {
                scheduleFollowUpTimeout()
            }
        }

        service.onInputTranscription = { text ->
            followUpTimeoutJob?.cancel()
            _uiState.value = _uiState.value.copy(
                userTranscript = _uiState.value.userTranscript + text,
                aiTranscript = ""
            )
        }

        service.onOutputTranscription = { text ->
            _uiState.value = _uiState.value.copy(
                aiTranscript = _uiState.value.aiTranscript + text
            )
        }

        service.onDisconnected = { reason ->
            if (_uiState.value.isGeminiActive) {
                stopSession()
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Connection lost: ${reason ?: "Unknown error"}"
                )
            }
        }

        // Check OpenClaw and start session
        viewModelScope.launch {
            openClawBridge.checkConnection()
            openClawBridge.resetSession()

            // Wire tool call handling
            toolCallRouter = ToolCallRouter(openClawBridge, viewModelScope)

            service.onToolCall = { toolCall ->
                // A tool call means the turn isn't really over yet -- cancel any pending
                // silence countdown so a slow OpenClaw task doesn't get cut off.
                followUpTimeoutJob?.cancel()
                for (call in toolCall.functionCalls) {
                    if (!_uiState.value.isOpenClawActive) {
                        // Reject immediately instead of running the (potentially slow) agent
                        // task -- OpenClaw has to be turned on explicitly first.
                        service.sendToolResult(
                            call.id,
                            call.name,
                            ToolResult.Failure(
                                "OpenClaw is currently turned off. Tell the user to turn it on if they want you to take this action."
                            ),
                        )
                        continue
                    }
                    toolCallRouter?.handleToolCall(call) { callId, name, result ->
                        service.sendToolResult(callId, name, result)
                    }
                }
            }

            service.onToolCallCancellation = { cancellation ->
                toolCallRouter?.cancelToolCalls(cancellation.ids)
            }

            // Observe service state
            stateObservationJob = viewModelScope.launch {
                while (isActive) {
                    delay(100)
                    _uiState.value = _uiState.value.copy(
                        connectionState = service.connectionState.value,
                        isModelSpeaking = service.isModelSpeaking.value,
                        toolCallStatus = openClawBridge.lastToolCallStatus.value,
                        openClawConnectionState = openClawBridge.connectionState.value,
                    )
                }
            }

            // Connect to the selected AI provider
            service.connect { setupOk ->
                if (!setupOk) {
                    val msg = when (val state = service.connectionState.value) {
                        is GeminiConnectionState.Error -> state.message
                        else -> "Failed to connect"
                    }
                    _uiState.value = _uiState.value.copy(errorMessage = msg)
                    service.disconnect()
                    stateObservationJob?.cancel()
                    activeService = null
                    _uiState.value = _uiState.value.copy(
                        isGeminiActive = false,
                        connectionState = GeminiConnectionState.Disconnected
                    )
                    refreshWakeWordListening()
                    return@connect
                }

                // Start mic capture
                try {
                    audioManager.startCapture(service.inputSampleRate, service.outputSampleRate)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Mic capture failed: ${e.message}"
                    )
                    service.disconnect()
                    stateObservationJob?.cancel()
                    activeService = null
                    _uiState.value = _uiState.value.copy(
                        isGeminiActive = false,
                        connectionState = GeminiConnectionState.Disconnected
                    )
                    refreshWakeWordListening()
                }

                // Connect to OpenClaw event stream for proactive notifications
                if (SettingsManager.proactiveNotificationsEnabled) {
                    eventClient.onNotification = { text ->
                        val state = _uiState.value
                        if (state.isGeminiActive && state.connectionState == GeminiConnectionState.Ready) {
                            service.sendTextMessage(text)
                        }
                    }
                    eventClient.connect()
                }
            }
        }
    }

    fun stopSession() {
        followUpTimeoutJob?.cancel()
        followUpTimeoutJob = null
        eventClient.disconnect()
        toolCallRouter?.cancelAll()
        toolCallRouter = null
        audioManager.stopCapture()
        activeService?.disconnect()
        activeService = null
        stateObservationJob?.cancel()
        stateObservationJob = null
        _uiState.value = GeminiUiState()
        refreshWakeWordListening()
    }

    fun sendVideoFrameIfThrottled(bitmap: Bitmap) {
        if (!SettingsManager.videoStreamingEnabled) return
        if (!_uiState.value.isGeminiActive) return
        if (_uiState.value.connectionState != GeminiConnectionState.Ready) return
        val service = activeService ?: return
        val now = System.currentTimeMillis()
        if (now - lastVideoFrameTime < service.videoFrameIntervalMs) return
        lastVideoFrameTime = now
        service.sendVideoFrame(bitmap)
    }

    fun toggleMicMute() {
        _uiState.value = _uiState.value.copy(isMicMuted = !_uiState.value.isMicMuted)
    }

    fun toggleOpenClaw() {
        _uiState.value = _uiState.value.copy(isOpenClawActive = !_uiState.value.isOpenClawActive)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        stopSession()
        wakeWordListener.stop()
    }
}
