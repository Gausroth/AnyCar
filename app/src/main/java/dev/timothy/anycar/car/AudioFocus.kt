package dev.timothy.anycar.car

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/** Ducks music while AnyCar speaks and pauses it while listening (M2.4). */
class AudioFocus(ctx: Context) {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private var current: AudioFocusRequest? = null

    /** Most players pause on a transient gain, so the driver isn't drowned out. */
    fun forListening(): Boolean = request(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)

    /** Music keeps playing, quieter. Returns false when focus is denied (for example during a call). */
    fun forSpeaking(): Boolean = request(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)

    fun release() {
        current?.let { audio.abandonAudioFocusRequest(it) }
        current = null
    }

    private fun request(gain: Int): Boolean {
        release()
        val req = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .build()
        val granted = audio.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) current = req
        return granted
    }
}
