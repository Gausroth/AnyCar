package dev.timothy.anycar.car

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import dev.timothy.anycar.graph
import kotlinx.coroutines.launch

/**
 * Manifest receiver for ACL_CONNECTED / ACL_DISCONNECTED. Those actions are exempt from the implicit-broadcast
 * limits, so this fires even when the app isn't running. Reading the address needs no permission.
 */
class CarConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        val mac = device.address.uppercase()
        val pending = goAsync()
        ctx.graph.appScope.launch {
            try {
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> CarConnectionHandler.onAclConnected(ctx.applicationContext, mac)
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> CarConnectionHandler.onAclDisconnected(ctx.applicationContext, mac)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
