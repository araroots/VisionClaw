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

    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private var triggered = false
    private var targetPhrase: String = ""

    var onWakePhraseDetected: (() -> Unit)? = null

    fun start(phrase: String) {
        if (isListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition not available on this device")
            return
        }
        targetPhrase = normalize(phrase)
        if (targetPhrase.isEmpty()) return
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
            if (normalize(candidate).contains(targetPhrase)) {
                Log.d(TAG, "Wake phrase detected: $candidate")
                triggered = true
                onWakePhraseDetected?.invoke()
                return
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
