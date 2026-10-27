package dev.timothy.anycar

import dev.timothy.anycar.ai.MetaApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class MetaApiTest {
    // Trimmed from a real muse-spark-1.3 tool-calling response.
    private val resp = Json.parseToJsonElement(
        """
        {"output":[
          {"type":"reasoning","summary":[]},
          {"type":"message","role":"assistant","content":[{"type":"output_text","text":"Got it."}]},
          {"type":"function_call","name":"send_text","arguments":"{\"contact\":\"mom\",\"message\":\"I'm running late\"}"},
          {"type":"function_call","name":"navigate","arguments":"{\"destination\":\"nearest gas station\"}"},
          {"type":"function_call","name":"go_home","arguments":""}
        ]}
        """,
    ).jsonObject

    @Test fun outputText() = assertEquals("Got it.", MetaApi.outputText(resp))

    @Test fun functionCallsInOrder() {
        val calls = MetaApi.functionCalls(resp)
        assertEquals(listOf("send_text", "navigate", "go_home"), calls.map { it.first })
        assertEquals("I'm running late", calls[0].second["message"]!!.jsonPrimitive.content)
        assertEquals(0, calls[2].second.size)
    }
}
