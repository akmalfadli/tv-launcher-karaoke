package id.karaoke.timerlauncher.timer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.timerDataStore: DataStore<Preferences> by preferencesDataStore(name = "timer_storage")

class TimerRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val keySnapshot = stringPreferencesKey("timer_snapshot")

    suspend fun saveSnapshot(snapshot: TimerSnapshot) {
        val serialized = json.encodeToString(snapshot)
        context.timerDataStore.edit { prefs ->
            prefs[keySnapshot] = serialized
        }
    }

    suspend fun loadSnapshot(): TimerSnapshot? {
        val raw = context.timerDataStore.data.map { it[keySnapshot] }.firstOrNull() ?: return null
        return try {
            json.decodeFromString<TimerSnapshot>(raw)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun clear() {
        context.timerDataStore.edit { prefs ->
            prefs.remove(keySnapshot)
        }
    }
}
