package id.karaoke.timerlauncher.server

import id.karaoke.timerlauncher.server.dto.WsMessage
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class WebSocketHub {
    private val json = Json { ignoreUnknownKeys = true }
    private val sessions = ConcurrentHashMap.newKeySet<DefaultWebSocketSession>()
    private val mutex = Mutex()

    fun addSession(session: DefaultWebSocketSession) {
        sessions.add(session)
    }

    fun removeSession(session: DefaultWebSocketSession) {
        sessions.remove(session)
    }

    suspend fun broadcast(message: WsMessage) {
        val payload = json.encodeToString(message)
        val frame = Frame.Text(payload)

        val iterator = sessions.iterator()
        while (iterator.hasNext()) {
            val session = iterator.next()
            try {
                session.send(frame)
            } catch (e: Exception) {
                iterator.remove()
            }
        }
    }
}
