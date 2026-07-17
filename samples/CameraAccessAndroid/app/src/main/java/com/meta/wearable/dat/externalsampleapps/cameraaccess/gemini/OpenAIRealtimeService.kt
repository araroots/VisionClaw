package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.GeminiFunctionCall
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.GeminiToolCall
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.GeminiToolCallCancellation
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.ToolDeclarations
import com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw.ToolResult
import java.io.ByteArrayOutputStream
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject

// WebSocket client for the OpenAI Realtime API (wss://api.openai.com/v1/realtime),
// implementing the same surface as GeminiLiveService.
class OpenAIRealtimeService : RealtimeAIService {
    companion object {
        private const val TAG = "OpenAIRealtimeService"
    }

    private val _connectionState = MutableStateFlow<GeminiConnectionState>(GeminiConnectionState.Disconnected)
    override val connectionState: StateFlow<GeminiConnectionState> = _connectionState.asStateFlow()

    private val _isModelSpeaking = MutableStateFlow(false)
    override val isModelSpeaking: StateFlow<Boolean> = _isModelSpeaking.asStateFlow()

    override val inputSampleRate = OpenAIConfig.INPUT_AUDIO_SAMPLE_RATE
    override val outputSampleRate = OpenAIConfig.OUTPUT_AUDIO_SAMPLE_RATE
    override val videoFrameIntervalMs = OpenAIConfig.VIDEO_FRAME_INTERVAL_MS

    override var onAudioReceived: ((ByteArray) -> Unit)? = null
    override var onTurnComplete: (() -> Unit)? = null
    override var onInterrupted: (() -> Unit)? = null
    override var onDisconnected: ((String?) -> Unit)? = null
    override var onInputTranscription: ((String) -> Unit)? = null
    override var onOutputTranscription: ((String) -> Unit)? = null
    override var onToolCall: ((GeminiToolCall) -> Unit)? = null
    override var onToolCallCancellation: ((GeminiToolCallCancellation) -> Unit)? = null

    private var webSocket: WebSocket? = null
    private val sendExecutor = Executors.newSingleThreadExecutor()
    private var connectCallback: ((Boolean) -> Unit)? = null
    private var timeoutTimer: Timer? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    override fun connect(callback: (Boolean) -> Unit) {
        if (!OpenAIConfig.isConfigured) {
            _connectionState.value = GeminiConnectionState.Error("No API key configured")
            callback(false)
            return
        }

        _connectionState.value = GeminiConnectionState.Connecting
        connectCallback = callback

        val request = Request.Builder()
            .url("${OpenAIConfig.WEBSOCKET_BASE_URL}?model=${OpenAIConfig.MODEL}")
            .addHeader("Authorization", "Bearer ${OpenAIConfig.apiKey}")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                _connectionState.value = GeminiConnectionState.SettingUp
                sendSessionUpdate()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleMessage(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val msg = t.message ?: "Unknown error"
                Log.e(TAG, "WebSocket failure: $msg")
                _connectionState.value = GeminiConnectionState.Error(msg)
                _isModelSpeaking.value = false
                resolveConnect(false)
                onDisconnected?.invoke(msg)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                _connectionState.value = GeminiConnectionState.Disconnected
                _isModelSpeaking.value = false
                resolveConnect(false)
                onDisconnected?.invoke("Connection closed (code $code: $reason)")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                _connectionState.value = GeminiConnectionState.Disconnected
                _isModelSpeaking.value = false
            }
        })

        // Timeout after 15 seconds (use Timer so we don't block sendExecutor)
        timeoutTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    if (_connectionState.value == GeminiConnectionState.Connecting
                        || _connectionState.value == GeminiConnectionState.SettingUp) {
                        Log.e(TAG, "Connection timed out")
                        _connectionState.value = GeminiConnectionState.Error("Connection timed out")
                        resolveConnect(false)
                    }
                }
            }, 15000)
        }
    }

    override fun disconnect() {
        timeoutTimer?.cancel()
        timeoutTimer = null
        webSocket?.close(1000, null)
        webSocket = null
        onToolCall = null
        onToolCallCancellation = null
        _connectionState.value = GeminiConnectionState.Disconnected
        _isModelSpeaking.value = false
        resolveConnect(false)
    }

    override fun sendAudio(data: ByteArray) {
        if (_connectionState.value != GeminiConnectionState.Ready) return
        sendExecutor.execute {
            val base64 = Base64.encodeToString(data, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("type", "input_audio_buffer.append")
                put("audio", base64)
            }
            webSocket?.send(json.toString())
        }
    }

    override fun sendVideoFrame(bitmap: Bitmap) {
        if (_connectionState.value != GeminiConnectionState.Ready) return
        // Sending a frame while the model is speaking interrupts its audio response.
        if (_isModelSpeaking.value) return
        sendExecutor.execute {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, OpenAIConfig.VIDEO_JPEG_QUALITY, baos)
            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("type", "conversation.item.create")
                put("item", JSONObject().apply {
                    put("type", "message")
                    put("role", "user")
                    put("content", JSONArray().put(JSONObject().apply {
                        put("type", "input_image")
                        put("image_url", "data:image/jpeg;base64,$base64")
                        put("detail", "low")
                    }))
                })
            }
            webSocket?.send(json.toString())
        }
    }

    override fun sendToolResult(callId: String, name: String, result: ToolResult) {
        sendExecutor.execute {
            val output = when (result) {
                is ToolResult.Success -> result.result
                is ToolResult.Failure -> result.toJSON().toString()
            }
            val createItem = JSONObject().apply {
                put("type", "conversation.item.create")
                put("item", JSONObject().apply {
                    put("type", "function_call_output")
                    put("call_id", callId)
                    put("output", output)
                })
            }
            webSocket?.send(createItem.toString())
            // Function call outputs don't auto-trigger a follow-up response.
            webSocket?.send(JSONObject().put("type", "response.create").toString())
        }
    }

    override fun sendTextMessage(text: String) {
        if (_connectionState.value != GeminiConnectionState.Ready) return
        sendExecutor.execute {
            val createItem = JSONObject().apply {
                put("type", "conversation.item.create")
                put("item", JSONObject().apply {
                    put("type", "message")
                    put("role", "user")
                    put("content", JSONArray().put(JSONObject().apply {
                        put("type", "input_text")
                        put("text", text)
                    }))
                })
            }
            webSocket?.send(createItem.toString())
            webSocket?.send(JSONObject().put("type", "response.create").toString())
        }
    }

    override fun seedHistory(turns: List<ConversationTurn>) {
        if (turns.isEmpty()) return
        if (_connectionState.value != GeminiConnectionState.Ready) return
        Log.d(TAG, "seedHistory: replaying ${turns.size} turn(s)")
        sendExecutor.execute {
            // One conversation.item.create per turn, deliberately with no trailing
            // response.create -- these are prior turns being replayed as context after a
            // reconnect, not a new prompt, so they must not trigger a fresh model response.
            for (turn in turns) {
                val role = if (turn.role == ConversationTurn.Role.USER) "user" else "assistant"
                val contentType = if (turn.role == ConversationTurn.Role.USER) "input_text" else "text"
                val createItem = JSONObject().apply {
                    put("type", "conversation.item.create")
                    put("item", JSONObject().apply {
                        put("type", "message")
                        put("role", role)
                        put("status", "completed")
                        put("content", JSONArray().put(JSONObject().apply {
                            put("type", contentType)
                            put("text", turn.text)
                        }))
                    })
                }
                Log.d(TAG, "seedHistory item: $createItem")
                webSocket?.send(createItem.toString())
            }
        }
    }

    // Private

    private fun resolveConnect(success: Boolean) {
        val cb = connectCallback
        connectCallback = null // null out BEFORE invoking to prevent re-entrancy
        timeoutTimer?.cancel()
        timeoutTimer = null
        cb?.invoke(success)
    }

    private fun sendSessionUpdate() {
        val sessionUpdate = JSONObject().apply {
            put("type", "session.update")
            put("session", JSONObject().apply {
                put("type", "realtime")
                put("model", OpenAIConfig.MODEL)
                put("output_modalities", JSONArray().put("audio"))
                put("instructions", GeminiConfig.systemInstruction)
                put("audio", JSONObject().apply {
                    put("input", JSONObject().apply {
                        put("format", JSONObject().apply {
                            put("type", "audio/pcm")
                            put("rate", OpenAIConfig.INPUT_AUDIO_SAMPLE_RATE)
                        })
                        put("turn_detection", JSONObject().apply {
                            put("type", "server_vad")
                            put("threshold", 0.5)
                            put("prefix_padding_ms", 300)
                            put("silence_duration_ms", 500)
                            put("create_response", true)
                            put("interrupt_response", true)
                        })
                        put("transcription", JSONObject().apply {
                            put("model", OpenAIConfig.TRANSCRIPTION_MODEL)
                        })
                    })
                    put("output", JSONObject().apply {
                        put("format", JSONObject().apply {
                            put("type", "audio/pcm")
                            put("rate", OpenAIConfig.OUTPUT_AUDIO_SAMPLE_RATE)
                        })
                        put("voice", OpenAIConfig.VOICE)
                    })
                })
                put("tools", ToolDeclarations.allDeclarationsOpenAIJSON())
                put("tool_choice", "auto")
                // Without this, conversation context grows unbounded within the session and
                // both transcription and response latency creep up the longer the call runs.
                put("truncation", "auto")
            })
        }
        // Send directly (not via sendExecutor) to ensure it's the first message
        webSocket?.send(sessionUpdate.toString())
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)

            when (json.optString("type", "")) {
                "session.updated" -> {
                    _connectionState.value = GeminiConnectionState.Ready
                    resolveConnect(true)
                }

                "error" -> {
                    val error = json.optJSONObject("error")
                    val msg = error?.optString("message", "Unknown error") ?: "Unknown error"
                    Log.e(TAG, "Server error: $msg")
                    if (_connectionState.value != GeminiConnectionState.Ready) {
                        _connectionState.value = GeminiConnectionState.Error(msg)
                        resolveConnect(false)
                    }
                }

                "response.output_audio.delta" -> {
                    val base64Data = json.optString("delta", "")
                    if (base64Data.isNotEmpty()) {
                        val audioData = Base64.decode(base64Data, Base64.DEFAULT)
                        _isModelSpeaking.value = true
                        onAudioReceived?.invoke(audioData)
                    }
                }

                "response.output_audio_transcript.delta" -> {
                    val delta = json.optString("delta", "")
                    if (delta.isNotEmpty()) {
                        onOutputTranscription?.invoke(delta)
                    }
                }

                "conversation.item.input_audio_transcription.completed" -> {
                    val transcript = json.optString("transcript", "")
                    if (transcript.isNotEmpty()) {
                        onInputTranscription?.invoke(transcript)
                    }
                }

                "response.function_call_arguments.done" -> {
                    val callId = json.optString("call_id", "")
                    val name = json.optString("name", "")
                    val argsStr = json.optString("arguments", "{}")
                    if (callId.isNotEmpty() && name.isNotEmpty()) {
                        val args = mutableMapOf<String, Any?>()
                        try {
                            val argsJson = JSONObject(argsStr)
                            for (key in argsJson.keys()) {
                                args[key] = argsJson.opt(key)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse function call arguments: ${e.message}")
                        }
                        onToolCall?.invoke(GeminiToolCall(listOf(GeminiFunctionCall(callId, name, args))))
                    }
                }

                "input_audio_buffer.speech_started" -> {
                    // The user started talking; if the model was mid-response, this is an interruption.
                    if (_isModelSpeaking.value) {
                        _isModelSpeaking.value = false
                        onInterrupted?.invoke()
                    }
                }

                "response.done" -> {
                    _isModelSpeaking.value = false
                    onTurnComplete?.invoke()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}")
        }
    }
}
