package com.dsh.android.data.remote

import android.util.Log
import com.dsh.android.data.local.DshPreferences
import com.dsh.android.domain.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okio.BufferedSource
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "DshWebSocketClient"

/**
 * WebSocket client that communicates with DSH via the remote.mux endpoint.
 *
 * Protocol: JSON-RPC over WebSocket
 * Send:     {"type":"client-request","rpcId":"<uuid>","method":"...","payload":{"args":{"...":{...}}}}
 * Receive:  {"type":"server-response","rpcId":"<uuid>","result":{...}}
 *   or:     {"type":"server-event","event":{"type":"...","seq":...,"data":...}}
 *
 * session/follow is a streaming endpoint: after the initial response, the server
 * pushes events with message data, tool calls, agent status, etc.
 */
@Singleton
class DshWebSocketClient @Inject constructor(
    private val preferences: DshPreferences,
    @Named("plain") private val httpClient: OkHttpClient
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val gson = Gson()

    private var currentSessionId: String? = null
    private var onEventCallback: ((WebSocketEvent) -> Unit)? = null
    private var isManualDisconnect = false
    private var reconnectAttempt = 0
    private val maxReconnectAttempts = 5
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Pending RPC calls (for streaming follow)
    private val pendingRpcs = mutableMapOf<String, (JsonObject) -> Unit>()

    // Message buffer for session/follow stream
    private val _events = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 100)
    val events: SharedFlow<WebSocketEvent> = _events.asSharedFlow()

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
            val serverAddress = preferences.serverAddress.first() ?: return@launch
            val sessionToken = preferences.sessionToken.first() ?: return@launch
            val coreCookie = preferences.coreCookie.first()

            val wsUrl = serverAddress.replace("http", "ws") + "/api/remote.mux"
            val cookieHeader = buildString {
                append("dsh_wua_session=$sessionToken")
                if (!coreCookie.isNullOrBlank()) append("; $coreCookie")
            }

            Log.d(TAG, "Connecting WebSocket to $wsUrl")
            val request = Request.Builder()
                .url(wsUrl)
                .addHeader("Cookie", cookieHeader)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connected")
                    reconnectAttempt = 0
                    onEvent(WebSocketEvent.Connected(sessionId))
                    // Start following the session for real-time events
                    followSession(sessionId)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = gson.fromJson(text, JsonObject::class.java)
                        val type = json.get("type")?.asString
                        Log.d(TAG, "WS message type: $type")

                        when (type) {
                            "server-response" -> {
                                val rpcId = json.get("rpcId")?.asString
                                val result = json.getAsJsonObject("result")
                                if (result?.get("ok")?.asBoolean == true) {
                                    val value = result.getAsJsonObject("value")
                                    // Check if this is a follow stream initial response
                                    if (value?.has("stream") == true || value?.has("events") == true) {
                                        parseFollowStreamEvents(value, sessionId, onEvent)
                                    }
                                } else {
                                    val error = result?.getAsJsonObject("error")
                                    Log.w(TAG, "RPC error: ${error?.get("message")?.asString}")
                                }
                            }
                            "server-event" -> {
                                // Streaming event from session/follow
                                val event = json.getAsJsonObject("event")
                                if (event != null) {
                                    parseFollowEvent(event, sessionId, onEvent)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "WS message parse error", e)
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    if (!isManualDisconnect) {
                        onEvent(WebSocketEvent.Disconnected("连接关闭"))
                        scheduleReconnect()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket error", t)
                    if (!isManualDisconnect) {
                        onEvent(WebSocketEvent.Error(t))
                        scheduleReconnect()
                    }
                }
            })
        }
    }

    /**
     * Send session/follow RPC to start streaming events for a session.
     * The server pushes events with type "server-event" containing message data.
     */
    private fun followSession(sessionId: String) {
        val rpcId = UUID.randomUUID().toString()
        val message = JsonObject().apply {
            addProperty("type", "client-request")
            addProperty("rpcId", rpcId)
            addProperty("method", "session/follow")
            add("payload", JsonObject().apply {
                add("args", JsonObject().apply {
                    add("request", JsonObject().apply {
                        add("address", JsonObject().apply {
                            addProperty("kind", "session")
                            addProperty("sessionId", sessionId)
                        })
                        addProperty("maxMessages", 100)
                    })
                })
            })
        }
        Log.d(TAG, "Sending session/follow for $sessionId")
        webSocket?.send(gson.toJson(message))
    }

    /**
     * Send session/prompt RPC to send a user message.
     */
    fun sendMessage(sessionId: String, content: String) {
        val rpcId = UUID.randomUUID().toString()
        val message = JsonObject().apply {
            addProperty("type", "client-request")
            addProperty("rpcId", rpcId)
            addProperty("method", "session/prompt")
            add("payload", JsonObject().apply {
                add("args", JsonObject().apply {
                    add("request", JsonObject().apply {
                        addProperty("requestId", UUID.randomUUID().toString())
                        addProperty("sessionId", sessionId)
                        addProperty("mode", "queue")
                        add("content", gson.toJsonTree(listOf(
                            mapOf("type" to "text", "text" to content)
                        )))
                    })
                })
            })
        }
        Log.d(TAG, "Sending session/prompt: ${content.take(50)}")
        webSocket?.send(gson.toJson(message))
    }

    fun confirmToolCall(sessionId: String, callId: String, approved: Boolean) {
        // DSH uses session/prompt with tool confirmation in the content
        // For now, log it - tool confirm protocol needs further investigation
        Log.d(TAG, "Tool confirm: $callId approved=$approved")
    }

    fun disconnect() {
        isManualDisconnect = true
        coroutineScope.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        currentSessionId = null
        onEventCallback = null
    }

    private fun scheduleReconnect() {
        if (isManualDisconnect || reconnectAttempt >= maxReconnectAttempts) return
        reconnectAttempt++
        val delayMs = minOf(1000L * (1 shl (reconnectAttempt - 1)), 30_000L)
        Log.d(TAG, "Scheduling reconnect in ${delayMs}ms (attempt $reconnectAttempt)")
        coroutineScope.launch {
            delay(delayMs)
            performConnect()
        }
    }

    /**
     * Parse events from the session/follow stream.
     * Each event has: {type, event: {type, seq, time, data}}
     */
    private fun parseFollowStreamEvents(
        value: JsonObject?,
        sessionId: String,
        onEvent: (WebSocketEvent) -> Unit
    ) {
        val events = value?.getAsJsonArray("events") ?: return
        for (eventElement in events) {
            val event = eventElement.asJsonObject
            parseFollowEvent(event, sessionId, onEvent)
        }
    }

    /**
     * Parse a single follow event.
     * Event types include: "message/assistant", "message/user", "tool/call", etc.
     */
    private fun parseFollowEvent(
        event: JsonObject,
        sessionId: String,
        onEvent: (WebSocketEvent) -> Unit
    ) {
        val eventType = event.get("type")?.asString ?: return
        val seq = event.get("seq")?.asLong ?: 0
        Log.d(TAG, "Follow event: type=$eventType seq=$seq")

        when {
            eventType == "assistant/message" || eventType == "user/message" -> {
                val data = event.getAsJsonObject("data") ?: return
                val role = if (eventType == "assistant/message")
                    MessageRole.ASSISTANT else MessageRole.USER

                // content is a list: [{type:"text", text:"..."}, {type:"reasoning", text:"..."}, ...]
                val contentArray = if (eventType == "assistant/message") {
                    data.getAsJsonObject("message")?.getAsJsonArray("content")
                } else {
                    data.getAsJsonArray("content")
                }

                val textParts = mutableListOf<String>()
                val toolCalls = mutableListOf<ToolCall>()

                if (contentArray != null) {
                    for (item in contentArray) {
                        val itemObj = item.asJsonObject
                        val itemType = itemObj.get("type")?.asString ?: ""
                        when (itemType) {
                            "text", "reasoning" -> {
                                val text = itemObj.get("text")?.asString ?: ""
                                if (text.isNotBlank()) textParts.add(text)
                            }
                            "tool-call" -> {
                                toolCalls.add(ToolCall(
                                    id = itemObj.get("id")?.asString ?: "tc-$seq",
                                    toolName = itemObj.get("name")?.asString ?: "unknown",
                                    args = emptyMap()
                                ))
                            }
                        }
                    }
                } else {
                    // Fallback: content might be a string
                    val contentStr = data.get("content")?.asString ?: ""
                    if (contentStr.isNotBlank()) textParts.add(contentStr)
                }

                val content = textParts.joinToString("\n")
                if (content.isNotBlank() && content != "null") {
                    val message = Message(
                        id = "msg-$seq",
                        sessionId = sessionId,
                        role = role,
                        content = content,
                        timestamp = event.get("time")?.asLong ?: System.currentTimeMillis(),
                        toolCalls = toolCalls
                    )
                    onEvent(WebSocketEvent.MessageReceived(message))
                }
            }
            eventType.contains("tool") -> {
                val data = event.get("data")
                if (data?.isJsonObject == true) {
                    val obj = data.asJsonObject
                    val toolCall = ToolCall(
                        id = obj.get("callId")?.asString ?: "tc-$seq",
                        toolName = obj.get("tool")?.asString ?: obj.get("name")?.asString ?: "unknown",
                        args = emptyMap()
                    )
                    onEvent(WebSocketEvent.ToolCallReceived(toolCall))
                }
            }
            eventType.contains("status") || eventType.contains("agent") -> {
                val data = event.get("data")
                val status = when {
                    data?.asString == "running" -> AgentStatus.RUNNING
                    data?.asString == "waiting" -> AgentStatus.WAITING_CONFIRMATION
                    data?.isJsonObject == true -> {
                        val s = data.asJsonObject.get("status")?.asString
                        when (s) {
                            "running" -> AgentStatus.RUNNING
                            "waiting" -> AgentStatus.WAITING_CONFIRMATION
                            else -> AgentStatus.IDLE
                        }
                    }
                    else -> AgentStatus.IDLE
                }
                onEvent(WebSocketEvent.AgentStatusChanged(status))
            }
            else -> {
                Log.d(TAG, "Unhandled event type: $eventType")
            }
        }
    }
}
