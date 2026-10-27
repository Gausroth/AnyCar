package dev.timothy.anycar.ai

import android.util.Base64
import dev.timothy.anycar.data.VehicleProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@Serializable
data class DashLight(
    val name: String,
    val color: String = "",
    /** "stop_now", "soon", or "info". */
    val urgency: String = "info",
    val meaning: String = "",
    /** [x_min, y_min, x_max, y_max] on a 0–1000 grid of the photo. */
    val box: List<Int> = emptyList(),
)

@Serializable
data class DashReport(val lights: List<DashLight> = emptyList(), val spoken: String = "")

/**
 * Muse Spark vision: names the lit warning lights in a parked-car dashboard photo and says how urgent each is.
 * Sends the photo plus the car's make, model, and year, which the Meta AI disclosure lists.
 */
class DashboardScanner(private val api: MetaApi) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun scan(jpeg: ByteArray, vehicle: VehicleProfile?): DashReport {
        val car = vehicle?.let { listOf(it.year.takeIf { y -> y > 0 }?.toString(), it.make, it.model).filterNot { s -> s.isNullOrBlank() }.joinToString(" ") }
            .orEmpty().ifBlank { "car" }
        val resp = api.respond(
            buildJsonObject {
                put("model", MetaApi.SPARK_MODEL)
                putJsonObject("reasoning") { put("effort", "low") }
                put("instructions", instructions(car))
                putJsonArray("input") {
                    addJsonObject {
                        put("type", "message")
                        put("role", "user")
                        putJsonArray("content") {
                            addJsonObject { put("type", "input_text"); put("text", "What do these dashboard lights mean?") }
                            addJsonObject {
                                put("type", "input_image")
                                put("image_url", "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP))
                            }
                        }
                    }
                }
            },
            timeoutMs = 25_000,
        )
        val text = MetaApi.outputText(resp)
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return DashReport(spoken = text.ifBlank { "I couldn't read that photo. Try again closer to the dashboard." })
        return runCatching { json.decodeFromString<DashReport>(text.substring(start, end + 1)) }
            .getOrElse { DashReport(spoken = "I couldn't read that photo. Try again closer to the dashboard.") }
    }

    private fun instructions(car: String) =
        "You are AnyCar. The driver is parked and photographed the instrument cluster of their $car. Identify every " +
            "lit warning or indicator light. Return only JSON: {\"lights\":[{\"name\":\"short name\",\"color\":\"red|amber|" +
            "green|blue|white\",\"urgency\":\"stop_now|soon|info\",\"meaning\":\"under 15 words, what to do\",\"box\":" +
            "[x_min,y_min,x_max,y_max]}],\"spoken\":\"under 40 words for the driver, most urgent first, plain spoken " +
            "language\"}. Boxes are on a 0-1000 grid of the image. Red lights that mean stop driving (oil pressure, " +
            "temperature, brake system) are stop_now. If the photo isn't a dashboard or nothing is lit, return an empty " +
            "list and say so in spoken."
}
