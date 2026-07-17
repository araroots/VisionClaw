package com.meta.wearable.dat.externalsampleapps.cameraaccess.wakeword

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.text.Normalizer

// Continuously listens for a spoken wake phrase using Android's built-in SpeechRecognizer.
// Each recognition session is short-lived (ends after a pause or timeout), so this restarts
// itself in a loop while active -- there's a brief gap between sessions where speech can be missed.
class WakeWordListener(private val context: Context) {
    companion object {
        private const val TAG = "WakeWordListener"
    }

    private data class Trigger(val normalizedPhrase: String, val onDetected: () -> Unit)

    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private var triggered = false
    private var triggers: List<Trigger> = emptyList()

    // Listens for any number of distinct phrases in the same recognition session (a single
    // SpeechRecognizer instance is what's reliable on-device -- running two in parallel isn't),
    // invoking whichever phrase's callback matches first.
    fun start(phrases: List<Pair<String, () -> Unit>>) {
        if (isListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition not available on this device")
            return
        }
        triggers = phrases
            .map { (phrase, callback) -> Trigger(normalize(phrase), callback) }
            .filter { it.normalizedPhrase.isNotEmpty() }
        if (triggers.isEmpty()) return
        isListening = true
        createAndStartRecognizer()
    }

    fun stop() {
        isListening = false
        recognizer?.let {
            it.setRecognitionListener(null)
            it.destroy()
        }
        recognizer = null
    }

    private fun createAndStartRecognizer() {
        if (!isListening) return
        triggered = false

        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                restart()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                checkMatch(partialResults)
            }

            override fun onResults(results: Bundle?) {
                checkMatch(results)
                restart()
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        }

        try {
            r.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening: ${e.message}")
            restart()
        }
    }

    private fun checkMatch(bundle: Bundle?) {
        if (triggered) return
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        for (candidate in matches) {
            val normalizedCandidate = normalize(candidate)
            for (trigger in triggers) {
                if (normalizedCandidate.contains(trigger.normalizedPhrase)) {
                    Log.d(TAG, "Wake phrase detected: $candidate")
                    triggered = true
                    trigger.onDetected()
                    return
                }
            }
        }
    }

    private fun restart() {
        recognizer?.let {
            it.setRecognitionListener(null)
            it.destroy()
        }
        recognizer = null
        if (isListening) {
            createAndStartRecognizer()
        }
    }

    private fun normalize(text: String): String {
        val stripped = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return stripped.lowercase().trim()
    }
}
