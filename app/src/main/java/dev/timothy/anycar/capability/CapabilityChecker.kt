package dev.timothy.anycar.capability

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import dev.timothy.anycar.car.CompanionLink
import dev.timothy.anycar.car.Notifications
import dev.timothy.anycar.car.ObdNames
import dev.timothy.anycar.car.SupportPolicy
import dev.timothy.anycar.car.SupportStatus
import dev.timothy.anycar.data.CheckResult
import dev.timothy.anycar.data.CheckStatus
import dev.timothy.anycar.data.ProbeKeys
import dev.timothy.anycar.data.SettingsStore
import dev.timothy.anycar.data.VehicleProfile
import dev.timothy.anycar.data.VehicleRepository
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What this car and phone support (M3). Never blocks car mode, never nags, never retries in the background.
 * The wheel-button result is written only by its own test ([dev.timothy.anycar.car.WheelButton]).
 */
class CapabilityChecker(
    private val ctx: Context,
    private val vehicles: VehicleRepository,
    private val settings: SettingsStore,
    private val metaSpeechReady: suspend () -> Boolean,
) {
    /**
     * The quick probe on every car-mode entry: voice, auto-launch, car audio.
     * Returns the note of the first key that went from PASS to something else, to say once (M3.4).
     */
    suspend fun quick(v: VehicleProfile, connected: Boolean): String? = withContext(Dispatchers.IO) {
        val results = mapOf(
            ProbeKeys.VOICE to voice(),
            ProbeKeys.AUTO_LAUNCH to autoLaunch(v),
            ProbeKeys.AUDIO_ROUTE to audioRoute(v, connected),
        )
        vehicles.recordResults(v.id, results)
        results.entries.firstOrNull { (key, new) ->
            v.capabilityCheckResults[key]?.status == CheckStatus.PASS && new.status != CheckStatus.PASS
        }?.value?.note
    }

    /** Re-check from the report: every non-interactive probe. Never runs the wheel-button test. */
    suspend fun full(v: VehicleProfile, connected: Boolean) = withContext(Dispatchers.IO) {
        quick(v, connected)
        vehicles.recordResults(v.id, mapOf(ProbeKeys.OBD_LINK to obdLink(v), ProbeKeys.EXTENDED_PIDS to extendedData(v)))
    }

    private suspend fun voice(): CheckResult {
        val mic = granted(Manifest.permission.RECORD_AUDIO)
        val recognizer = SpeechRecognizer.isRecognitionAvailable(ctx) || metaSpeechReady()
        val tts = ctx.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0).isNotEmpty()
        return when {
            !recognizer -> result(CheckStatus.FAIL, "This phone has no speech recognizer. That's a phone limit; the buttons still work.")
            !tts -> result(CheckStatus.FAIL, "This phone has no text-to-speech engine. That's a phone limit; replies show on screen.")
            !mic -> result(CheckStatus.WARN, "Microphone permission is off.")
            else -> result(CheckStatus.PASS, "Speech recognition and voice output are ready.")
        }
    }

    private fun autoLaunch(v: VehicleProfile): CheckResult {
        val mac = v.btMac ?: return result(CheckStatus.FAIL, "This car has no Bluetooth, so car mode opens from the launcher.")
        val overlay = Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(ctx)
        val linked = Build.VERSION.SDK_INT < 31 || CompanionLink.isLinked(ctx, mac)
        return when {
            !linked -> result(CheckStatus.WARN, "Link your car so AnyCar can start in the background.")
            !overlay && !Notifications.canPost(ctx) -> result(CheckStatus.WARN, "Turn on notifications so AnyCar can tell you car mode is ready.")
            !overlay -> result(CheckStatus.WARN, "Car mode opens from a notification. Allow 'Display over other apps' to open it automatically.")
            else -> result(CheckStatus.PASS, "Car mode opens automatically when you start the car.")
        }
    }

    private suspend fun audioRoute(v: VehicleProfile, connected: Boolean): CheckResult {
        val mac = v.btMac ?: return result(
            CheckStatus.FAIL,
            "This car has no Bluetooth, so AnyCar speaks from the phone. A 12 V Bluetooth adapter can add car-speaker audio.",
        )
        if (settings.voiceViaCarCall.first()) {
            return result(CheckStatus.PASS, "AnyCar's voice plays through your car's call channel, whatever source the car is on.")
        }
        val cached = v.capabilityCheckResults[ProbeKeys.AUDIO_ROUTE]
        if (!connected) return cached ?: CheckResult(CheckStatus.NOT_TESTED, "Checked while the car is connected.", 0)
        return when (musicConnected(mac)) {
            true -> result(CheckStatus.PASS, "Your car plays AnyCar's voice and music over Bluetooth.")
            // Music audio connects a few seconds after the car does, so keep the last result rather than guess.
            false, null -> cached ?: CheckResult(CheckStatus.NOT_TESTED, "Checked while the car is connected.", 0)
        }
    }

    /** True if the car's music link (A2DP) is up. Null if it can't be checked. */
    @SuppressLint("MissingPermission")
    private suspend fun musicConnected(mac: String): Boolean? {
        if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) return null
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        val proxy = withTimeoutOrNull(2_000) {
            suspendCancellableCoroutine<BluetoothProfile> { cont ->
                adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) { if (cont.isActive) cont.resume(proxy) }
                    override fun onServiceDisconnected(profile: Int) = Unit
                }, BluetoothProfile.A2DP)
            }
        } ?: return null
        return try {
            proxy.connectedDevices.any { it.address.equals(mac, ignoreCase = true) }
        } finally {
            adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
        }
    }

    @SuppressLint("MissingPermission")
    private fun obdLink(v: VehicleProfile): CheckResult {
        when (SupportPolicy.status(v.year, Locale.getDefault().country)) {
            SupportStatus.NOT_SUPPORTED -> return result(CheckStatus.FAIL, "Not supported by this vehicle (pre-1996).")
            SupportStatus.UNKNOWN -> return result(CheckStatus.WARN, "Add your car's year so AnyCar can check support.")
            else -> Unit
        }
        // Names only. Never opens a connection.
        val adapter = if (Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            runCatching { ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty() }.getOrDefault(emptySet())
                .firstOrNull { !it.address.equals(v.btMac, ignoreCase = true) && ObdNames.looksLikeAdapter(it.name) }
        } else {
            null
        }
        return if (adapter != null) {
            result(CheckStatus.WARN, "Adapter found (${adapter.name}). Diagnostics arrive in a later version.")
        } else {
            result(CheckStatus.WARN, "Engine diagnostics need a small adapter that plugs in under the dashboard.")
        }
    }

    private fun extendedData(v: VehicleProfile): CheckResult =
        if (SupportPolicy.status(v.year, Locale.getDefault().country) == SupportStatus.NOT_SUPPORTED) {
            result(CheckStatus.FAIL, "Not supported by this vehicle (pre-1996).")
        } else {
            CheckResult(CheckStatus.NOT_TESTED, "Needs a diagnostics adapter.", 0)
        }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    private fun result(status: CheckStatus, note: String) = CheckResult(status, note, System.currentTimeMillis())
}
