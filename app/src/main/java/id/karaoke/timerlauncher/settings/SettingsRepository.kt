package id.karaoke.timerlauncher.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import id.karaoke.timerlauncher.server.dto.RentalRecord
import java.util.UUID
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
    val allowedPackages: Set<String> = setOf("com.google.android.youtube.tv", "org.smarttube.stable", "com.google.android.youtube"),
    val hourlyRate: Long = 50000L,
    val standbyBgMode: String = "NONE", // "NONE", "IMAGE", "VIDEO"
    val standbyYoutubeUrl: String = "",
    val standbyYoutubeMute: Boolean = true,
    val bgImageTimestamp: Long = 0L,
    val standbyVideoMute: Boolean = true,
    val bgVideoTimestamp: Long = 0L,
    val operatorPhone: String = ""
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
    private val keyHourlyRate = longPreferencesKey("hourly_rate")
    private val keyStandbyBgMode = stringPreferencesKey("standby_bg_mode")
    private val keyStandbyYoutubeUrl = stringPreferencesKey("standby_youtube_url")
    private val keyStandbyYoutubeMute = booleanPreferencesKey("standby_youtube_mute")
    private val keyBgImageTimestamp = longPreferencesKey("bg_image_timestamp")
    private val keyStandbyVideoMute = booleanPreferencesKey("standby_video_mute")
    private val keyBgVideoTimestamp = longPreferencesKey("bg_video_timestamp")
    private val keyOperatorPhone = stringPreferencesKey("operator_phone")
    private val keyActionLogs = stringPreferencesKey("action_logs")
    private val keyRentalRecords = stringPreferencesKey("rental_records")
    private val keyActiveRentalId = stringPreferencesKey("active_rental_id")
    private val keyActiveCustomerName = stringPreferencesKey("active_customer_name")

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
            allowedPackages = prefs[keyAllowedPackages] ?: setOf("com.google.android.youtube.tv", "org.smarttube.stable", "com.google.android.youtube"),
            hourlyRate = prefs[keyHourlyRate] ?: 50000L,
            standbyBgMode = prefs[keyStandbyBgMode] ?: "NONE",
            standbyYoutubeUrl = prefs[keyStandbyYoutubeUrl] ?: "",
            standbyYoutubeMute = prefs[keyStandbyYoutubeMute] ?: true,
            bgImageTimestamp = prefs[keyBgImageTimestamp] ?: 0L,
            standbyVideoMute = prefs[keyStandbyVideoMute] ?: true,
            bgVideoTimestamp = prefs[keyBgVideoTimestamp] ?: 0L,
            operatorPhone = prefs[keyOperatorPhone] ?: ""
        )
    }

    suspend fun getSettings(): AppSettings = settingsFlow.first()

    fun getSettingsSync(): AppSettings = AppSettings()

    fun getStandbyBgFile(): java.io.File {
        return java.io.File(context.filesDir, "standby_bg.jpg")
    }

    suspend fun saveStandbyBgImage(bytes: ByteArray) {
        val file = getStandbyBgFile()
        file.writeBytes(bytes)
        context.settingsDataStore.edit { prefs ->
            prefs[keyStandbyBgMode] = "IMAGE"
            prefs[keyBgImageTimestamp] = System.currentTimeMillis()
        }
    }

    suspend fun deleteStandbyBgImage() {
        val file = getStandbyBgFile()
        if (file.exists()) file.delete()
        context.settingsDataStore.edit { prefs ->
            if (prefs[keyStandbyBgMode] == "IMAGE") {
                prefs[keyStandbyBgMode] = "NONE"
            }
            prefs[keyBgImageTimestamp] = System.currentTimeMillis()
        }
    }

    fun getStandbyVideoFile(): java.io.File {
        return java.io.File(context.filesDir, "standby_video.mp4")
    }

    suspend fun saveStandbyVideo(bytes: ByteArray) {
        val file = getStandbyVideoFile()
        file.writeBytes(bytes)
        context.settingsDataStore.edit { prefs ->
            prefs[keyStandbyBgMode] = "VIDEO"
            prefs[keyBgVideoTimestamp] = System.currentTimeMillis()
        }
    }

    suspend fun saveStandbyVideoStream(inputStream: java.io.InputStream) {
        val file = getStandbyVideoFile()
        val tempFile = java.io.File(context.filesDir, "standby_video_tmp.mp4")
        tempFile.outputStream().use { out ->
            inputStream.copyTo(out)
        }
        if (tempFile.exists() && tempFile.length() > 0) {
            if (file.exists()) file.delete()
            tempFile.renameTo(file)
        }
        context.settingsDataStore.edit { prefs ->
            prefs[keyStandbyBgMode] = "VIDEO"
            prefs[keyBgVideoTimestamp] = System.currentTimeMillis()
        }
    }

    suspend fun deleteStandbyVideo() {
        val file = getStandbyVideoFile()
        if (file.exists()) file.delete()
        context.settingsDataStore.edit { prefs ->
            if (prefs[keyStandbyBgMode] == "VIDEO") {
                prefs[keyStandbyBgMode] = "NONE"
            }
            prefs[keyBgVideoTimestamp] = System.currentTimeMillis()
        }
    }

    suspend fun updateRoomName(name: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[keyRoomName] = name
        }
    }

    suspend fun updateHourlyRate(rate: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[keyHourlyRate] = rate
        }
    }

    suspend fun updateGeneralSettings(
        roomName: String? = null,
        hourlyRate: Long? = null,
        corner: OverlayCorner? = null,
        size: OverlaySize? = null,
        standbyBgMode: String? = null,
        standbyYoutubeUrl: String? = null,
        standbyYoutubeMute: Boolean? = null,
        standbyVideoMute: Boolean? = null,
        operatorPhone: String? = null
    ) {
        context.settingsDataStore.edit { prefs ->
            if (roomName != null && roomName.isNotBlank()) {
                prefs[keyRoomName] = roomName.trim()
            }
            if (hourlyRate != null && hourlyRate >= 0) {
                prefs[keyHourlyRate] = hourlyRate
            }
            if (corner != null) {
                prefs[keyOverlayCorner] = corner.name
            }
            if (size != null) {
                prefs[keyOverlaySize] = size.name
            }
            if (standbyBgMode != null) {
                prefs[keyStandbyBgMode] = standbyBgMode
            }
            if (standbyYoutubeUrl != null) {
                prefs[keyStandbyYoutubeUrl] = standbyYoutubeUrl.trim()
            }
            if (standbyYoutubeMute != null) {
                prefs[keyStandbyYoutubeMute] = standbyYoutubeMute
            }
            if (standbyVideoMute != null) {
                prefs[keyStandbyVideoMute] = standbyVideoMute
            }
            if (operatorPhone != null) {
                prefs[keyOperatorPhone] = operatorPhone.trim()
            }
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

    suspend fun toggleAllowedPackage(packageName: String, isAllowed: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[keyAllowedPackages] ?: setOf("com.google.android.youtube.tv", "org.smarttube.stable", "com.google.android.youtube")
            val updated = if (isAllowed) {
                current + packageName
            } else {
                current - packageName
            }
            prefs[keyAllowedPackages] = updated
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

    suspend fun getActiveCustomerName(): String? {
        return context.settingsDataStore.data.map { it[keyActiveCustomerName] }.first()
    }

    suspend fun startRental(customerName: String, roomName: String, durationMinutes: Int): RentalRecord {
        val record = RentalRecord(
            id = UUID.randomUUID().toString(),
            customerName = customerName,
            roomName = roomName,
            startTime = System.currentTimeMillis(),
            initialDurationMinutes = durationMinutes,
            totalDurationMinutes = durationMinutes,
            status = "ACTIVE"
        )
        context.settingsDataStore.edit { prefs ->
            val existing = getRentalsInternal(prefs)
            existing.add(0, record)
            prefs[keyRentalRecords] = json.encodeToString(existing.take(500))
            prefs[keyActiveRentalId] = record.id
            prefs[keyActiveCustomerName] = customerName
        }
        return record
    }

    suspend fun extendRental(minutes: Int) {
        context.settingsDataStore.edit { prefs ->
            val activeId = prefs[keyActiveRentalId] ?: return@edit
            val existing = getRentalsInternal(prefs)
            val index = existing.indexOfFirst { it.id == activeId }
            if (index != -1) {
                val current = existing[index]
                val updated = current.copy(
                    extendedMinutes = current.extendedMinutes + minutes,
                    totalDurationMinutes = current.totalDurationMinutes + minutes
                )
                existing[index] = updated
                prefs[keyRentalRecords] = json.encodeToString(existing)
            }
        }
    }

    suspend fun finishRental(status: String = "COMPLETED") {
        context.settingsDataStore.edit { prefs ->
            val activeId = prefs[keyActiveRentalId] ?: return@edit
            val existing = getRentalsInternal(prefs)
            val index = existing.indexOfFirst { it.id == activeId }
            if (index != -1) {
                val current = existing[index]
                val updated = current.copy(
                    endTime = System.currentTimeMillis(),
                    status = status
                )
                existing[index] = updated
                prefs[keyRentalRecords] = json.encodeToString(existing)
            }
            prefs.remove(keyActiveRentalId)
            prefs.remove(keyActiveCustomerName)
        }
    }

    suspend fun updateActiveCustomerName(newName: String) {
        context.settingsDataStore.edit { prefs ->
            val activeId = prefs[keyActiveRentalId] ?: return@edit
            val existing = getRentalsInternal(prefs)
            val index = existing.indexOfFirst { it.id == activeId }
            if (index != -1) {
                val current = existing[index]
                existing[index] = current.copy(customerName = newName)
                prefs[keyRentalRecords] = json.encodeToString(existing)
            }
            prefs[keyActiveCustomerName] = newName
        }
    }

    suspend fun clearRentals() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(keyRentalRecords)
        }
    }

    suspend fun getRentals(): List<RentalRecord> {
        val raw = context.settingsDataStore.data.map { it[keyRentalRecords] }.first() ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getRentalsInternal(prefs: androidx.datastore.preferences.core.Preferences): MutableList<RentalRecord> {
        val raw = prefs[keyRentalRecords]
        return if (!raw.isNullOrBlank()) {
            try {
                json.decodeFromString<List<RentalRecord>>(raw).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
    }
}

