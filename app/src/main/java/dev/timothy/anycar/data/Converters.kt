package dev.timothy.anycar.data

import androidx.room.TypeConverter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter fun fromResults(v: Map<String, CheckResult>): String = json.encodeToString(v)
    @TypeConverter fun toResults(s: String): Map<String, CheckResult> =
        if (s.isBlank()) emptyMap() else json.decodeFromString(s)
}
