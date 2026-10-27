package dev.timothy.anycar.actions

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import dev.timothy.anycar.car.CarSession
import dev.timothy.anycar.car.Notifications

enum class LaunchResult { LAUNCHED, NEEDS_TAP, NO_APP }

/**
 * Opens the dialer, messaging, or Maps (M2.7). Android blocks activity starts from the background, so
 * without a visible car screen or the overlay permission this posts a tap-to-finish notification instead.
 * Main thread only.
 */
class ExternalLauncher(private val ctx: Context, private val session: CarSession) {
    fun launch(intent: Intent, label: String): LaunchResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(ctx.packageManager) == null) return LaunchResult.NO_APP

        val allowed = session.carScreenResumed || Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(ctx)
        if (!allowed) {
            Notifications.postAction(ctx, intent, label)
            return LaunchResult.NEEDS_TAP
        }

        // Over the lock screen: ask Android to unlock first, then open the app.
        val keyguard = ctx.getSystemService(KeyguardManager::class.java)
        val activity = session.carActivity?.get()
        if (keyguard.isKeyguardLocked && session.carScreenResumed && activity != null) {
            keyguard.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { start(intent) }
            })
            return LaunchResult.LAUNCHED
        }
        return if (start(intent)) LaunchResult.LAUNCHED else LaunchResult.NO_APP
    }

    private fun start(intent: Intent): Boolean = try {
        ctx.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
