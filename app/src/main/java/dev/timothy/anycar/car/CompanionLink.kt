package dev.timothy.anycar.car

import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

/**
 * Links a car through CompanionDeviceManager (M1.3). On Android 12+ that's what lets the Bluetooth receiver
 * start CarModeService from the background.
 */
object CompanionLink {
    /** Shows the system dialog for [mac]. [onLinked] gets the association id (0 below API 33, after RESULT_OK). */
    fun associate(
        activity: ComponentActivity,
        mac: String,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        onLinked: (Int) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val cdm = activity.getSystemService(CompanionDeviceManager::class.java)
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(mac.uppercase()).build())
            .setSingleDevice(true)
            .build()
        if (Build.VERSION.SDK_INT >= 33) {
            cdm.associate(request, activity.mainExecutor, object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(sender: IntentSender) =
                    launcher.launch(IntentSenderRequest.Builder(sender).build())
                override fun onAssociationCreated(info: AssociationInfo) = onLinked(info.id)
                override fun onFailure(error: CharSequence?) = onFailure(error?.toString() ?: "Couldn't link the car.")
            })
        } else {
            @Suppress("DEPRECATION")
            cdm.associate(request, object : CompanionDeviceManager.Callback() {
                @Deprecated("Deprecated in Java")
                override fun onDeviceFound(sender: IntentSender) =
                    launcher.launch(IntentSenderRequest.Builder(sender).build())
                override fun onFailure(error: CharSequence?) = onFailure(error?.toString() ?: "Couldn't link the car.")
            }, null)
        }
    }

    /** The source of truth; the stored cdmAssociationId is only a cache. */
    fun isLinked(ctx: Context, mac: String): Boolean {
        val cdm = ctx.getSystemService(CompanionDeviceManager::class.java)
        return if (Build.VERSION.SDK_INT >= 33) {
            cdm.myAssociations.any { it.deviceMacAddress?.toString().equals(mac, ignoreCase = true) }
        } else {
            @Suppress("DEPRECATION")
            cdm.associations.any { it.equals(mac, ignoreCase = true) }
        }
    }
}
