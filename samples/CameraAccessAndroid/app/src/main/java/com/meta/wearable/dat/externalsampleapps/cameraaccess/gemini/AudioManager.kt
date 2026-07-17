package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import java.io.ByteArrayOutputStream

class AudioManager {
    companion object {
        private const val TAG = "AudioManager"
        private const val MIN_SEND_BYTES = 3200 // 100ms at 16kHz mono Int16 = 1600 frames * 2 bytes
    }

    var onAudioCaptured: ((ByteArray) -> Unit)? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var captureThread: Thread? = null
    @Volatile
    private var isCapturing = false
    private val accumulatedData = ByteArrayOutputStream()
    private val accumulateLock = Any()

    @SuppressLint("MissingPermission")
    fun startCapture(inputSampleRate: Int, outputSampleRate: Int) {
        if (isCapturing) return

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
                            val chunk = accumulatedData.toByteArray()
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

        Log.d(TAG, "Audio capture stopped")
    }
}
