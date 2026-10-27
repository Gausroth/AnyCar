package dev.timothy.anycar.voice

import android.content.Context
import android.os.SystemClock
import android.speech.SpeechRecognizer
import android.util.Log
import dev.timothy.anycar.BuildConfig
import dev.timothy.anycar.actions.SpokenResult
import dev.timothy.anycar.actions.UiChoice
import dev.timothy.anycar.ai.MetaError
import dev.timothy.anycar.car.AudioFocus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class VoicePhase { IDLE, LISTENING, PROCESSING, SPEAKING }

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val message: String = "Tap Talk and say a command.",
    val heard: String = "",
    val path: RecognizerPath? = null,
    val choices: List<UiChoice> = emptyList(),
)

/**
 * The single voice pipeline (M2.2): IDLE → LISTENING → PROCESSING → SPEAKING → IDLE.
 * Every trigger calls [startListening]. Main thread only.
 */
class VoiceController(
    ctx: Context,
    private val tts: TtsEngine,
    private val focus: AudioFocus,
    private val cloudAllowed: () -> Boolean,
    private val metaSpeechOn: () -> Boolean,
    private val metaSpeech: MetaSpeech,
    /** Turns a transcript into a result. The second argument updates the status line, such as "Thinking…". */
    private val handler: suspend (String, (String) -> Unit) -> SpokenResult,
) {
    private val engine = VoiceEngine(ctx.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var lastTrigger = 0L
    private var missesInARow = 0
    private var retriedSilently = false
    private var work: Job? = null

    fun refreshPath() = _state.update { it.copy(path = currentPath()) }

    private fun currentPath() = if (metaSpeechOn() && metaSpeech.configured) RecognizerPath.META else engine.path(cloudAllowed())

    fun startListening() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTrigger < 300) return  // two triggers within 300 ms collapse to one session
        lastTrigger = now
        when (_state.value.phase) {
            VoicePhase.SPEAKING -> tts.stop()
            VoicePhase.IDLE -> Unit
            else -> { Log.d(TAG, "startListening ignored in ${_state.value.phase}"); return }
        }
        tts.stop()
        refreshPath()
        when (_state.value.path) {
            RecognizerPath.NEEDS_CLOUD_CONSENT -> { show("Voice needs Google or Meta speech on this phone. The buttons still work."); return }
            RecognizerPath.UNAVAILABLE -> { show("Voice input unavailable on this phone; the buttons still work."); return }
            else -> Unit
        }
        retriedSilently = false
        listen()
    }

    private fun listen() {
        focus.forListening()
        val meta = _state.value.path == RecognizerPath.META
        _state.update { it.copy(phase = VoicePhase.LISTENING, message = if (meta) "Listening… (Meta speech)" else "Listening…", heard = "", choices = emptyList()) }
        work = scope.launch {
            delay(150)
            if (meta) listenMeta() else if (!engine.start(cloudAllowed(), listener)) {
                focus.release()
                show("Voice input unavailable on this phone; the buttons still work.")
            }
        }
    }

    private suspend fun listenMeta() {
        val text = try {
            metaSpeech.listen()
        } catch (e: MetaSpeech.NoSpeech) {
            listener.onError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT); return
        } catch (e: MetaError.Offline) {
            listener.onError(SpeechRecognizer.ERROR_NETWORK); return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Meta speech failed: ${e.message}")
            focus.release()
            say("Meta speech didn't work. Try again.")
            return
        }
        if (text.isBlank()) listener.onError(SpeechRecognizer.ERROR_NO_MATCH) else listener.onResult(text)
    }

    /** Speaks and shows [text], then returns to IDLE. Used for announcements such as the greeting. */
    fun say(text: String, onDone: () -> Unit = {}) = say(SpokenResult(text), onDone)

    /** Speaks a result, shows its display text and any choice buttons, then returns to IDLE. */
    fun say(result: SpokenResult, onDone: () -> Unit = {}) {
        // Debug builds only: replies can hold message text, which release builds never log.
        if (BuildConfig.DEBUG) Log.i(TAG, "Reply: ${result.text} | choices=${result.choices.map { it.label }}")
        focus.release()
        _state.update { it.copy(phase = VoicePhase.SPEAKING, message = result.display, choices = result.choices) }
        tts.speak(result.text) {
            _state.update { s -> if (s.phase == VoicePhase.SPEAKING) s.copy(phase = VoicePhase.IDLE) else s }
            onDone()
        }
    }

    /** Like [say], but returns once speech has finished (or after [timeoutMs], so a stuck engine can't hang a caller). */
    suspend fun sayAndWait(text: String, timeoutMs: Long = 4_000) {
        val done = CompletableDeferred<Unit>()
        say(text) { done.complete(Unit) }
        withTimeoutOrNull(timeoutMs) { done.await() }
    }

    /** Runs a transcript through the handler. Also the debug seam (§6.6) for typed transcripts. */
    fun handleTranscript(text: String) {
        tts.stop()
        _state.update { it.copy(phase = VoicePhase.PROCESSING, heard = text, message = "Working…", choices = emptyList()) }
        work = scope.launch { say(runSafely { handler(text) { msg -> _state.update { s -> s.copy(message = msg) } } }) }
    }

    /** A tapped choice button, such as one of several contacts. */
    fun pick(choice: UiChoice) {
        tts.stop()
        _state.update { it.copy(phase = VoicePhase.PROCESSING, message = "Working…", choices = emptyList()) }
        work = scope.launch { say(runSafely { choice.run() }) }
    }

    private suspend fun runSafely(block: suspend () -> SpokenResult): SpokenResult = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Command failed", e)
        SpokenResult("Something went wrong. Try again.")
    }

    fun cancel() {
        work?.cancel()
        engine.cancel()
        tts.stop()
        focus.release()
        _state.update { it.copy(phase = VoicePhase.IDLE) }
    }

    /** Ends the session's voice resources; the controller can start again later. */
    fun endSession() {
        cancel()
        engine.destroy()
        _state.value = VoiceUiState()
    }

    private fun show(text: String) = _state.update { it.copy(phase = VoicePhase.IDLE, message = text) }

    private val listener = object : VoiceEngine.Listener {
        override fun onPartial(text: String) = _state.update { it.copy(heard = text) }

        override fun onResult(text: String) {
            missesInARow = 0
            focus.release()
            handleTranscript(text)
        }

        override fun onError(code: Int) {
            focus.release()
            when (code) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    missesInARow++
                    say(if (missesInARow >= 3) "It's noisy. Try the big buttons." else "Didn't catch that. Try again.")
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    say("No connection. Try again, or use the buttons.")
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    engine.reset()
                    if (!retriedSilently) { retriedSilently = true; listen() } else say("Something went wrong. Try again.")
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> show("AnyCar needs the microphone. Tap Allow microphone.")
                else -> say("Something went wrong. Try again.")
            }
        }
    }

    private companion object { const val TAG = "VoiceController" }
}
