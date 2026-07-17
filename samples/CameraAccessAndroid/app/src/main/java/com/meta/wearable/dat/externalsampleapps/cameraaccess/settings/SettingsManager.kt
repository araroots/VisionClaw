package com.meta.wearable.dat.externalsampleapps.cameraaccess.settings

import android.content.Context
import android.content.SharedPreferences
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.externalsampleapps.cameraaccess.Secrets

object SettingsManager {
    private const val PREFS_NAME = "visionclaw_settings"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var aiProvider: AIProvider
        get() = try {
            AIProvider.valueOf(prefs.getString("aiProvider", null) ?: AIProvider.GEMINI.name)
        } catch (e: IllegalArgumentException) {
            AIProvider.GEMINI
        }
        set(value) = prefs.edit().putString("aiProvider", value.name).apply()

    var geminiAPIKey: String
        get() = prefs.getString("geminiAPIKey", null) ?: Secrets.geminiAPIKey
        set(value) = prefs.edit().putString("geminiAPIKey", value).apply()

    var openaiAPIKey: String
        get() = prefs.getString("openaiAPIKey", null) ?: Secrets.openaiAPIKey
        set(value) = prefs.edit().putString("openaiAPIKey", value).apply()

    var geminiSystemPrompt: String
        get() = prefs.getString("geminiSystemPrompt", null) ?: DEFAULT_SYSTEM_PROMPT
        set(value) = prefs.edit().putString("geminiSystemPrompt", value).apply()

    var openClawHost: String
        get() = prefs.getString("openClawHost", null) ?: Secrets.openClawHost
        set(value) = prefs.edit().putString("openClawHost", value).apply()

    var openClawPort: Int
        get() {
            val stored = prefs.getInt("openClawPort", 0)
            return if (stored != 0) stored else Secrets.openClawPort
        }
        set(value) = prefs.edit().putInt("openClawPort", value).apply()

    var openClawHookToken: String
        get() = prefs.getString("openClawHookToken", null) ?: Secrets.openClawHookToken
        set(value) = prefs.edit().putString("openClawHookToken", value).apply()

    var openClawGatewayToken: String
        get() = prefs.getString("openClawGatewayToken", null) ?: Secrets.openClawGatewayToken
        set(value) = prefs.edit().putString("openClawGatewayToken", value).apply()

    var webrtcSignalingURL: String
        get() = prefs.getString("webrtcSignalingURL", null) ?: Secrets.webrtcSignalingURL
        set(value) = prefs.edit().putString("webrtcSignalingURL", value).apply()

    var videoStreamingEnabled: Boolean
        get() = prefs.getBoolean("videoStreamingEnabled", true)
        set(value) = prefs.edit().putBoolean("videoStreamingEnabled", value).apply()

    var proactiveNotificationsEnabled: Boolean
        get() = prefs.getBoolean("proactiveNotificationsEnabled", true)
        set(value) = prefs.edit().putBoolean("proactiveNotificationsEnabled", value).apply()

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean("wakeWordEnabled", false)
        set(value) = prefs.edit().putBoolean("wakeWordEnabled", value).apply()

    var wakePhrase: String
        get() = prefs.getString("wakePhrase", null) ?: DEFAULT_WAKE_PHRASE
        set(value) = prefs.edit().putString("wakePhrase", value).apply()

    // A second, separate wake phrase that turns OpenClaw on hands-free -- distinct from the AI's
    // own wake word so the two can't be triggered by the same utterance.
    var openClawWakeWordEnabled: Boolean
        get() = prefs.getBoolean("openClawWakeWordEnabled", false)
        set(value) = prefs.edit().putBoolean("openClawWakeWordEnabled", value).apply()

    var openClawWakePhrase: String
        get() = prefs.getString("openClawWakePhrase", null) ?: DEFAULT_OPENCLAW_WAKE_PHRASE
        set(value) = prefs.edit().putString("openClawWakePhrase", value).apply()

    var continuousConversationEnabled: Boolean
        get() = prefs.getBoolean("continuousConversationEnabled", true)
        set(value) = prefs.edit().putBoolean("continuousConversationEnabled", value).apply()

    // Playback speed for the AI's spoken responses (both Gemini Live and OpenAI Realtime share
    // the same AudioManager output path, so this applies to either provider). Pitch is kept
    // constant regardless of speed -- see AudioManager's use of PlaybackParams.
    var aiSpeechSpeed: Float
        get() = prefs.getFloat("aiSpeechSpeed", 1f)
        set(value) = prefs.edit().putFloat("aiSpeechSpeed", value).apply()

    var videoQuality: VideoQuality
        get() = try {
            VideoQuality.valueOf(prefs.getString("videoQuality", null) ?: VideoQuality.MEDIUM.name)
        } catch (e: IllegalArgumentException) {
            VideoQuality.MEDIUM
        }
        set(value) = prefs.edit().putString("videoQuality", value.name).apply()

    var videoFrameRate: Int
        get() {
            val stored = prefs.getInt("videoFrameRate", 0)
            return if (stored != 0) stored else DEFAULT_FRAME_RATE
        }
        set(value) = prefs.edit().putInt("videoFrameRate", value).apply()

    var imageBrightness: Float
        get() = prefs.getFloat("imageBrightness", 0f)
        set(value) = prefs.edit().putFloat("imageBrightness", value).apply()

    var imageContrast: Float
        get() = prefs.getFloat("imageContrast", 1f)
        set(value) = prefs.edit().putFloat("imageContrast", value).apply()

    var imageSaturation: Float
        get() = prefs.getFloat("imageSaturation", 1f)
        set(value) = prefs.edit().putFloat("imageSaturation", value).apply()

    fun resetAll() {
        prefs.edit().clear().apply()
    }

    const val DEFAULT_WAKE_PHRASE = "Araguaia é Mestre"
    const val DEFAULT_OPENCLAW_WAKE_PHRASE = "Ativar OpenClaw"
    const val DEFAULT_FRAME_RATE = 24

    const val DEFAULT_SYSTEM_PROMPT = """You are an AI assistant for someone wearing Meta Ray-Ban smart glasses. You can see through their camera and have a voice conversation. Keep responses concise and natural.

You have one tool, execute, which delegates to a separate personal assistant capable of taking real-world actions: sending messages, searching the live web, managing lists/reminders/notes, controlling smart home devices, interacting with apps, and other things that reach outside this conversation or need to persist beyond it.

Answer directly, using your own knowledge, for anything that doesn't need that: general knowledge questions, translations, definitions, math, explanations, opinions, or casual conversation. You have broad knowledge already -- don't delegate a question just because you can't act on it. Only use execute when the request genuinely requires:
- Taking an action with a real-world effect (sending a message, opening/controlling an app or device, adding to a list, setting a reminder)
- Information that changes over time or is local/personal to the user (current events, live web search, files or state on their systems)
- Remembering or storing something for later (you have no memory or persistence of your own)

If you're unsure whether a simple question needs execute, it almost always doesn't -- just answer it.

Be detailed in the task description when you do call execute. Include all relevant context: names, content, platforms, quantities, etc. The assistant works better with complete information. Never pretend to have taken an action yourself without calling execute.

IMPORTANT: Before calling execute, ALWAYS speak a brief acknowledgment first. For example:
- "Sure, let me add that to your shopping list." then call execute.
- "Got it, searching for that now." then call execute.
- "On it, sending that message." then call execute.
Never call execute silently -- the user needs verbal confirmation that you heard them and are working on it. The tool may take several seconds to complete, so the acknowledgment lets them know something is happening.

For messages, confirm recipient and content before delegating unless clearly urgent."""
}
