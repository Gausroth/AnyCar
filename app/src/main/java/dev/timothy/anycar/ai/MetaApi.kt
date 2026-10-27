package dev.timothy.anycar.ai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed class MetaError(message: String) : Exception(message) {
    class Offline : MetaError("offline")
    class Timeout : MetaError("timeout")
    class RateLimited : MetaError("rate limited")
    class InvalidKey : MetaError("invalid key")
    class Unavailable(code: Int) : MetaError("HTTP $code")
}

/**
 * The Meta Model API (Muse Spark responses and Muse Voice Transcribe), over plain HTTPS.
 * Never logs request bodies, transcripts, images, or the key.
 */
class MetaApi(private val key: String) {
    val configured get() = key.isNotBlank()

    /** POST /responses. Returns the parsed response. */
    suspend fun respond(body: JsonObject, timeoutMs: Int = 12_000): JsonObject =
        Json.parseToJsonElement(post("/responses", "application/json", body.toString().toByteArray(), timeoutMs)).jsonObject

    /** POST /asr/transcribe in push-to-talk mode. [wav] is mono 16-bit PCM at 16 kHz. */
    suspend fun transcribe(wav: ByteArray, timeoutMs: Int = 10_000): String {
        val boundary = UUID.randomUUID().toString().replace("-", "")
        val settings = """{"mode":"PUSH_TO_TALK","model":"$TRANSCRIBE_MODEL","audioEncoding":"WAV","keywords":["AnyCar"]}"""
        val body = (
            "--$boundary\r\nContent-Disposition: form-data; name=\"request\"\r\nContent-Type: application/json\r\n\r\n$settings\r\n" +
                "--$boundary\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"command.wav\"\r\nContent-Type: audio/wav\r\n\r\n"
            ).toByteArray() + wav + "\r\n--$boundary--\r\n".toByteArray()
        val resp = Json.parseToJsonElement(post("/asr/transcribe", "multipart/form-data; boundary=$boundary", body, timeoutMs)).jsonObject
        return resp["transcript"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
    }

    private suspend fun post(path: String, contentType: String, body: ByteArray, timeoutMs: Int): String = withContext(Dispatchers.IO) {
        if (!configured) throw MetaError.InvalidKey()
        val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4_000
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", contentType)
        }
        try {
            conn.outputStream.use { it.write(body) }
            when (val code = conn.responseCode) {
                200 -> conn.inputStream.bufferedReader().use { it.readText() }
                401, 403 -> throw MetaError.InvalidKey()
                429 -> throw MetaError.RateLimited()
                else -> throw MetaError.Unavailable(code)
            }
        } catch (e: SocketTimeoutException) {
            throw MetaError.Timeout()
        } catch (e: UnknownHostException) {
            throw MetaError.Offline()
        } catch (e: MetaError) {
            throw e
        } catch (e: IOException) {
            throw MetaError.Offline()
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val BASE = "https://api.meta.ai/v1"
        const val SPARK_MODEL = "muse-spark-1.3"
        const val TRANSCRIBE_MODEL = "muse-voice-transcribe-1.0"

        /** The concatenated output_text of a /responses result. */
        fun outputText(resp: JsonObject): String =
            resp["output"]?.jsonArray.orEmpty()
                .map { it.jsonObject }
                .filter { it["type"]?.jsonPrimitive?.contentOrNull == "message" }
                .flatMap { it["content"]?.jsonArray.orEmpty() }
                .mapNotNull { c -> c.jsonObject.takeIf { it["type"]?.jsonPrimitive?.contentOrNull == "output_text" }?.get("text")?.jsonPrimitive?.contentOrNull }
                .joinToString(" ")
                .trim()

        /** The function calls of a /responses result, in order, as (name, arguments). */
        fun functionCalls(resp: JsonObject): List<Pair<String, JsonObject>> =
            resp["output"]?.jsonArray.orEmpty()
                .map { it.jsonObject }
                .filter { it["type"]?.jsonPrimitive?.contentOrNull == "function_call" }
                .map { call ->
                    val args = call["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "{}" }
                    call["name"]!!.jsonPrimitive.content to runCatching { Json.parseToJsonElement(args).jsonObject }.getOrDefault(JsonObject(emptyMap()))
                }
    }
}
