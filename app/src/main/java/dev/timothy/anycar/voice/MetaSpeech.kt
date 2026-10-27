package dev.timothy.anycar.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import dev.timothy.anycar.ai.MetaApi
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
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
    suspend fun listen(onRecorded: () -> Unit = {}): String {
        val pcm = withContext(Dispatchers.IO) { record() }
        onRecorded()
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
        val start = SystemClock.elapsedRealtime()
        val opening = mutableListOf<Double>()
        var floor = 0.0
        var peak = 0.0
        var loud = 0
        var quietMs = 0
        var speaking = false
        try {
            rec.startRecording()
            check(rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Mic is busy" }
            // Deadlines are wall-clock, so a mic that returns nothing can't hold "Listening…" forever.
            while (true) {
                coroutineContext.ensureActive()
                val elapsed = SystemClock.elapsedRealtime() - start
                if (!speaking && elapsed > NO_SPEECH_MS) throw NoSpeech()
                if (elapsed > MAX_MS) break
                // Non-blocking, so a silenced mic can't park this thread past the deadlines.
                val n = rec.read(frame, 0, FRAME, AudioRecord.READ_NON_BLOCKING)
                check(n >= 0) { "AudioRecord read error $n" }
                if (n == 0) { Thread.sleep(5); continue }
                bytes.clear()
                for (i in 0 until n) bytes.putShort(frame[i])
                out.write(bytes.array(), 0, n * 2)

                val rms = sqrt(frame.take(n).sumOf { it.toDouble() * it } / n)
                peak = maxOf(peak, rms)
                // Noise floor: the median of the first 200 ms, capped so speaking right away can't set it,
                // then it follows the quiet frames (fan, road) slowly.
                if (opening.size < 10) {
                    opening += rms
                    if (opening.size == 10) floor = minOf(opening.sorted()[5], FLOOR_CAP)
                    continue
                }
                val threshold = maxOf(floor * 2.5, MIN_THRESHOLD)
                if (rms > threshold) {
                    loud++
                    quietMs = 0
                } else {
                    loud = 0
                    quietMs += n * 1000 / RATE
                    floor = minOf(floor * 0.95 + rms * 0.05, FLOOR_CAP)
                }
                if (!speaking && loud >= 3) speaking = true
                if (speaking && quietMs >= END_SILENCE_MS) break
            }
        } finally {
            Log.i(TAG, "Recorded ${SystemClock.elapsedRealtime() - start} ms, floor=${floor.toInt()}, peak=${peak.toInt()}, speech=$speaking")
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
        const val TAG = "MetaSpeech"
        const val RATE = 16_000
        const val FRAME = 320              // 20 ms
        const val END_SILENCE_MS = 900     // a pause this long ends the command
        const val NO_SPEECH_MS = 7_000L
        const val MAX_MS = 12_000L
        const val MIN_THRESHOLD = 300.0
        const val FLOOR_CAP = 1_500.0
    }
}
