package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import android.graphics.Bitmap
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.GeminiToolCall
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.GeminiToolCallCancellation
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.ToolResult
import kotlinx.coroutines.flow.StateFlow

// Common surface for a realtime voice+vision AI backend (Gemini Live, OpenAI Realtime, ...)
interface RealtimeAIService {
    val connectionState: StateFlow<GeminiConnectionState>
    val isModelSpeaking: StateFlow<Boolean>

    val inputSampleRate: Int
    val outputSampleRate: Int
    val videoFrameIntervalMs: Long

    var onAudioReceived: ((ByteArray) -> Unit)?
    var onTurnComplete: (() -> Unit)?
    var onInterrupted: (() -> Unit)?
    var onDisconnected: ((String?) -> Unit)?
    var onInputTranscription: ((String) -> Unit)?
    var onOutputTranscription: ((String) -> Unit)?
    var onToolCall: ((GeminiToolCall) -> Unit)?
    var onToolCallCancellation: ((GeminiToolCallCancellation) -> Unit)?

    fun connect(callback: (Boolean) -> Unit)
    fun disconnect()
    fun sendAudio(data: ByteArray)
    fun sendVideoFrame(bitmap: Bitmap)
    fun sendToolResult(callId: String, name: String, result: ToolResult)
    fun sendTextMessage(text: String)

    // Replays prior conversation turns into a freshly-connected session as context, without
    // triggering a new model response -- used to fake continuity across a reconnect since
    // neither backend supports true session resumption.
    fun seedHistory(turns: List<ConversationTurn>)
}
