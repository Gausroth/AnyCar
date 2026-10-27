package dev.timothy.anycar.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import dev.timothy.anycar.car.CarModeService
import dev.timothy.anycar.graph
import dev.timothy.anycar.ui.screens.CarScreen
import dev.timothy.anycar.ui.theme.CarTheme
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** The car screen (M4). Opens from Bluetooth auto-launch, the tap-to-open notification, or the launcher. */
class CarModeActivity : ComponentActivity() {
    private val session get() = graph.session

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterFrom(intent)

        // Close when the drive ends (Quit, or disconnect without "stay open").
        lifecycleScope.launch {
            session.activeVehicle.drop(1).collect { if (it == null) finish() }
        }
        setContent { CarTheme { CarScreen(onQuit = ::quit) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        enterFrom(intent)
    }

    private fun enterFrom(intent: Intent) {
        val id = intent.getLongExtra(EXTRA_VEHICLE_ID, -1)
        val manual = intent.getBooleanExtra(EXTRA_MANUAL, false)
        lifecycleScope.launch {
            val vehicle = graph.vehicles.get(id) ?: graph.vehicles.getDefault()
            if (vehicle == null) { finish(); return@launch }
            graph.settings.setLastVehicleId(vehicle.id)
            if (manual || session.activeVehicle.value?.id != vehicle.id) {
                session.enter(vehicle, connected = !manual && session.connected.value)
            }
            // Opened from the notification for a connected Bluetooth car whose service was blocked from
            // starting in the background: start it now, while the screen is visible (M1.4).
            if (!manual && vehicle.hasBluetooth && !session.serviceRunning.value) {
                lifecycle.withStarted {
                    runCatching {
                        ContextCompat.startForegroundService(this@CarModeActivity, CarModeService.enterIntent(this@CarModeActivity, vehicle))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        session.carScreenResumed = true
        session.carActivity = WeakReference(this)
    }

    override fun onDestroy() {
        if (session.carActivity?.get() === this) session.carActivity = null
        super.onDestroy()
    }

    override fun onPause() {
        session.carScreenResumed = false
        super.onPause()
    }

    private fun quit() {
        if (session.serviceRunning.value) startService(CarModeService.quitIntent(this)) else session.exit()
        finish()
    }

    companion object {
        private const val EXTRA_VEHICLE_ID = "vehicle_id"
        private const val EXTRA_MANUAL = "manual"

        fun intent(ctx: Context, vehicleId: Long, manual: Boolean = false): Intent =
            Intent(ctx, CarModeActivity::class.java)
                .putExtra(EXTRA_VEHICLE_ID, vehicleId)
                .putExtra(EXTRA_MANUAL, manual)
    }
}
