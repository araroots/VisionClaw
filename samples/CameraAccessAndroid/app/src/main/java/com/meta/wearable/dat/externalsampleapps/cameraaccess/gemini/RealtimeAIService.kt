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

    // Fired when the backend hands back a fresh resumption handle (Gemini Live only -- OpenAI
    // Realtime has no equivalent here and never invokes this). The caller should hold onto the
    // latest handle and pass it into the next connect() call to truly resume the same server
    // side session/context, instead of faking continuity via seedHistory.
    var onSessionResumptionUpdate: ((String) -> Unit)?

    // resumptionHandle: if non-null (Gemini Live only), asks the server to resume that exact
    // prior session instead of starting cold.
    fun connect(resumptionHandle: String? = null, callback: (Boolean) -> Unit)
    fun disconnect()
    fun sendAudio(data: ByteArray)
    fun sendVideoFrame(bitmap: Bitmap)
    fun sendToolResult(callId: String, name: String, result: ToolResult)
    fun sendTextMessage(text: String)

    // Replays prior conversation turns into a freshly-connected session as context, without
    // triggering a new model response. Only meaningful when real session resumption isn't
    // available (OpenAI Realtime, or Gemini's very first connection with no handle yet) --
    // sending both would duplicate context that resumption already restored.
    fun seedHistory(turns: List<ConversationTurn>)
}
