package dev.timothy.anycar.ai

import dev.timothy.anycar.command.Command
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Muse Spark as the agent for utterances the keyword grammar can't parse, such as
 * "let Mom know I'm late and get me to the nearest gas station". The model only chooses tools; every
 * tool maps back to a normal [Command], so contacts are still resolved on the phone and the driver
 * still taps Call or Send. Only the transcript is sent.
 */
class MuseAgent(private val api: MetaApi) {
    sealed interface Reply {
        data class Commands(val commands: List<Command>) : Reply
        data class Answer(val text: String) : Reply
    }

    val configured get() = api.configured

    suspend fun handle(transcript: String): Reply {
        val resp = api.respond(
            buildJsonObject {
                put("model", MetaApi.SPARK_MODEL)
                putJsonObject("reasoning") { put("effort", "minimal") }
                put("instructions", INSTRUCTIONS)
                put("input", transcript)
                putJsonArray("tools") { TOOLS.forEach { add(it) } }
            },
        )
        val commands = MetaApi.functionCalls(resp).mapNotNull { (name, args) -> toCommand(name, args) }
        if (commands.isNotEmpty()) return Reply.Commands(commands)
        return Reply.Answer(MetaApi.outputText(resp).ifBlank { "Sorry, I don't know." })
    }

    private fun toCommand(name: String, args: JsonObject): Command? {
        fun arg(k: String) = args[k]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        return when (name) {
            "call" -> Command.Call(arg("contact"))
            "send_text" -> Command.Text(arg("contact"), arg("message"))
            "navigate" -> Command.Navigate(arg("destination"))
            "go_home" -> Command.GoHome
            "media" -> when (arg("action")) {
                "next" -> Command.MediaNext
                "previous" -> Command.MediaPrevious
                else -> Command.MediaToggle
            }
            "tell_time" -> Command.Time
            "scan_dashboard" -> Command.Diagnose
            else -> null
        }
    }

    private companion object {
        const val INSTRUCTIONS =
            "You are AnyCar, a voice assistant for someone driving. Carry out every request in the utterance with the " +
                "tools, in the order spoken. Pass contact names exactly as said, such as \"Mom\". Write text messages in " +
                "the driver's own words, in first person. If nothing needs a tool, answer in under 30 words of plain " +
                "spoken language, with no lists or markdown. Never suggest the driver look at or type on the phone."

        fun fn(name: String, description: String, vararg params: Pair<String, JsonObject>) = buildJsonObject {
            put("type", "function")
            put("name", name)
            put("description", description)
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") { params.forEach { (k, v) -> put(k, v) } }
                putJsonArray("required") { params.forEach { add(it.first) } }
            }
        }

        fun str(description: String) = buildJsonObject { put("type", "string"); put("description", description) }

        val TOOLS = listOf(
            fn("call", "Open the dialer for a contact or phone number. The driver taps Call.", "contact" to str("Contact name as spoken, or a phone number")),
            fn(
                "send_text", "Open the messaging app pre-filled. The driver taps Send.",
                "contact" to str("Contact name as spoken, or a phone number"), "message" to str("The message text"),
            ),
            fn("navigate", "Start Google Maps turn-by-turn navigation.", "destination" to str("A place, business, or address")),
            fn("go_home", "Navigate to the Home saved in Google Maps."),
            fn(
                "media", "Control whatever music or podcast is playing.",
                "action" to buildJsonObject {
                    put("type", "string")
                    putJsonArray("enum") { add("play_pause"); add("next"); add("previous") }
                },
            ),
            fn("tell_time", "Say the current time."),
            // Only for "scan it" requests: questions about what a light means get a spoken answer instead.
            fn("scan_dashboard", "The driver asks to photograph or scan the dashboard lights."),
        )
    }
}
