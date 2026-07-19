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
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamingMode
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamingService
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wakeword.WakeWordListener
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wakeword.normalizePhrase
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
        private const val MAX_HISTORY_TURNS = 10 // matches OpenClawBridge's cap
    }

    private val _uiState = MutableStateFlow(GeminiUiState())
    val uiState: StateFlow<GeminiUiState> = _uiState.asStateFlow()

    // Durable log of completed turns (voice + typed), kept outside GeminiUiState so it survives
    // stopSession()'s full state reset -- this is what lets the conversation continue across the
    // AI on/off toggle instead of starting cold every time. Also hydrated from disk at startup
    // and persisted on every append, so the AI keeps remembering across app restarts too, bounded
    // by SettingsManager.conversationHistoryRetentionDays.
    private val historyStore = ConversationHistoryStore(application)
    private val _conversationHistory = MutableStateFlow(
        historyStore.recentTurnsForSeed(SettingsManager.conversationHistoryRetentionDays, MAX_HISTORY_TURNS * 2)
    )
    val conversationHistory: StateFlow<List<ConversationTurn>> = _conversationHistory.asStateFlow()

    private fun appendTurn(turn: ConversationTurn) {
        _conversationHistory.value = (_conversationHistory.value + turn).takeLast(MAX_HISTORY_TURNS * 2)
        historyStore.appendTurn(turn, SettingsManager.conversationHistoryRetentionDays)
    }

    // Set by sendChatMessage() when it has to cold-start the session first; flushed once the
    // new connection is Ready.
    private var pendingChatMessage: String? = null

    // Gemini Live's real session resumption handle (survives stopSession() like
    // conversationHistory). Once we have one, it's used instead of seedHistory's transcript
    // replay, which testing showed the model doesn't actually treat as real context.
    private var sessionResumptionHandle: String? = null

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

    // Bridge to let the idle wake-word listener (which lives here) trigger camera/recording
    // actions (which live on StreamViewModel) -- mirrors StreamViewModel's own
    // `geminiViewModel` field, wired the same way from StreamScreen.
    var streamViewModel: StreamViewModel? = null

    // Wake word only listens while the containing screen is on-screen and the AI isn't
    // already active -- call from the composable's mount/dispose so it never runs unattended.
    fun onScreenActive() {
        isScreenActive = true
        refreshWakeWordListening()
    }

    fun onScreenInactive() {
        isScreenActive = false
        wakeWordListener.stop()
        stopStreamingServiceIfIdle()
    }

    private fun refreshWakeWordListening() {
        if (isScreenActive && !_uiState.value.isGeminiActive) {
            val triggers = mutableListOf<Pair<String, () -> Unit>>()
            if (SettingsManager.wakeWordEnabled) {
                triggers.add(SettingsManager.wakePhrase to {
                    Log.d(TAG, "Wake phrase detected, starting session")
                    startSession()
                })
            }
            if (SettingsManager.openClawWakeWordEnabled) {
                triggers.add(SettingsManager.openClawWakePhrase to {
                    Log.d(TAG, "OpenClaw wake phrase detected, activating OpenClaw")
                    activateOpenClawByVoice()
                })
            }
            if (SettingsManager.cameraWakeWordEnabled) {
                triggers.add(SettingsManager.cameraStartPhrase to {
                    Log.d(TAG, "Camera start phrase detected")
                    streamViewModel?.startStream()
                })
                triggers.add(SettingsManager.cameraStopPhrase to {
                    Log.d(TAG, "Camera stop phrase detected")
                    streamViewModel?.stopStream()
                })
            }
            if (SettingsManager.recordingWakeWordEnabled) {
                triggers.add(SettingsManager.recordingStartPhrase to {
                    Log.d(TAG, "Recording start phrase detected")
                    streamViewModel?.startRecording()
                })
                triggers.add(SettingsManager.recordingStopPhrase to {
                    Log.d(TAG, "Recording stop phrase detected")
                    streamViewModel?.stopRecording()
                })
            }
            if (triggers.isNotEmpty()) {
                // Keep listening alive even with the screen off or the app backgrounded --
                // hands-free, phone-in-pocket use is the whole point of wake words, and Android
                // cuts mic access for background apps without this.
                StreamingService.start(getApplication())
                wakeWordListener.start(triggers)
            } else {
                wakeWordListener.stop()
                stopStreamingServiceIfIdle()
            }
        } else {
            wakeWordListener.stop()
            stopStreamingServiceIfIdle()
        }
    }

    // Only stop the shared foreground service if nothing else needs it -- StreamViewModel may
    // still be relying on it for camera streaming.
    private fun stopStreamingServiceIfIdle() {
        val cameraStillStreaming = streamViewModel?.uiState?.value?.streamSessionState ==
            StreamSessionState.STREAMING
        if (!_uiState.value.isGeminiActive && !cameraStillStreaming) {
            StreamingService.stop(getApplication())
        }
    }

    // After each AI response, give the user a short window to keep talking without repeating
    // the wake phrase; if nothing comes in, close the session and go back to wake-word listening.
    // In continuous conversation mode this auto-close is skipped entirely -- the session stays
    // open until the user manually turns the AI off, no matter how long the silence lasts.
    private fun scheduleFollowUpTimeout() {
        followUpTimeoutJob?.cancel()
        if (SettingsManager.continuousConversationEnabled) return
        followUpTimeoutJob = viewModelScope.launch {
            delay(STRICT_FOLLOWUP_WINDOW_MS)
            Log.d(TAG, "No follow-up within ${STRICT_FOLLOWUP_WINDOW_MS}ms, closing session")
            stopSession()
        }
    }

    fun startSession() {
        if (_uiState.value.isGeminiActive) return
        wakeWordListener.stop()

        // Keeps the mic (and network) alive once the screen turns off or the app leaves the
        // foreground -- without a running foreground service declaring the microphone type,
        // Android cuts audio capture entirely for background apps. Safe to call even if
        // StreamViewModel already started it for camera streaming (idempotent).
        StreamingService.start(getApplication())

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
            val turnState = _uiState.value
            if (turnState.userTranscript.isNotEmpty()) {
                appendTurn(ConversationTurn(ConversationTurn.Role.USER, turnState.userTranscript))
            }
            if (turnState.aiTranscript.isNotEmpty()) {
                appendTurn(ConversationTurn(ConversationTurn.Role.ASSISTANT, turnState.aiTranscript))
            }
            _uiState.value = _uiState.value.copy(userTranscript = "")

            // Voice-triggered end of session -- checked against the live conversation's own
            // transcription, since the idle wake-word listener is off while a session is active
            // and can't be reused here the way the start phrase is.
            val stopPhraseDetected = SettingsManager.aiStopPhraseEnabled &&
                normalizePhrase(turnState.userTranscript).contains(normalizePhrase(SettingsManager.aiStopPhrase))
            if (stopPhraseDetected) {
                Log.d(TAG, "Stop phrase detected, ending session")
                stopSession()
            } else if (SettingsManager.wakeWordEnabled && !_uiState.value.toolCallStatus.isActive) {
                // Don't start the silence countdown while a tool call is still running in the
                // background (e.g. an OpenClaw task) -- the model's final spoken confirmation is
                // still coming and can take much longer than the follow-up window.
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

        service.onSessionResumptionUpdate = { handle ->
            sessionResumptionHandle = handle
        }

        // Start session (OpenClaw connectivity is only checked once it's toggled on)
        viewModelScope.launch {
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
            service.connect(sessionResumptionHandle) { setupOk ->
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

                // Only fall back to the transcript-replay hack when we don't already have a real
                // resumption handle -- testing showed the model doesn't treat replayed
                // clientContent turns as genuine context, so once resumption is available for
                // this session, sending both would just add noise without helping.
                if (sessionResumptionHandle == null && _conversationHistory.value.isNotEmpty()) {
                    Log.d(TAG, "startSession: no resumption handle yet, seeding ${_conversationHistory.value.size} turn(s)")
                    service.seedHistory(_conversationHistory.value)
                }

                // Flush a chat message typed while the AI was off -- it triggered this
                // startSession() call, so send it now that the connection is Ready.
                pendingChatMessage?.let { msg ->
                    appendTurn(ConversationTurn(ConversationTurn.Role.USER, msg))
                    service.sendTextMessage(msg)
                    pendingChatMessage = null
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

    // Sends a typed message, sharing the same history as voice turns. If the AI is currently
    // off, starts a session first (which will replay history) and flushes this message once
    // the new connection is Ready.
    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        if (_uiState.value.isGeminiActive) {
            appendTurn(ConversationTurn(ConversationTurn.Role.USER, text))
            activeService?.sendTextMessage(text)
        } else {
            pendingChatMessage = text
            startSession()
        }
    }

    fun stopSession() {
        followUpTimeoutJob?.cancel()
        followUpTimeoutJob = null
        pendingChatMessage = null
        eventClient.disconnect()
        toolCallRouter?.cancelAll()
        toolCallRouter = null
        openClawBridge.markInactive()
        audioManager.stopCapture()
        activeService?.disconnect()
        activeService = null
        stateObservationJob?.cancel()
        stateObservationJob = null
        _uiState.value = GeminiUiState()
        // Decides on its own whether the shared foreground service should keep running (wake
        // words listening, or camera still streaming) or stop.
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
        val active = !_uiState.value.isOpenClawActive
        _uiState.value = _uiState.value.copy(isOpenClawActive = active)
        if (active) {
            viewModelScope.launch {
                openClawBridge.checkConnection()
                openClawBridge.resetSession()
            }
        } else {
            openClawBridge.markInactive()
        }
    }

    // Voice-triggered equivalent of tapping the OpenClaw button on -- idempotent (unlike
    // toggleOpenClaw) so hearing the phrase again while it's already on is a no-op, not an
    // accidental deactivation.
    private fun activateOpenClawByVoice() {
        if (_uiState.value.isOpenClawActive) return
        _uiState.value = _uiState.value.copy(isOpenClawActive = true)
        viewModelScope.launch {
            openClawBridge.checkConnection()
            openClawBridge.resetSession()
        }
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
