package dev.timothy.anycar.command

/**
 * The keyword grammar (M2.5). Rules run in order and the first match wins.
 * Matching uses a normalized copy; arguments are cut from the original transcript.
 */
object CommandParser {
    // Recognizers add sentence punctuation; it would break a contact lookup or a Maps search.
    private val END_PUNCT = charArrayOf('.', ',', '!', '?')
    private val HELP = setOf("help", "what can you say", "what can you do")
    private val CANCEL = setOf("cancel", "never mind", "nevermind", "forget it")
    private val CALL = listOf("call ", "dial ", "phone ")
    private val TEXT = listOf("send a text to ", "send a message to ", "text ", "message ")
    private val HOME = setOf("go home", "take me home", "navigate home", "navigate to home")
    private val NAVIGATE = listOf("navigate to ", "directions to ", "go to ", "take me to ")
    private val TOGGLE = setOf("play", "pause", "resume", "play music", "pause music", "resume music")
    private val NEXT = setOf("next", "skip", "next song", "skip song")
    private val PREVIOUS = setOf("previous", "back", "previous song", "go back")
    private val WEATHER = setOf("weather", "weather today", "what's the weather", "whats the weather", "how's the weather", "is it raining")
    private val TIME = setOf("time", "what time is it", "what's the time", "whats the time", "current time")

    // Hackathon edition: the dashboard-light phrases join rule 11, since Diagnose now offers the photo scan.
    private val DIAGNOSE = setOf(
        "check engine", "check engine light", "read codes", "diagnostics",
        "warning light", "what's that light", "whats that light", "what is that light", "what does that light mean",
        "scan dashboard", "scan my dashboard",
    )

    fun normalize(s: String): String =
        s.lowercase().replace('’', '\'').replace(Regex("[.,!?]"), "").replace(Regex("\\s+"), " ").trim()

    fun parse(transcript: String): Command {
        val n = normalize(transcript)
        return when {
            n in HELP -> Command.Help
            n in CANCEL -> Command.Cancel
            prefix(n, CALL) != null -> Command.Call(rest(transcript, prefix(n, CALL)!!).trimEnd(*END_PUNCT))
            prefix(n, TEXT) != null -> text(rest(transcript, prefix(n, TEXT)!!))
            n in HOME -> Command.GoHome
            prefix(n, NAVIGATE) != null -> Command.Navigate(rest(transcript, prefix(n, NAVIGATE)!!).trimEnd(*END_PUNCT))
            n == "navigate" || n == "navigate to" -> Command.Navigate("")
            n in TOGGLE -> Command.MediaToggle
            n in NEXT -> Command.MediaNext
            n in PREVIOUS -> Command.MediaPrevious
            n in WEATHER || n.startsWith("what's the weather") -> Command.Weather
            n in TIME -> Command.Time
            n in DIAGNOSE -> Command.Diagnose
            else -> Command.Unknown(transcript.trim())
        }
    }

    /**
     * Splits "text Mom I'm late and navigate to Walmart" into its commands, entirely on the phone.
     * Returns null unless every part is a known command, so "bread and milk" stays inside a text body.
     */
    fun parseChain(transcript: String): List<Command>? {
        val parts = transcript.split(Regex("(?i),?\\s+(?:and then|and also|then|and)\\s+")).filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val commands = parts.map(::parse)
        return commands.takeIf { list -> list.none { it is Command.Unknown || it is Command.Cancel || it is Command.Help } }
    }

    /**
     * Digits of a spoken phone number (7–15 digits once separators are removed), or null if it isn't one.
     * Three-digit numbers count too, so "call 911" dials instead of searching contacts for "911".
     */
    fun phoneDigits(s: String): String? {
        val stripped = s.trim().replace(Regex("[\\s.()-]"), "")
        val digits = stripped.removePrefix("+")
        return if ((digits.length in 7..15 || digits.length == 3) && digits.all(Char::isDigit)) stripped else null
    }

    /** "5075550143" → "507 555 0143", for speaking a number in groups. */
    fun spokenDigits(number: String): String {
        val d = number.filter(Char::isDigit)
        return when (d.length) {
            10 -> "${d.take(3)} ${d.substring(3, 6)} ${d.takeLast(4)}"
            7 -> "${d.take(3)} ${d.takeLast(4)}"
            else -> d.chunked(3).joinToString(" ")
        }
    }

    private fun text(rest: String): Command {
        val words = rest.trim().split(Regex("\\s+"), limit = 2)
        val query = words.getOrElse(0) { "" }.trimEnd(*END_PUNCT, ':')
        var body = words.getOrElse(1) { "" }.trim()
        for (lead in listOf("saying ", "that ")) {
            if (body.lowercase().startsWith(lead)) body = body.substring(lead.length).trim()
        }
        return Command.Text(query, body)
    }

    private fun prefix(n: String, prefixes: List<String>): String? = prefixes.firstOrNull { n.startsWith(it) }

    /** Cuts the matched prefix's words off the original transcript, so the argument keeps its case and punctuation. */
    private fun rest(original: String, prefix: String): String {
        var words = prefix.trim().split(" ").size
        var s = original.trim()
        while (words-- > 0) s = s.substringAfter(' ', "").trimStart()
        return s.trim()
    }
}
