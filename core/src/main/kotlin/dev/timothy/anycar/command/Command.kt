package dev.timothy.anycar.command

/** One spoken command (M2.5). Arguments keep the transcript's original case and punctuation. */
sealed interface Command {
    data object Help : Command
    data object Cancel : Command
    data class Call(val query: String) : Command
    data class Text(val query: String, val body: String) : Command
    data object GoHome : Command
    data class Navigate(val destination: String) : Command
    data object MediaToggle : Command
    data object MediaNext : Command
    data object MediaPrevious : Command
    data object Weather : Command
    data object Time : Command
    data object Diagnose : Command
    data class Unknown(val raw: String) : Command
}
