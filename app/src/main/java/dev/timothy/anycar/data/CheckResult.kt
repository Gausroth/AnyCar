package dev.timothy.anycar.data

import kotlinx.serialization.Serializable

@Serializable enum class CheckStatus { PASS, WARN, FAIL, NOT_TESTED }

@Serializable data class CheckResult(
    val status: CheckStatus,
    val note: String,      // one-line, user-facing, honest; TTS and screen use the same string
    val checkedAt: Long,   // epoch millis; 0 when NOT_TESTED
)

/** The only valid keys in VehicleProfile.capabilityCheckResults (§7.3). */
object ProbeKeys {
    const val VOICE = "voice"
    const val AUTO_LAUNCH = "auto_launch"
    const val AUDIO_ROUTE = "audio_route"
    const val WHEEL_BUTTON = "wheel_button"
    const val OBD_LINK = "obd_link"
    const val EXTENDED_PIDS = "extended_pids"
}
