package dev.timothy.anycar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug builds only (§6.6): runs a typed transcript as if heard, from adb.
 * adb shell am broadcast -n dev.timothy.anycar/.DebugSayReceiver --es text "what time is it"
 */
class DebugSayReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: return
        ctx.graph.session.voice.handleTranscript(text)
    }
}
