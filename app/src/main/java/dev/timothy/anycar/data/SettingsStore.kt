package dev.timothy.anycar.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Global settings (§7.7). Only the keys this build uses are here; the rest arrive with their features. */
class SettingsStore(ctx: Context) {
    private val store = ctx.settingsDataStore

    val firstRunDone: Flow<Boolean> = flag(FIRST_RUN_DONE, false)
    val cloudSpeechAllowed: Flow<Boolean> = flag(CLOUD_SPEECH_ALLOWED, false)
    val ttsEnabled: Flow<Boolean> = flag(TTS_ENABLED, true)
    val stayOpenOnDisconnect: Flow<Boolean> = flag(STAY_OPEN_ON_DISCONNECT, false)
    val lastVehicleId: Flow<Long> = store.data.map { it[LAST_VEHICLE_ID] ?: -1L }

    // Hackathon edition: Meta models, off until the driver turns them on after reading what's sent.
    val metaAiEnabled: Flow<Boolean> = flag(META_AI_ENABLED, false)
    val metaSpeechEnabled: Flow<Boolean> = flag(META_SPEECH_ENABLED, false)

    suspend fun setFirstRunDone(v: Boolean) = set(FIRST_RUN_DONE, v)
    suspend fun setCloudSpeechAllowed(v: Boolean) = set(CLOUD_SPEECH_ALLOWED, v)
    suspend fun setMetaAiEnabled(v: Boolean) = set(META_AI_ENABLED, v)
    suspend fun setMetaSpeechEnabled(v: Boolean) = set(META_SPEECH_ENABLED, v)
    suspend fun setLastVehicleId(id: Long) = set(LAST_VEHICLE_ID, id)

    private fun flag(key: Preferences.Key<Boolean>, default: Boolean) = store.data.map { it[key] ?: default }
    private suspend fun <T> set(key: Preferences.Key<T>, v: T) { store.edit { it[key] = v } }

    private companion object {
        val FIRST_RUN_DONE = booleanPreferencesKey("first_run_done")
        val CLOUD_SPEECH_ALLOWED = booleanPreferencesKey("cloud_speech_allowed")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val STAY_OPEN_ON_DISCONNECT = booleanPreferencesKey("stay_open_on_disconnect")
        val LAST_VEHICLE_ID = longPreferencesKey("last_vehicle_id")
        val META_AI_ENABLED = booleanPreferencesKey("meta_ai_enabled")
        val META_SPEECH_ENABLED = booleanPreferencesKey("meta_speech_enabled")
    }
}
