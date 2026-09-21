package com.dsh.android.data.remote

import com.dsh.android.data.local.DshPreferences
import com.dsh.android.domain.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DshWebSocketClient @Inject constructor(
    private val preferences: DshPreferences
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No timeout for WebSocket
        .build()

    private var webSocket: WebSocket? = null
    private val gson = Gson()

    fun connect(sessionId: String, onEvent: (WebSocketEvent) -> Unit) {
        val serverAddress = runBlocking { preferences.serverAddress.first() }
        val sessionToken = runBlocking { preferences.sessionToken.first() }

        if (serverAddress == null || sessionToken == null) {
            onEvent(WebSocketEvent.Error(Exception("Not connected to server")))
            return
        }

        val wsUrl = serverAddress.replace("http", "ws") + "/api/remote.mux"
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Cookie", "dsh_wua_session=$sessionToken")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onEvent(WebSocketEvent.Connected(sessionId))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = gson.fromJson(text, JsonObject::class.java)
                    val type = json.get("type")?.asString

                    when (type) {
                        "assistant/message" -> {
                            val message = Message(
                                id = json.get("id")?.asString ?: "",
                                sessionId = sessionId,
                                role = MessageRole.ASSISTANT,
                                content = json.get("content")?.asString ?: "",
                                timestamp = System.currentTimeMillis()
                            )
                            onEvent(WebSocketEvent.MessageReceived(message))
                        }
                        "tool/call" -> {
                            val toolCall = ToolCall(
                                id = json.get("callId")?.asString ?: "",
                                toolName = json.get("tool")?.asString ?: "",
                                args = gson.fromJson(json.get("args"), Map::class.java) as? Map<String, Any> ?: emptyMap()
                            )
                            onEvent(WebSocketEvent.ToolCallReceived(toolCall))
                        }
                        "agent/status" -> {
                            val status = when (json.get("status")?.asString) {
                                "running" -> AgentStatus.RUNNING
                                "waiting" -> AgentStatus.WAITING_CONFIRMATION
                                else -> AgentStatus.IDLE
                            }
                            onEvent(WebSocketEvent.AgentStatusChanged(status))
                        }
                    }
                } catch (e: Exception) {
                    onEvent(WebSocketEvent.Error(e))
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                onEvent(WebSocketEvent.Disconnected(reason))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onEvent(WebSocketEvent.Error(t))
            }
        })
    }

    fun sendMessage(sessionId: String, content: String) {
        val message = JsonObject().apply {
            addProperty("type", "user/message")
            addProperty("sessionId", sessionId)
            addProperty("content", content)
        }
        webSocket?.send(gson.toJson(message))
    }

    fun confirmToolCall(sessionId: String, callId: String, approved: Boolean) {
        val message = JsonObject().apply {
            addProperty("type", "tool/confirm")
            addProperty("sessionId", sessionId)
            addProperty("callId", callId)
            addProperty("approved", approved)
        }
        webSocket?.send(gson.toJson(message))
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }

    private fun <T> runBlocking(block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking { block() }
    }
}
