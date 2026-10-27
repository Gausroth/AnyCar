package dev.timothy.anycar.car

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import java.util.concurrent.Executor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The car's hands-free (phone-call) channel, used so AnyCar's voice plays through the car speakers
 * whatever source the head unit is on. Many older head units, like SYNC Gen 1, only play Bluetooth media
 * when the driver has picked "Bluetooth Audio", but switch to the phone channel by themselves.
 * Same link as M10.5's car mic, opened only while AnyCar speaks. Music pauses while it's open. API 31+.
 */
class ScoLink(ctx: Context) {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val main: Executor = ctx.mainExecutor

    val isOpen get() = Build.VERSION.SDK_INT >= 31 && audio.communicationDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO

    /** Opens the link and waits until audio can flow. False if there's no hands-free car or it doesn't come up. */
    suspend fun open(timeoutMs: Long = 4_000): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        if (isOpen) return true
        // A phone or VoIP call owns the channel; never touch it then.
        if (audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION) return false
        val sco = audio.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO } ?: return false
        val up = CompletableDeferred<Unit>()
        val listener = AudioManager.OnCommunicationDeviceChangedListener { d ->
            if (d?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) up.complete(Unit)
        }
        audio.addOnCommunicationDeviceChangedListener(main, listener)
        try {
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            if (!audio.setCommunicationDevice(sco)) { close(); return false }
            if (withTimeoutOrNull(timeoutMs) { up.await() } == null && !isOpen) {
                Log.w(TAG, "Car phone channel didn't open")
                close()
                return false
            }
            // The head unit needs a moment to switch its speakers over, or the first words are cut off.
            delay(SWITCH_DELAY_MS)
            return true
        } catch (e: CancellationException) {
            close()  // the driver tapped Talk mid-open: don't leave the call channel up
            throw e
        } finally {
            audio.removeOnCommunicationDeviceChangedListener(listener)
        }
    }

    fun close() {
        if (Build.VERSION.SDK_INT < 31) return
        audio.clearCommunicationDevice()
        audio.mode = AudioManager.MODE_NORMAL
    }

    private companion object {
        const val TAG = "ScoLink"
        const val SWITCH_DELAY_MS = 800L
    }
}
