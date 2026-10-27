package dev.timothy.anycar.car

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import dev.timothy.anycar.graph
import dev.timothy.anycar.ui.CarModeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Matches a Bluetooth connect or disconnect to a car and enters or leaves car mode (M1.4, M1.5).
 * Takes a MAC instead of a BluetoothDevice so the debug screen can simulate a car (§6.6).
 */
object CarConnectionHandler {
    private const val TAG = "CarConnection"

    suspend fun onAclConnected(ctx: Context, mac: String) {
        val g = ctx.graph
        // Only btMac matches: OBD adapters and headsets never launch car mode (M1.7).
        val vehicle = g.vehicles.findByMac(mac) ?: run { Log.i(TAG, "Not a known car; ignoring"); return }
        Log.i(TAG, "Connected: ${vehicle.displayName}")

        withContext(Dispatchers.Main) {
            val alreadyActive = g.session.activeVehicle.value?.id == vehicle.id
            g.session.enter(vehicle, connected = true)
            val serviceStarted = try {
                ContextCompat.startForegroundService(ctx, CarModeService.enterIntent(ctx, vehicle))
                true
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException on 31+: the car isn't linked through CompanionDeviceManager.
                Log.w(TAG, "Service start blocked: ${e.message}")
                false
            }
            // A reconnect during the disconnect debounce just keeps the drive going.
            if (alreadyActive) return@withContext

            if (Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(ctx)) {
                ctx.startActivity(
                    CarModeActivity.intent(ctx, vehicle.id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            } else if (!serviceStarted) {
                Notifications.postTapToOpen(ctx, vehicle)
            }
        }
    }

    suspend fun onAclDisconnected(ctx: Context, mac: String) {
        val g = ctx.graph
        withContext(Dispatchers.Main) {
            val active = g.session.activeVehicle.value
            if (active?.btMac?.equals(mac, ignoreCase = true) != true) {
                Log.i(TAG, "Disconnect with no active drive for this car; ignoring")
                return@withContext
            }
            Log.i(TAG, "Disconnected: ${active.displayName}, service=${g.session.serviceRunning.value}")
            if (g.session.serviceRunning.value) {
                ctx.startService(CarModeService.disconnectIntent(ctx))
            } else {
                Notifications.cancelTapToOpen(ctx)
                g.session.setConnected(false)
            }
        }
    }
}
