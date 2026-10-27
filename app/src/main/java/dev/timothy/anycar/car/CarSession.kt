package dev.timothy.anycar.car

import android.app.Activity
import android.content.Context
import android.util.Log
import dev.timothy.anycar.actions.SpokenResult
import dev.timothy.anycar.data.SettingsStore
import dev.timothy.anycar.data.VehicleProfile
import dev.timothy.anycar.voice.MetaSpeech
import dev.timothy.anycar.voice.TtsEngine
import dev.timothy.anycar.voice.VoiceController
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Process-wide drive state: the active vehicle, whether its Bluetooth is connected, and the voice loop.
 * Voice objects are created lazily and must first be touched on the main thread; [exit] must run there too.
 */
class CarSession(
    private val app: Context,
    settings: SettingsStore,
    scope: CoroutineScope,
    private val metaSpeech: MetaSpeech,
) {
    private val _activeVehicle = MutableStateFlow<VehicleProfile?>(null)
    val activeVehicle: StateFlow<VehicleProfile?> = _activeVehicle.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Set by CarModeService in onCreate/onDestroy. */
    val serviceRunning = MutableStateFlow(false)

    @Volatile var carScreenResumed = false

    /** The car screen while it exists, for unlocking the keyguard before a handoff (M2.7). */
    @Volatile var carActivity: WeakReference<Activity>? = null

    /** Set by AppGraph: turns a transcript into a result. */
    var handler: suspend (String, (String) -> Unit) -> SpokenResult = { _, _ -> SpokenResult("Not ready yet.") }

    /** Set by AppGraph: clears follow-ups when the drive ends. */
    var onExit: () -> Unit = {}

    @Volatile private var cloudAllowed = false
    @Volatile private var ttsEnabled = true
    @Volatile private var metaSpeechOn = false
    @Volatile private var carChannelOn = true

    private val focus = AudioFocus(app)
    private val ttsLazy = lazy { TtsEngine(app, focus).also { it.enabled = ttsEnabled } }
    val tts: TtsEngine by ttsLazy
    private val voiceLazy = lazy {
        VoiceController(
            app, tts, focus, { cloudAllowed }, { metaSpeechOn }, metaSpeech, ScoLink(app),
            useCarChannel = { carChannelOn && _connected.value },
        ) { text, progress -> handler(text, progress) }
    }
    val voice: VoiceController by voiceLazy

    init {
        scope.launch { settings.cloudSpeechAllowed.collect { cloudAllowed = it } }
        scope.launch { settings.metaSpeechEnabled.collect { metaSpeechOn = it } }
        scope.launch { settings.voiceViaCarCall.collect { carChannelOn = it } }
        scope.launch {
            settings.ttsEnabled.collect { enabled ->
                ttsEnabled = enabled
                if (ttsLazy.isInitialized()) tts.enabled = enabled
            }
        }
    }

    fun enter(vehicle: VehicleProfile, connected: Boolean) {
        _activeVehicle.value = vehicle
        _connected.value = connected
    }

    fun setConnected(connected: Boolean) { _connected.value = connected }

    /** Ends the drive. Car-mode screens observe [activeVehicle] and close when it becomes null. */
    fun exit() {
        Log.i("CarSession", "Drive ended")
        _activeVehicle.value = null
        _connected.value = false
        onExit()
        if (voiceLazy.isInitialized()) voice.endSession()
    }
}
