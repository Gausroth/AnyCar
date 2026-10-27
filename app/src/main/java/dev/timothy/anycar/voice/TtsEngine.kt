package dev.timothy.anycar.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dev.timothy.anycar.car.AudioFocus
import java.util.Locale
import java.util.UUID

/**
 * Wraps TextToSpeech (M2.4). Uses a local voice only, so spoken names and messages never go to a cloud voice.
 * When speech isn't possible the caller still gets onDone, and the screen text carries the message.
 */
class TtsEngine(ctx: Context, private val focus: AudioFocus) {
    private val main = Handler(Looper.getMainLooper())
    private val callbacks = mutableMapOf<String, () -> Unit>()
    private var ready = false
    private var failed = false
    private var pending: Pair<String, () -> Unit>? = null

    var enabled = true

    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext) { status -> main.post { onInit(status) } }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS || !pickLanguage()) {
            failed = true
            Log.w(TAG, "Voice output unavailable")
        } else {
            tts.voices?.firstOrNull { it.locale == tts.voice?.locale && !it.isNetworkConnectionRequired }?.let { tts.voice = it }
            if (tts.voice?.isNetworkConnectionRequired == true) {
                failed = true
                Log.w(TAG, "No local voice; text only")
            }
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String) = Unit
                override fun onDone(id: String) { finish(id) }
                @Deprecated("Deprecated in Java") override fun onError(id: String) { finish(id) }
                override fun onStop(id: String, interrupted: Boolean) { finish(id) }
            })
            ready = true
        }
        pending?.let { (text, done) -> pending = null; speak(text, done) }
    }

    private fun pickLanguage(): Boolean {
        val ok = { r: Int -> r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED }
        return ok(tts.setLanguage(Locale.US)) || ok(tts.setLanguage(Locale.getDefault()))
    }

    /** Speaks [text] and calls [onDone] on the main thread when it ends, fails, or is skipped. */
    fun speak(text: String, onDone: () -> Unit = {}) {
        when {
            !enabled || failed -> onDone()
            !ready -> pending = text to onDone
            !focus.forSpeaking() -> onDone()  // focus denied, e.g. during a call: show text only
            else -> {
                val id = UUID.randomUUID().toString()
                callbacks[id] = onDone
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            }
        }
    }

    fun stop() {
        pending = null
        if (ready) tts.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private fun finish(id: String) = main.post {
        focus.release()
        callbacks.remove(id)?.invoke()
    }

    private companion object { const val TAG = "TtsEngine" }
}
