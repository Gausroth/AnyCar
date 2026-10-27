package dev.timothy.anycar.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import dev.timothy.anycar.ai.MetaApi
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Muse Voice Transcribe as the speech path: records one command on the phone, stops after a pause,
 * and sends that clip only. Audio is never kept. Used when the driver turns on Meta speech.
 */
class MetaSpeech(private val api: MetaApi) {
    class NoSpeech : Exception("no speech")

    val configured get() = api.configured

    /** Records until the driver stops talking, then returns the transcript. Callers check RECORD_AUDIO. */
    @SuppressLint("MissingPermission")
    suspend fun listen(): String {
        val pcm = withContext(Dispatchers.IO) { record() }
        return api.transcribe(wav(pcm))
    }

    @SuppressLint("MissingPermission")
    private suspend fun record(): ByteArray {
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, FRAME * 2 * 4),
        )
        check(rec.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord unavailable" }
        val out = ByteArrayOutputStream()
        val frame = ShortArray(FRAME)
        val bytes = ByteBuffer.allocate(FRAME * 2).order(ByteOrder.LITTLE_ENDIAN)
        var frames = 0
        var noise = 0.0
        var loud = 0
        var quiet = 0
        var speaking = false
        try {
            rec.startRecording()
            while (true) {
                kotlin.coroutines.coroutineContext.ensureActive()
                val n = rec.read(frame, 0, FRAME)
                if (n <= 0) continue
                bytes.clear()
                for (i in 0 until n) bytes.putShort(frame[i])
                out.write(bytes.array(), 0, n * 2)
                frames++

                // A simple energy gate: the first 300 ms set the noise floor (fan, road), speech is well above it.
                val rms = sqrt(frame.take(n).sumOf { it.toDouble() * it } / n)
                if (frames <= 15) { noise += rms / 15; continue }
                val threshold = maxOf(noise * 2.5, 350.0)
                if (rms > threshold) { loud++; quiet = 0 } else { quiet++; loud = 0 }
                if (!speaking && loud >= 3) speaking = true
                if (speaking && quiet >= END_SILENCE_FRAMES) break
                if (!speaking && frames >= NO_SPEECH_FRAMES) throw NoSpeech()
                if (frames >= MAX_FRAMES) break
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
        return out.toByteArray()
    }

    private fun wav(pcm: ByteArray): ByteArray = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
        put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(pcm.size); put(pcm)
    }.array()

    private companion object {
        const val RATE = 16_000
        const val FRAME = 320                  // 20 ms
        const val END_SILENCE_FRAMES = 45      // 0.9 s of quiet ends the command
        const val NO_SPEECH_FRAMES = 300       // 6 s with no speech
        const val MAX_FRAMES = 600             // 12 s cap
    }
}
