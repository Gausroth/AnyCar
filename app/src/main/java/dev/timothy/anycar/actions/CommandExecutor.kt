package dev.timothy.anycar.actions

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.view.KeyEvent
import dev.timothy.anycar.command.Command
import dev.timothy.anycar.command.CommandParser
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Runs commands (M2.6, M5–M9). One command or several from one utterance go through [run]:
 * 1. Contacts are resolved first, on the phone. A choice between contacts stops here and resumes after the tap.
 * 2. Instant steps run (music, time), then Maps, then at most one dialer or messaging handoff on top,
 *    so the driver taps Send or Call and drops straight back into navigation.
 * Main thread only.
 */
class CommandExecutor(
    private val ctx: Context,
    private val launcher: ExternalLauncher,
    private val contacts: ContactResolver,
    private val metaAiOn: () -> Boolean,
) {
    private sealed interface Step {
        data class Media(val keyCode: Int, val spoken: String) : Step
        data class Nav(val destination: String) : Step
        data object Home : Step
        data class Dial(val name: String, val number: String) : Step
        data class Sms(val name: String, val number: String, val body: String) : Step
    }

    private sealed interface Pending {
        val expiresAt: Long
        data class TextBody(val name: String, val number: String, override val expiresAt: Long) : Pending
        data class Destination(override val expiresAt: Long) : Pending
    }

    private var pending: Pending? = null

    /** Clears follow-ups. Called on "cancel" and when car mode exits. */
    fun clear() { pending = null }

    /** A waiting follow-up ("What should the text say?") takes the next utterance, unless it's "cancel". */
    suspend fun fillPending(transcript: String): SpokenResult? {
        val p = pending ?: return null
        pending = null
        if (SystemClock.elapsedRealtime() > p.expiresAt) return null
        if (CommandParser.parse(transcript) == Command.Cancel) return SpokenResult(OKAY)
        return when (p) {
            is Pending.TextBody -> run(listOf(Command.Text(p.name, transcript.trim())), mapOf(0 to ContactMatch(p.name, p.number, "")))
            is Pending.Destination -> run(listOf(Command.Navigate(transcript.trim().trimEnd('.', '!', '?'))))
        }
    }

    /** [picks] holds contacts already chosen, by command index, so a resumed chain doesn't ask again. */
    suspend fun run(commands: List<Command>, picks: Map<Int, ContactMatch> = emptyMap()): SpokenResult {
        val said = mutableListOf<String>()
        val steps = mutableListOf<Step>()

        for ((i, c) in commands.withIndex()) {
            when (c) {
                Command.Cancel -> { clear(); said += OKAY }
                Command.Help, is Command.Unknown -> said += HELP
                Command.Weather -> said += "Weather is coming in a later update."
                Command.Time -> said += "It's ${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))}."
                Command.Diagnose -> said += if (metaAiOn()) {
                    "When you're parked, tap Scan a light and point the camera at the dashboard."
                } else {
                    "Engine diagnostics need a small adapter that plugs in under the dashboard. That arrives in a later version."
                }
                Command.MediaToggle -> steps += Step.Media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, OKAY)
                Command.MediaNext -> steps += Step.Media(KeyEvent.KEYCODE_MEDIA_NEXT, "Next.")
                Command.MediaPrevious -> steps += Step.Media(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous.")
                Command.GoHome -> steps += Step.Home
                is Command.Navigate -> if (c.destination.isBlank()) {
                    pending = Pending.Destination(expiry())
                    said += "Where to?"
                } else {
                    steps += Step.Nav(c.destination)
                }
                is Command.Call, is Command.Text -> {
                    val query = if (c is Command.Call) c.query else (c as Command.Text).query
                    val body = (c as? Command.Text)?.body
                    val match = picks[i] ?: CommandParser.phoneDigits(query)?.let { ContactMatch(CommandParser.spokenDigits(it), it, "") }
                    if (match == null) {
                        if (query.isBlank()) { said += "Who should I ${if (body == null) "call" else "text"}?"; continue }
                        when (val found = contacts.find(query)) {
                            Lookup.NoPermission -> return SpokenResult(
                                "Contacts permission is off, so I can't look up names.",
                                choices = listOf(
                                    UiChoice("Allow contacts", needsContactsPermission = true) { run(commands, picks) },
                                    UiChoice("Open settings") { openAppSettings() },
                                ),
                            )
                            is Lookup.Found -> when (found.matches.size) {
                                0 -> said += "I couldn't find '$query' in your contacts."
                                1 -> return run(commands, picks + (i to found.matches[0]))
                                // Never pick a number silently: calling the wrong person from a car is a real hazard.
                                else -> return SpokenResult(
                                    "I found several. Which one?",
                                    choices = found.matches.take(3).map { m ->
                                        UiChoice("${m.name} · ${m.type} (${format(m.number)})") { run(commands, picks + (i to m)) }
                                    },
                                )
                            }
                        }
                        continue
                    }
                    when {
                        body == null -> steps += Step.Dial(match.name, match.number)
                        body.isBlank() -> {
                            pending = Pending.TextBody(match.name, match.number, expiry())
                            said += "What should the text say?"
                        }
                        else -> steps += Step.Sms(match.name, match.number, body)
                    }
                }
            }
        }

        val spoken = said.toMutableList()
        val shown = said.toMutableList()
        val handoffs = steps.filter { it is Step.Dial || it is Step.Sms }
        val ordered = steps.filterIsInstance<Step.Media>() + steps.filter { it is Step.Nav || it == Step.Home } + handoffs.take(1)
        for (step in ordered) {
            val r = execute(step)
            spoken += r.text
            shown += r.display
        }
        handoffs.drop(1).forEach { extra ->
            val name = if (extra is Step.Dial) extra.name else (extra as Step.Sms).name
            spoken += "I can open one call or text at a time. Ask again for $name."
            shown += spoken.last()
        }
        if (spoken.isEmpty()) return SpokenResult(HELP)
        return SpokenResult(spoken.joinToString(" "), display = shown.joinToString("\n"))
    }

    private fun execute(step: Step): SpokenResult = when (step) {
        is Step.Media -> {
            val audio = ctx.getSystemService(AudioManager::class.java)
            val now = SystemClock.uptimeMillis()
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, step.keyCode, 0))
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, step.keyCode, 0))
            SpokenResult(step.spoken)
        }
        is Step.Nav -> {
            val nav = mapsIntent(step.destination)
            when (launcher.launch(nav, "Navigate to ${step.destination}")) {
                LaunchResult.LAUNCHED -> SpokenResult("Navigating to ${step.destination}.")
                LaunchResult.NEEDS_TAP -> SpokenResult("Tap the notification to start navigation.")
                LaunchResult.NO_APP -> {
                    // No Google Maps: a plain map search in whatever map app the driver picks.
                    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(step.destination)))
                    if (launcher.launch(geo, "Open ${step.destination}") == LaunchResult.NO_APP) SpokenResult("No map app found on this phone.")
                    else SpokenResult("Opening ${step.destination} in your map app.")
                }
            }
        }
        Step.Home -> when (launcher.launch(mapsIntent("Home"), "Navigate home")) {
            LaunchResult.LAUNCHED -> SpokenResult("Navigating home.")
            LaunchResult.NEEDS_TAP -> SpokenResult("Tap the notification to start navigation.")
            LaunchResult.NO_APP -> SpokenResult("Go home needs Google Maps.")
        }
        is Step.Dial -> {
            val dial = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", step.number.trim(), null))
            when (launcher.launch(dial, "Call ${step.name}")) {
                LaunchResult.LAUNCHED -> SpokenResult("Calling ${step.name}. Tap call.", display = "Calling ${step.name}…\n${format(step.number)}")
                LaunchResult.NEEDS_TAP -> SpokenResult("Tap the notification to call ${step.name}.")
                LaunchResult.NO_APP -> SpokenResult("No phone app found.")
            }
        }
        is Step.Sms -> {
            val sms = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", step.number.trim(), null)).putExtra("sms_body", step.body)
            when (launcher.launch(sms, "Text ${step.name}")) {
                // Speaks the name only, never the body.
                LaunchResult.LAUNCHED -> SpokenResult(
                    "Ready to send to ${step.name}. Tap send.",
                    display = "To: ${step.name} (${format(step.number)})\n\"${step.body}\"\nTap Send in your messaging app.",
                )
                LaunchResult.NEEDS_TAP -> SpokenResult("Tap the notification to text ${step.name}.")
                LaunchResult.NO_APP -> SpokenResult("No messaging app found.")
            }
        }
    }

    private fun mapsIntent(destination: String) =
        Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(destination))).setPackage(MAPS)

    private fun openAppSettings(): SpokenResult {
        val settings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
        launcher.launch(settings, "Open settings")
        return SpokenResult("Turn on Contacts, then come back.")
    }

    private fun format(number: String) = PhoneNumberUtils.formatNumber(number, Locale.getDefault().country) ?: number

    private fun expiry() = SystemClock.elapsedRealtime() + 60_000

    companion object {
        const val HELP = "I can call, text, play music, navigate, or tell you the time. Say 'help' for more."
        const val OKAY = "Okay."
        private const val MAPS = "com.google.android.apps.maps"
    }
}
