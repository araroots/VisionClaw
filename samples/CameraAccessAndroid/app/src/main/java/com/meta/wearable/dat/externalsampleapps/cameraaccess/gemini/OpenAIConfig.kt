package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager

object OpenAIConfig {
    const val WEBSOCKET_BASE_URL = "wss://api.openai.com/v1/realtime"
    const val MODEL = "gpt-realtime"
    const val VOICE = "alloy"
    const val TRANSCRIPTION_MODEL = "gpt-4o-mini-transcribe"

    // The Realtime API's PCM format is fixed at 24kHz for both directions.
    const val INPUT_AUDIO_SAMPLE_RATE = 24000
    const val OUTPUT_AUDIO_SAMPLE_RATE = 24000

    const val VIDEO_JPEG_QUALITY = 50

    // Unlike Gemini's dedicated realtimeInput.video channel, OpenAI treats each frame as a
    // conversation.item.create -- a heavier operation not meant for near-continuous streaming.
    // Space these out further to avoid disrupting in-progress audio responses.
    const val VIDEO_FRAME_INTERVAL_MS = 4000L

    val apiKey: String
        get() = SettingsManager.openaiAPIKey

    val isConfigured: Boolean
        get() = apiKey != "YOUR_OPENAI_API_KEY" && apiKey.isNotEmpty()
}
