package dev.timothy.anycar.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** META is Muse Voice Transcribe ([MetaSpeech]); the others are Android's SpeechRecognizer. */
enum class RecognizerPath { ON_DEVICE, CLOUD, META, NEEDS_CLOUD_CONSENT, UNAVAILABLE }

/** Wraps SpeechRecognizer (M2.3). Main thread only. On-device recognition is always preferred (§8.4). */
class VoiceEngine(private val ctx: Context) {
    interface Listener {
        fun onPartial(text: String)
        fun onResult(text: String)
        fun onError(code: Int)
    }

    private var recognizer: SpeechRecognizer? = null
    private var recognizerPath: RecognizerPath? = null
    private var sessions = 0

    fun path(cloudAllowed: Boolean): RecognizerPath = when {
        Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) -> RecognizerPath.ON_DEVICE
        !SpeechRecognizer.isRecognitionAvailable(ctx) -> RecognizerPath.UNAVAILABLE
        cloudAllowed -> RecognizerPath.CLOUD
        else -> RecognizerPath.NEEDS_CLOUD_CONSENT
    }

    /** Starts one listening session. Returns false if this phone can't listen on the allowed path. */
    fun start(cloudAllowed: Boolean, listener: Listener): Boolean {
        val path = path(cloudAllowed)
        if (path != RecognizerPath.ON_DEVICE && path != RecognizerPath.CLOUD) return false
        // Some OEM recognizers leak, so recreate every 20 sessions, and whenever the path changes.
        if (recognizer == null || recognizerPath != path || sessions >= 20) recreate(path)
        sessions++
        recognizer!!.setRecognitionListener(adapter(listener))
        recognizer!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        })
        return true
    }

    fun cancel() { recognizer?.cancel() }

    /** Drops the recognizer so the next start builds a fresh one (used after BUSY/CLIENT errors). */
    fun reset() { destroy() }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        recognizerPath = null
        sessions = 0
    }

    private fun recreate(path: RecognizerPath) {
        recognizer?.destroy()
        recognizer = if (path == RecognizerPath.ON_DEVICE && Build.VERSION.SDK_INT >= 31) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        } else {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        }
        recognizerPath = path
        sessions = 0
    }

    private fun adapter(l: Listener) = object : RecognitionListener {
        override fun onPartialResults(b: Bundle?) { best(b)?.let(l::onPartial) }
        override fun onResults(b: Bundle?) {
            val text = best(b)
            if (text.isNullOrBlank()) l.onError(SpeechRecognizer.ERROR_NO_MATCH) else l.onResult(text)
        }
        override fun onError(error: Int) = l.onError(error)
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun best(b: Bundle?): String? = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}
