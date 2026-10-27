package dev.timothy.anycar.car

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.timothy.anycar.data.CheckResult
import dev.timothy.anycar.data.CheckStatus
import dev.timothy.anycar.data.VehicleProfile
import dev.timothy.anycar.data.VehicleRepository
import dev.timothy.anycar.ui.VoiceCommandActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The steering-wheel voice button (M11). Cars that support it send the hands-free voice command, and
 * Android opens whichever app handles ACTION_VOICE_COMMAND. AnyCar's handler stays disabled unless a car has
 * passed the test, so earbuds and other cars keep their usual assistant.
 */
object WheelButton {
    const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    const val TEST_SECONDS = 10

    @Volatile private var probe: CompletableDeferred<Unit>? = null

    sealed interface Handler {
        data object AnyCar : Handler
        data object Chooser : Handler
        data object None : Handler
        data class Other(val pkg: String, val label: String) : Handler
    }

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.packageManager.setComponentEnabledSetting(
            ComponentName(ctx, VoiceCommandActivity::class.java),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** Enabled while any car has the wheel button turned on. */
    fun sync(ctx: Context, vehicles: List<VehicleProfile>) = setEnabled(ctx, vehicles.any { it.wheelButtonEnabled })

    /** Who gets the voice button right now (M11.4). */
    fun currentHandler(ctx: Context): Handler {
        val ri = ctx.packageManager.resolveActivity(Intent(Intent.ACTION_VOICE_COMMAND), PackageManager.MATCH_DEFAULT_ONLY)
            ?: return Handler.None
        val pkg = ri.activityInfo.packageName
        return when (pkg) {
            ctx.packageName -> Handler.AnyCar
            "android" -> Handler.Chooser
            else -> Handler.Other(pkg, ri.loadLabel(ctx.packageManager).toString())
        }
    }

    /** Called by [VoiceCommandActivity]. True if a test was waiting for this press. */
    fun onPress(): Boolean = probe?.complete(Unit) == true

    /**
     * The 10-second test (M11.5). The driver presses the wheel button while the countdown runs.
     * Never retried automatically.
     */
    suspend fun test(ctx: Context, v: VehicleProfile, vehicles: VehicleRepository): CheckResult {
        setEnabled(ctx, true)
        val handler = currentHandler(ctx)
        if (handler is Handler.Other) {
            val result = CheckResult(
                CheckStatus.WARN,
                "Your phone sends the voice button to ${handler.label}. Clear its defaults (Settings → Apps → " +
                    "${handler.label} → Open by default), then test again.",
                System.currentTimeMillis(),
            )
            vehicles.setWheelButton(v.id, result, supported = false, enabled = false)
            restore(ctx, vehicles)
            return result
        }
        val waiting = CompletableDeferred<Unit>()
        probe = waiting
        val pressed = try {
            withTimeoutOrNull(TEST_SECONDS * 1000L) { waiting.await() } != null
        } finally {
            probe = null
        }
        val result = if (pressed) {
            CheckResult(CheckStatus.PASS, "Your car sends the voice button to AnyCar.", System.currentTimeMillis())
        } else {
            CheckResult(
                CheckStatus.FAIL,
                "No signal from the wheel button. That's normal on older cars; auto-launch and tap-to-talk still work.",
                System.currentTimeMillis(),
            )
        }
        vehicles.setWheelButton(v.id, result, supported = pressed, enabled = pressed)
        restore(ctx, vehicles)
        return result
    }

    private suspend fun restore(ctx: Context, vehicles: VehicleRepository) {
        val all = vehicles.all()
        sync(ctx, all)
    }
}
