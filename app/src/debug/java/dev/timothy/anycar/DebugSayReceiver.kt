package dev.timothy.anycar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * Debug builds only (§6.6), driven from adb:
 * - Runs a typed transcript as if heard:
 *   adb shell am broadcast -n dev.timothy.anycar/.DebugSayReceiver --es text "what time is it"
 * - Speaker-to-mic loopback: taps Talk, then plays a WAV from the app's files folder into the phone's own mic:
 *   adb shell am broadcast -n dev.timothy.anycar/.DebugSayReceiver --es play command.wav
 */
class DebugSayReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val voice = ctx.graph.session.voice
        intent.getStringExtra("text")?.let { voice.handleTranscript(it); return }
        val file = File(ctx.filesDir, intent.getStringExtra("play") ?: return)
        voice.startListening()
        Handler(Looper.getMainLooper()).postDelayed({
            // No audio focus request, so AnyCar's own listening doesn't pause it.
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).build())
                setDataSource(file.path)
                setOnCompletionListener { it.release() }
                prepare()
                start()
            }
        }, 1_200)
    }
}
