package com.meta.wearable.dat.externalsampleapps.cameraaccess.stream

import android.graphics.Bitmap
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import java.io.File
import java.util.concurrent.Executors

// Encodes a sequence of already-decoded Bitmap frames (the same frames already shown on screen)
// into an MP4 file. The DAT SDK has no recording-to-glasses-storage capability -- confirmed by
// decompiling both the pinned (0.4.0) and latest (0.8.0) versions, neither exposes anything
// beyond capturePhoto()/start()/stop()/state/videoStream/errorStream -- so this is the only way
// to save the camera stream: draw each frame onto a MediaCodec encoder's input Surface as it
// arrives, muxing the encoded output into an MP4 via MediaMuxer.
class VideoRecorder {
    companion object {
        private const val TAG = "VideoRecorder"
        private const val MIME_TYPE = "video/avc"
        private const val I_FRAME_INTERVAL_SECONDS = 1
        private const val TIMEOUT_US = 10_000L
        private const val BITS_PER_PIXEL = 4
    }

    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var muxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var muxerStarted = false
    private var outputFile: File? = null
    private var targetWidth = 0
    private var targetHeight = 0

    // All encoder/muxer calls happen on this single thread so drawing a frame and draining
    // encoder output never race with each other, and recordFrame() never blocks the caller
    // (mirrors the sendExecutor/videoSendExecutor separation used for the AI's own network
    // sends elsewhere in this app).
    private val executor = Executors.newSingleThreadExecutor()
    private val bufferInfo = MediaCodec.BufferInfo()

    fun start(file: File, width: Int, height: Int, frameRate: Int) {
        executor.execute {
            try {
                targetWidth = width
                targetHeight = height
                outputFile = file
                val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, width * height * BITS_PER_PIXEL)
                    setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
                }
                val enc = MediaCodec.createEncoderByType(MIME_TYPE)
                enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = enc.createInputSurface()
                enc.start()
                codec = enc
                muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxerStarted = false
                videoTrackIndex = -1
                Log.d(TAG, "Recording started: ${file.absolutePath} (${width}x$height @ ${frameRate}fps)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording: ${e.message}")
            }
        }
    }

    fun recordFrame(bitmap: Bitmap) {
        executor.execute {
            val surface = inputSurface ?: return@execute
            try {
                val canvas = surface.lockCanvas(null)
                if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
                    canvas.drawBitmap(bitmap, null, Rect(0, 0, targetWidth, targetHeight), null)
                } else {
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                }
                surface.unlockCanvasAndPost(canvas)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to draw frame: ${e.message}")
                return@execute
            }
            drainEncoder(endOfStream = false)
        }
    }

    // onStopped runs on the internal encoder thread -- callers should hop back to their own
    // thread/scope before touching UI state.
    fun stop(onStopped: (File?) -> Unit) {
        executor.execute {
            val file = outputFile
            try {
                codec?.signalEndOfInputStream()
                drainEncoder(endOfStream = true)
            } catch (e: Exception) {
                Log.e(TAG, "Error finishing recording: ${e.message}")
            }
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing codec: ${e.message}")
            }
            try {
                if (muxerStarted) muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing muxer: ${e.message}")
            }
            codec = null
            inputSurface = null
            muxer = null
            Log.d(TAG, "Recording stopped: ${file?.absolutePath}")
            onStopped(file)
        }
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val enc = codec ?: return
        val mux = muxer ?: return
        while (true) {
            val outputBufferId = enc.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                    // else keep polling until end-of-stream flag arrives
                }
                outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) {
                        Log.w(TAG, "Encoder output format changed more than once, ignoring")
                    } else {
                        videoTrackIndex = mux.addTrack(enc.outputFormat)
                        mux.start()
                        muxerStarted = true
                    }
                }
                outputBufferId >= 0 -> {
                    val encodedData = enc.getOutputBuffer(outputBufferId)
                    if (encodedData == null) {
                        enc.releaseOutputBuffer(outputBufferId, false)
                    } else {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size != 0 && muxerStarted) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            mux.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        }
                        val isEos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        enc.releaseOutputBuffer(outputBufferId, false)
                        if (isEos) return
                    }
                }
            }
        }
    }
}
