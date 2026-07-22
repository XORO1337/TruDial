package com.example.telecom

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * Captures microphone audio with [AudioRecord] and transcribes it in fixed windows via Groq's
 * Whisper endpoint (OpenAI-compatible). This is the fallback for devices where the platform
 * [android.speech.SpeechRecognizer] refuses to produce results during a call (ERROR_NO_MATCH loop).
 *
 * It records the MIC, so during a call it relies on speakerphone being on to pick up the remote
 * party. Requires RECORD_AUDIO (already granted for screening).
 */
class GroqTranscriber(
    private val apiKey: String,
    private val model: String = "whisper-large-v3-turbo",
    private val windowMs: Long = 5000,
    // Windows quieter than this (RMS of 16-bit samples) are treated as silence and skipped, so
    // Whisper never hallucinates phrases like "Thank you." from a muted mic.
    private val silenceRmsThreshold: Double = 250.0
) {
    companion object {
        private const val TAG = "TruDialCall"
        private const val SAMPLE_RATE = 16000
        private const val ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
    }

    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var job: Job? = null
    @Volatile private var recording = false

    @SuppressLint("MissingPermission") // RECORD_AUDIO is declared and granted for call screening.
    fun start(scope: CoroutineScope, onSegment: (String) -> Unit) {
        if (recording) return
        recording = true
        job = scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = maxOf(minBuf, SAMPLE_RATE) // ~0.5s worth of 16-bit samples
            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize
                )
            } catch (e: Exception) {
                Log.e(TAG, "GroqTranscriber: failed to create AudioRecord", e)
                return@launch
            }

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "GroqTranscriber: AudioRecord not initialized (state=${recorder.state})")
                recorder.release()
                return@launch
            }

            recorder.startRecording()
            Log.i(TAG, "GroqTranscriber: recording started (model=$model, window=${windowMs}ms)")

            val buffer = ByteArray(bufSize)
            val window = ByteArrayOutputStream()
            val bytesPerWindow = (SAMPLE_RATE * 2 * windowMs / 1000).toInt() // 16-bit mono

            try {
                while (recording && isActive) {
                    val n = recorder.read(buffer, 0, buffer.size)
                    if (n > 0) {
                        window.write(buffer, 0, n)
                        if (window.size() >= bytesPerWindow) {
                            val pcm = window.toByteArray()
                            window.reset()
                            val level = rms(pcm)
                            if (level < silenceRmsThreshold) {
                                // Mic captured silence (common when the OS mutes the mic during a
                                // call). Skip it — otherwise Whisper invents text from the silence.
                                Log.d(TAG, "GroqTranscriber: window RMS=%.0f < %.0f — skipping (silence)".format(level, silenceRmsThreshold))
                            } else {
                                Log.d(TAG, "GroqTranscriber: window RMS=%.0f — transcribing".format(level))
                                // Transcribe off the read loop so recording never stalls on the network.
                                scope.launch(Dispatchers.IO) {
                                    val text = transcribe(pcm)
                                    if (!text.isNullOrBlank()) {
                                        withContext(Dispatchers.Main) { onSegment(text) }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "GroqTranscriber: recording loop error", e)
            } finally {
                try { recorder.stop() } catch (_: Exception) {}
                recorder.release()
                Log.i(TAG, "GroqTranscriber: recording stopped")
            }
        }
    }

    fun stop() {
        recording = false
        job?.cancel()
        job = null
    }

    private fun transcribe(pcm: ByteArray): String? {
        return try {
            val wav = pcmToWav(pcm)
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("model", model)
                .addFormDataPart("response_format", "json")
                .addFormDataPart("temperature", "0")
                .addFormDataPart("file", "audio.wav", wav.toRequestBody("audio/wav".toMediaType()))
                .build()
            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()
            client.newCall(request).execute().use { resp ->
                val respBody = resp.body?.string()
                if (!resp.isSuccessful) {
                    Log.e(TAG, "GroqTranscriber: STT failed code=${resp.code} body=$respBody")
                    return null
                }
                val text = JSONObject(respBody ?: "{}").optString("text", "").trim()
                Log.i(TAG, "GroqTranscriber: segment → \"$text\"")
                text
            }
        } catch (e: Exception) {
            Log.e(TAG, "GroqTranscriber: transcribe error", e)
            null
        }
    }

    /** Root-mean-square amplitude of 16-bit little-endian PCM, used to detect silence. */
    private fun rms(pcm: ByteArray): Double {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < pcm.size) {
            val sample = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8) // signed 16-bit LE
            sum += sample.toDouble() * sample.toDouble()
            count++
            i += 2
        }
        return if (count > 0) sqrt(sum / count) else 0.0
    }

    /** Wrap raw 16 kHz mono 16-bit PCM in a minimal WAV container Whisper can read. */
    private fun pcmToWav(pcm: ByteArray): ByteArray {
        val channels = 1
        val bits = 16
        val byteRate = SAMPLE_RATE * channels * bits / 8
        val blockAlign = channels * bits / 8
        val dataLen = pcm.size
        val out = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray(Charsets.US_ASCII))
        out.putInt(36 + dataLen)
        out.put("WAVE".toByteArray(Charsets.US_ASCII))
        out.put("fmt ".toByteArray(Charsets.US_ASCII))
        out.putInt(16)                     // PCM fmt chunk size
        out.putShort(1)                    // audio format = PCM
        out.putShort(channels.toShort())
        out.putInt(SAMPLE_RATE)
        out.putInt(byteRate)
        out.putShort(blockAlign.toShort())
        out.putShort(bits.toShort())
        out.put("data".toByteArray(Charsets.US_ASCII))
        out.putInt(dataLen)
        out.put(pcm)
        return out.array()
    }
}
