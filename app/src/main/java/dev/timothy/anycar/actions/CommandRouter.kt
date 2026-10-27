package dev.timothy.anycar.actions

import android.util.Log
import dev.timothy.anycar.ai.MetaError
import dev.timothy.anycar.ai.MuseAgent
import dev.timothy.anycar.command.Command
import dev.timothy.anycar.command.CommandParser

/**
 * Decides who handles an utterance:
 * 1. A waiting follow-up ("What should the text say?").
 * 2. The keyword grammar, including chains like "text Mom I'm late and navigate to Walmart", all on the phone.
 * 3. Muse Spark, only for what the grammar can't parse and only with Meta AI turned on (M12.6: known
 *    commands never pass through a model).
 */
class CommandRouter(
    private val executor: CommandExecutor,
    private val agent: MuseAgent,
    private val metaAiOn: () -> Boolean,
) {
    suspend fun handle(transcript: String, progress: (String) -> Unit): SpokenResult {
        executor.fillPending(transcript)?.let { return it }
        CommandParser.parseChain(transcript)?.let { return executor.run(it) }

        val command = CommandParser.parse(transcript)
        if (command !is Command.Unknown || !metaAiOn() || !agent.configured) return executor.run(listOf(command))

        progress("Thinking…")
        return try {
            when (val reply = agent.handle(transcript)) {
                is MuseAgent.Reply.Commands -> executor.run(reply.commands)
                is MuseAgent.Reply.Answer -> SpokenResult(reply.text)
            }
        } catch (e: MetaError) {
            Log.w(TAG, "Muse Spark failed: ${e.message}")
            // Says why, then the help line, so the drive never dead-ends.
            SpokenResult("${failSoftLine(e)} ${CommandExecutor.HELP}")
        }
    }

    private fun failSoftLine(e: MetaError) = when (e) {
        is MetaError.Offline -> "No connection, so I'm using basic commands."
        is MetaError.RateLimited -> "AI limit reached, so I'm using basic commands."
        is MetaError.Timeout -> "The AI took too long, so I'm using basic commands."
        is MetaError.InvalidKey -> "The Meta AI key stopped working."
        is MetaError.Unavailable -> "The AI couldn't answer that, so I'm using basic commands."
    }

    private companion object { const val TAG = "CommandRouter" }
}
