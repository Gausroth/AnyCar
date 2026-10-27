package dev.timothy.anycar.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import dev.timothy.anycar.car.WheelButton
import dev.timothy.anycar.graph

/**
 * Receives the steering-wheel voice button (M11.4). Transparent, no history, not in recents, and disabled in
 * the manifest until a car passes the wheel-button test. Presses from outside car mode, such as earbuds,
 * go to the usual assistant.
 */
class VoiceCommandActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle()
        finish()
    }

    private fun handle() {
        if (WheelButton.onPress()) return  // the wheel-button test was waiting for this press

        val session = graph.session
        val v = session.activeVehicle.value
        if (v != null && v.wheelButtonSupported && v.wheelButtonEnabled) {
            startActivity(CarModeActivity.intent(this, v.id, resume = true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            session.voice.startListening()
            return
        }

        // Not in car mode with a linked car: forward to the usual assistant, by package, so it can't loop back here.
        try {
            startActivity(Intent(Intent.ACTION_VOICE_COMMAND).setPackage(WheelButton.GOOGLE_APP).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "AnyCar handles the voice button in car mode only.", Toast.LENGTH_SHORT).show()
        }
    }
}
