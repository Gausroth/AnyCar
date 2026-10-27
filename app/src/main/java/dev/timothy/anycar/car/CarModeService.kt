package dev.timothy.anycar.car

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.timothy.anycar.data.VehicleProfile
import dev.timothy.anycar.graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service (type connectedDevice) that owns a drive while a Bluetooth car is connected (M1.4, M1.5).
 * It never runs for cars without Bluetooth or for manual sessions with no car connected.
 */
class CarModeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var debounce: Job? = null
    private var greetedVehicleId: Long? = null

    private val btState = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            // Bluetooth turned off counts as a disconnect, with no debounce.
            if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) endDrive(spoken = true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        graph.session.serviceRunning.value = true
        ContextCompat.registerReceiver(
            this, btState, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Command: ${intent?.action}")
        when (intent?.action) {
            ACTION_ENTER -> enter(intent)
            ACTION_DISCONNECT -> if (debounce == null) {
                debounce = scope.launch { delay(DEBOUNCE_MS); endDrive(spoken = true) }
            }
            ACTION_QUIT -> endDrive(spoken = false, force = true)
            else -> stopSelf()  // restarted by the system with no drive to own
        }
        return START_NOT_STICKY
    }

    private fun enter(intent: Intent) {
        val vehicleId = intent.getLongExtra(EXTRA_VEHICLE_ID, -1)
        val btName = intent.getStringExtra(EXTRA_BT_NAME) ?: "your car"
        // startForeground first, inside the 5 s window.
        try {
            ServiceCompat.startForeground(
                this, Notifications.CAR_MODE_ID, Notifications.carMode(this, vehicleId, btName),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
        } catch (e: SecurityException) {
            // API 34+ without BLUETOOTH_CONNECT: car mode still works from the screen alone.
            Log.w(TAG, "Can't run as connectedDevice: ${e.message}")
            stopSelf()
            return
        }
        // A reconnect inside the debounce window cancels it: no greeting, no relaunch (M1.5).
        val wasDebouncing = debounce != null
        debounce?.cancel()
        debounce = null
        graph.session.setConnected(true)
        Notifications.cancelTapToOpen(this)
        if (!wasDebouncing && greetedVehicleId != vehicleId) {
            greetedVehicleId = vehicleId
            graph.session.voice.say("Connected to $btName")
        }
    }

    private fun endDrive(spoken: Boolean, force: Boolean = false) {
        Log.i(TAG, "Ending drive (spoken=$spoken, force=$force)")
        debounce?.cancel()
        debounce = null
        scope.launch {
            val session = graph.session
            if (spoken) session.voice.sayAndWait("Disconnected")
            session.setConnected(false)
            if (force || !graph.settings.stayOpenOnDisconnect.first()) session.exit()
            ServiceCompat.stopForeground(this@CarModeService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        unregisterReceiver(btState)
        graph.session.serviceRunning.value = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CarModeService"
        const val DEBOUNCE_MS = 8_000L
        private const val ACTION_ENTER = "dev.timothy.anycar.ENTER"
        private const val ACTION_DISCONNECT = "dev.timothy.anycar.DISCONNECT"
        private const val ACTION_QUIT = "dev.timothy.anycar.QUIT"
        private const val EXTRA_VEHICLE_ID = "vehicle_id"
        private const val EXTRA_BT_NAME = "bt_name"

        fun enterIntent(ctx: Context, v: VehicleProfile): Intent = Intent(ctx, CarModeService::class.java)
            .setAction(ACTION_ENTER).putExtra(EXTRA_VEHICLE_ID, v.id).putExtra(EXTRA_BT_NAME, v.btName)

        fun disconnectIntent(ctx: Context): Intent = Intent(ctx, CarModeService::class.java).setAction(ACTION_DISCONNECT)

        fun quitIntent(ctx: Context): Intent = Intent(ctx, CarModeService::class.java).setAction(ACTION_QUIT)
    }
}
