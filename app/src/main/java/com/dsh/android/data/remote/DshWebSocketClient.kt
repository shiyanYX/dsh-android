package com.dsh.android.data.remote

import com.dsh.android.data.local.DshPreferences
import com.dsh.android.domain.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
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

    // Reconnection state
    private var currentSessionId: String? = null
    private var onEventCallback: ((WebSocketEvent) -> Unit)? = null
    private var isManualDisconnect = false
    private var reconnectAttempt = 0
    private val maxReconnectAttempts = 10
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect(sessionId: String, onEvent: (WebSocketEvent) -> Unit) {
        currentSessionId = sessionId
        onEventCallback = onEvent
        isManualDisconnect = false
        reconnectAttempt = 0

        performConnect()
    }

    private fun performConnect() {
        val sessionId = currentSessionId ?: return
        val onEvent = onEventCallback ?: return

        coroutineScope.launch {
            val serverAddress = preferences.serverAddress.first()
            val sessionToken = preferences.sessionToken.first()
            val coreCookie = preferences.coreCookie.first()

            if (serverAddress == null || sessionToken == null) {
                withContext(Dispatchers.Main) {
                    onEvent(WebSocketEvent.Error(Exception("Not connected to server")))
                }
                return@launch
            }

            val wsUrl = serverAddress.replace("http", "ws") + "/api/remote.mux"
            val cookieHeader = buildString {
                append("dsh_wua_session=$sessionToken")
                if (!coreCookie.isNullOrBlank()) {
                    append("; $coreCookie")
                }
            }

            val request = Request.Builder()
                .url(wsUrl)
                .addHeader("Cookie", cookieHeader)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    reconnectAttempt = 0 // Reset on successful connection
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
                    if (!isManualDisconnect) {
                        onEvent(WebSocketEvent.Disconnected("连接关闭: $reason"))
                        scheduleReconnect()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!isManualDisconnect) {
                        onEvent(WebSocketEvent.Error(t))
                        scheduleReconnect()
                    }
                }
            })
        }
    }

    private fun scheduleReconnect() {
        if (isManualDisconnect || reconnectAttempt >= maxReconnectAttempts) return

        reconnectAttempt++
        val delayMs = calculateBackoff(reconnectAttempt)

        coroutineScope.launch {
            delay(delayMs)
            performConnect()
        }
    }

    private fun calculateBackoff(attempt: Int): Long {
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 30s (capped)
        val baseDelay = 1000L
        val exponentialDelay = baseDelay * (1 shl (attempt - 1))
        return minOf(exponentialDelay, 30_000L)
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
        isManualDisconnect = true
        coroutineScope.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        currentSessionId = null
        onEventCallback = null
    }
}
