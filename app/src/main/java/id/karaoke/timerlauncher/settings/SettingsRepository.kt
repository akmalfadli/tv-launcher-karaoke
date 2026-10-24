package id.karaoke.timerlauncher.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

@Serializable
data class ActionLogEntry(
    val timestamp: Long,
    val action: String,
    val details: String,
    val sourceIp: String = "local"
)

enum class OverlayCorner {
    TOP_RIGHT,
    TOP_LEFT,
    BOTTOM_RIGHT,
    BOTTOM_LEFT
}

enum class OverlaySize {
    SMALL,
    MEDIUM,
    LARGE
}

data class AppSettings(
    val roomName: String = "Ruang 1",
    val pinHash: String = "",
    val pinSalt: String = "",
    val isSetupDone: Boolean = false,
    val overlayCorner: OverlayCorner = OverlayCorner.TOP_RIGHT,
    val overlaySize: OverlaySize = OverlaySize.MEDIUM,
    val allowedPackages: Set<String> = setOf("com.google.android.youtube.tv", "com.google.android.youtube")
)

class SettingsRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private val keyRoomName = stringPreferencesKey("room_name")
    private val keyPinHash = stringPreferencesKey("pin_hash")
    private val keyPinSalt = stringPreferencesKey("pin_salt")
    private val keySetupDone = booleanPreferencesKey("is_setup_done")
    private val keyOverlayCorner = stringPreferencesKey("overlay_corner")
    private val keyOverlaySize = stringPreferencesKey("overlay_size")
    private val keyAllowedPackages = stringSetPreferencesKey("allowed_packages")
    private val keyActionLogs = stringPreferencesKey("action_logs")

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            roomName = prefs[keyRoomName] ?: "Ruang 1",
            pinHash = prefs[keyPinHash] ?: "",
            pinSalt = prefs[keyPinSalt] ?: "",
            isSetupDone = prefs[keySetupDone] ?: false,
            overlayCorner = runCatching {
                OverlayCorner.valueOf(prefs[keyOverlayCorner] ?: OverlayCorner.TOP_RIGHT.name)
            }.getOrDefault(OverlayCorner.TOP_RIGHT),
            overlaySize = runCatching {
                OverlaySize.valueOf(prefs[keyOverlaySize] ?: OverlaySize.MEDIUM.name)
            }.getOrDefault(OverlaySize.MEDIUM),
            allowedPackages = prefs[keyAllowedPackages] ?: setOf("com.google.android.youtube.tv", "com.google.android.youtube")
        )
    }

    suspend fun getSettings(): AppSettings = settingsFlow.first()

    fun getSettingsSync(): AppSettings = AppSettings()

    suspend fun updateRoomName(name: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[keyRoomName] = name
        }
    }

    suspend fun setPin(hash: String, salt: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[keyPinHash] = hash
            prefs[keyPinSalt] = salt
        }
    }

    suspend fun setSetupDone(done: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[keySetupDone] = done
        }
    }

    suspend fun updateOverlayConfig(corner: OverlayCorner, size: OverlaySize) {
        context.settingsDataStore.edit { prefs ->
            prefs[keyOverlayCorner] = corner.name
            prefs[keyOverlaySize] = size.name
        }
    }

    suspend fun updateAllowedPackages(packages: Set<String>) {
        context.settingsDataStore.edit { prefs ->
            prefs[keyAllowedPackages] = packages
        }
    }

    suspend fun recordAction(action: String, details: String, sourceIp: String = "local") {
        context.settingsDataStore.edit { prefs ->
            val existingJson = prefs[keyActionLogs]
            val logs = if (!existingJson.isNullOrBlank()) {
                try {
                    json.decodeFromString<List<ActionLogEntry>>(existingJson).toMutableList()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }
            logs.add(0, ActionLogEntry(System.currentTimeMillis(), action, details, sourceIp))
            // Keep last 200 logs
            val trimmed = logs.take(200)
            prefs[keyActionLogs] = json.encodeToString(trimmed)
        }
    }

    suspend fun getActionLogs(): List<ActionLogEntry> {
        val raw = context.settingsDataStore.data.map { it[keyActionLogs] }.first() ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
