package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import java.io.ByteArrayOutputStream

// Named AudioManager like the platform class it wraps parts of -- referred to as
// android.media.AudioManager (fully qualified) below wherever the system service is needed, to
// avoid colliding with this class's own name.
class AudioManager(private val context: Context) {
    companion object {
        private const val TAG = "AudioManager"
        private const val MIN_SEND_BYTES = 3200 // 100ms at 16kHz mono Int16 = 1600 frames * 2 bytes

        // Below this RMS, a chunk is treated as ambient noise rather than speech and zeroed out
        // before being sent -- neither provider's own VAD gets a chance to misread quiet
        // background noise/breathing as speech if it never receives anything but true silence
        // for it. Real speech is typically in the low thousands RMS for 16-bit PCM; this leaves
        // headroom above typical room-noise floors without requiring the user to speak loudly.
        private const val NOISE_GATE_RMS_THRESHOLD = 500.0
    }

    var onAudioCaptured: ((ByteArray) -> Unit)? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var captureThread: Thread? = null
    @Volatile
    private var isCapturing = false
    private val accumulatedData = ByteArrayOutputStream()
    private val accumulateLock = Any()
    private var focusRequest: AudioFocusRequest? = null
    private var routedToBluetoothMic = false

    @SuppressLint("MissingPermission")
    fun startCapture(inputSampleRate: Int, outputSampleRate: Int) {
        if (isCapturing) return

        // Prefer the glasses' Bluetooth mic over the phone's own -- the glasses are paired as a
        // standard Bluetooth audio device and sit right next to the wearer's mouth, so routing
        // capture through them instead of the phone's mic (which just picks up whoever/whatever
        // is nearest to the phone -- bad with the phone in a pocket) cuts down on ambient noise.
        // Bluetooth SCO (voice) and A2DP (media) cannot both be active on the same link, so this
        // does pause any phone media playback for as long as we're listening; requesting proper
        // audio focus first (instead of just grabbing the SCO device outright) is what lets the
        // system tell other apps to resume automatically once we abandon it in stopCapture().
        if (SettingsManager.useGlassesMic) {
            routeToBluetoothMicIfAvailable()
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            inputSampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            inputSampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(outputSampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(
                AudioTrack.getMinBufferSize(
                    outputSampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ) * 2
            )
            .build()

        // Independent of the input routing above -- lets the user hear AI responses through the
        // phone speaker (so people nearby can hear too) regardless of which mic is capturing.
        if (SettingsManager.useSpeakerForAiVoice) {
            routeOutputToSpeaker()
        }

        // Speed up spoken responses while keeping pitch natural (pitch=1f decouples it from
        // speed -- otherwise a faster speed also raises pitch, a la a sped-up tape).
        val speed = SettingsManager.aiSpeechSpeed
        if (speed != 1f) {
            try {
                audioTrack?.playbackParams = PlaybackParams().setSpeed(speed).setPitch(1f)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply playback speed $speed: ${e.message}")
            }
        }

        audioRecord?.startRecording()
        audioTrack?.play()
        isCapturing = true

        synchronized(accumulateLock) {
            accumulatedData.reset()
        }

        captureThread = Thread({
            val buffer = ByteArray(bufferSize)
            var tapCount = 0
            while (isCapturing) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read > 0) {
                    tapCount++
                    synchronized(accumulateLock) {
                        accumulatedData.write(buffer, 0, read)
                        if (accumulatedData.size() >= MIN_SEND_BYTES) {
                            val chunk = applyNoiseGate(accumulatedData.toByteArray())
                            accumulatedData.reset()
                            if (tapCount <= 3) {
                                Log.d(TAG, "Sending chunk: ${chunk.size} bytes (~${chunk.size / 32}ms)")
                            }
                            onAudioCaptured?.invoke(chunk)
                        }
                    }
                }
            }
        }, "audio-capture").also { it.start() }

        Log.d(TAG, "Audio capture started (in=${inputSampleRate}Hz, out=${outputSampleRate}Hz mono PCM16)")
    }

    private fun routeToBluetoothMicIfAvailable() {
        try {
            val systemAudioManager =
                context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val bluetoothDevice = systemAudioManager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            if (bluetoothDevice == null) {
                Log.d(TAG, "No Bluetooth SCO device connected, using the default mic")
                return
            }

            // Request audio focus (transient, ducking not needed since we own routing) *before*
            // grabbing the SCO device. This is what tells the system -- and well-behaved media
            // apps like Spotify/YouTube Music -- that playback should pause and then resume on
            // its own once we call abandonAudioFocusRequest() in stopCapture(). Routing straight
            // to the SCO device without going through focus first is what caused phone music to
            // stay paused indefinitely before.
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .build()
            val focusGranted = systemAudioManager.requestAudioFocus(request) ==
                android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            focusRequest = request
            Log.d(TAG, "Audio focus for glasses mic granted: $focusGranted")

            val routed = systemAudioManager.setCommunicationDevice(bluetoothDevice)
            routedToBluetoothMic = routed
            Log.d(TAG, "Routed capture to Bluetooth (glasses) mic: $routed")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to route capture to Bluetooth mic: ${e.message}")
        }
    }

    private fun routeOutputToSpeaker() {
        try {
            val systemAudioManager =
                context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val speakerDevice = systemAudioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (speakerDevice != null) {
                val routed = audioTrack?.setPreferredDevice(speakerDevice)
                Log.d(TAG, "Routed AI voice output to phone speaker: $routed")
            } else {
                Log.w(TAG, "No built-in speaker device found")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to route output to speaker: ${e.message}")
        }
    }

    // Computes RMS over the 16-bit PCM samples and zeroes the chunk out (keeping its size, so
    // downstream timing/pacing is unaffected) if it is quiet enough to be ambient noise rather
    // than speech.
    private fun applyNoiseGate(chunk: ByteArray): ByteArray {
        var sumSquares = 0.0
        var sampleCount = 0
        var i = 0
        while (i + 1 < chunk.size) {
            val sample = ((chunk[i + 1].toInt() shl 8) or (chunk[i].toInt() and 0xFF)).toShort()
            sumSquares += sample * sample.toDouble()
            sampleCount++
            i += 2
        }
        if (sampleCount == 0) return chunk
        val rms = kotlin.math.sqrt(sumSquares / sampleCount)
        return if (rms < NOISE_GATE_RMS_THRESHOLD) ByteArray(chunk.size) else chunk
    }

    fun playAudio(data: ByteArray) {
        if (!isCapturing || data.isEmpty()) return
        audioTrack?.write(data, 0, data.size)
    }

    fun stopPlayback() {
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.play()
    }

    fun stopCapture() {
        if (!isCapturing) return
        isCapturing = false

        captureThread?.join(1000)
        captureThread = null

        // Flush remaining accumulated audio
        synchronized(accumulateLock) {
            if (accumulatedData.size() > 0) {
                val chunk = accumulatedData.toByteArray()
                accumulatedData.reset()
                onAudioCaptured?.invoke(chunk)
            }
        }

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null

        if (routedToBluetoothMic) {
            try {
                val systemAudioManager =
                    context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                systemAudioManager.clearCommunicationDevice()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear communication device: ${e.message}")
            }
            routedToBluetoothMic = false
        }

        // Abandoning focus here (rather than leaving it held) is what signals the system that
        // whatever we ducked/paused for the glasses mic is free to resume playback on its own.
        focusRequest?.let { request ->
            try {
                val systemAudioManager =
                    context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                systemAudioManager.abandonAudioFocusRequest(request)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to abandon audio focus: ${e.message}")
            }
            focusRequest = null
        }

        Log.d(TAG, "Audio capture stopped")
    }
}
