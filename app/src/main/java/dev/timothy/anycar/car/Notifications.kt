package dev.timothy.anycar.car

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.timothy.anycar.R
import dev.timothy.anycar.data.VehicleProfile
import dev.timothy.anycar.ui.CarModeActivity

object Notifications {
    const val CAR_MODE_ID = 1
    private const val TAP_TO_OPEN_ID = 2
    private const val ACTION_ID = 3
    private const val CH_CAR_MODE = "car_mode"
    private const val CH_CAR_CONNECTED = "car_connected"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_CAR_MODE, "Car mode running", NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CAR_CONNECTED, "Car connected", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    /** The foreground-service notification: "AnyCar · connected to SYNC", with Open and Quit (M1.4). */
    fun carMode(ctx: Context, vehicleId: Long, btName: String): Notification =
        NotificationCompat.Builder(ctx, CH_CAR_MODE)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle("AnyCar · connected to $btName")
            .setContentText("Tap to open car mode")
            .setOngoing(true)
            .setContentIntent(openIntent(ctx, vehicleId))
            .addAction(0, "Open car mode", openIntent(ctx, vehicleId))
            .addAction(
                0, "Quit car mode",
                PendingIntent.getService(
                    ctx, 1, CarModeService.quitIntent(ctx),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

    /** Only for when the service couldn't start and the screen can't open by itself (M1.4). */
    fun postTapToOpen(ctx: Context, vehicle: VehicleProfile) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_CAR_CONNECTED)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle("${vehicle.btName} connected")
            .setContentText("Tap to open car mode")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, vehicle.id))
            .build()
        NotificationManagerCompat.from(ctx).notify(TAP_TO_OPEN_ID, n)
    }

    fun cancelTapToOpen(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(TAP_TO_OPEN_ID)

    /** "Call Mom", "Navigate to Walmart": finishes a handoff Android wouldn't open from the background (M2.7). */
    fun postAction(ctx: Context, intent: Intent, label: String) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_CAR_CONNECTED)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle(label)
            .setContentText("Tap to finish")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(ctx, ACTION_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        NotificationManagerCompat.from(ctx).notify(ACTION_ID, n)
    }

    fun canPost(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    private fun openIntent(ctx: Context, vehicleId: Long): PendingIntent = PendingIntent.getActivity(
        ctx, vehicleId.toInt(), CarModeActivity.intent(ctx, vehicleId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
