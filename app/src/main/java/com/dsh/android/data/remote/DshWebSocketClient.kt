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
    @Named("plain") private val httpClient: OkHttpClient,
    private val logger: com.dsh.android.util.DshLogger
) {
    // WebSocket needs its own client: readTimeout=0 (infinite), no callTimeout
    // Must share SSL trust-all config with the plain client
    private val client = httpClient.newBuilder()
        .readTimeout(0, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .followRedirects(true)
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
        val sessionId = currentSessionId ?: run {
            logger.e(TAG, "WS performConnect ABORT: sessionId is null")
            return
        }
        val onEvent = onEventCallback ?: run {
            logger.e(TAG, "WS performConnect ABORT: onEventCallback is null")
            return
        }

        coroutineScope.launch {
            val serverAddress = preferences.serverAddress.first()
            val sessionToken = preferences.sessionToken.first()
            val coreCookie = preferences.coreCookie.first()

            if (serverAddress.isNullOrBlank()) {
                logger.e(TAG, "WS performConnect ABORT: serverAddress is null/blank")
                return@launch
            }
            if (sessionToken.isNullOrBlank()) {
                logger.e(TAG, "WS performConnect ABORT: sessionToken is null/blank")
                return@launch
            }

            val wsUrl = serverAddress.replace("http", "ws") + "/api/remote.mux"
            val cookieHeader = buildString {
                append("dsh_wua_session=$sessionToken")
                if (!coreCookie.isNullOrBlank()) append("; $coreCookie")
            }

            logger.i(TAG, "Connecting to $wsUrl for session=$sessionId")
            logger.d(TAG, "Cookie: ${cookieHeader.take(80)}...")

            val request = Request.Builder()
                .url(wsUrl)
                .addHeader("Cookie", cookieHeader)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    logger.i(TAG, "WebSocket connected (HTTP ${response.code})")
                    reconnectAttempt = 0
                    onEvent(WebSocketEvent.Connected(sessionId))
                    // Start following the session for real-time events
                    followSession(sessionId)
                }

                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    logger.i(TAG, "WS ← BINARY frame: ${bytes.size} bytes, first20=${bytes.substring(0, minOf(20, bytes.size)).hex()}")
                    // Try to interpret as UTF-8 text
                    try {
                        val text = bytes.utf8()
                        logger.d(TAG, "WS ← binary as text: ${text.take(200)}")
                        onMessage(webSocket, text)
                    } catch (e: Exception) {
                        logger.w(TAG, "WS ← binary not valid UTF-8")
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = gson.fromJson(text, JsonObject::class.java)
                        val type = json.get("type")?.asString
                        logger.i(TAG, "WS ← type=$type keys=${json.keySet()} (${text.length} bytes)")
                        if (type == null) {
                            logger.w(TAG, "WS ← NULL type! Full message: ${text.take(500)}")
                        }

                        when (type) {
                            "server-response" -> {
                                val rpcId = json.get("rpcId")?.asString
                                val result = json.getAsJsonObject("result")
                                if (result?.get("ok")?.asBoolean == true) {
                                    val value = result.getAsJsonObject("value")
                                    val valueKeys = value?.keySet() ?: emptySet()
                                    logger.d(TAG, "WS response OK rpcId=$rpcId value keys=$valueKeys")
                                    // Check if this is a follow stream initial response
                                    if (value?.has("stream") == true || value?.has("events") == true) {
                                        logger.d(TAG, "WS follow stream detected, parsing events...")
                                        parseFollowStreamEvents(value, sessionId, onEvent)
                                    }
                                } else {
                                    val error = result?.getAsJsonObject("error")
                                    val errCode = error?.get("code")?.asString
                                    val errMsg = error?.get("message")?.asString
                                    logger.e(TAG, "WS RPC error: [$errCode] $errMsg")
                                }
                            }
                            "server-event" -> {
                                // Streaming event from session/follow
                                val event = json.getAsJsonObject("event")
                                if (event != null) {
                                    val eventType = event.get("type")?.asString
                                    val seq = event.get("seq")?.asLong
                                    logger.d(TAG, "WS event: type=$eventType seq=$seq")
                                    parseFollowEvent(event, sessionId, onEvent)
                                }
                            }
                            else -> {
                                logger.w(TAG, "WS unknown message type: $type")
                            }
                        }
                    } catch (e: Exception) {
                        logger.e(TAG, "WS message parse error: ${text.take(200)}", e)
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    logger.w(TAG, "WS closing: code=$code reason=$reason")
                    webSocket.close(1000, null)
                    if (!isManualDisconnect) {
                        onEvent(WebSocketEvent.Disconnected("连接关闭 code=$code"))
                        scheduleReconnect()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    logger.e(TAG, "WS failure: ${response?.code ?: "no response"} - ${t.message}", t)
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
        val jsonStr = gson.toJson(message)
        logger.i(TAG, "WS SEND session/follow rpcId=$rpcId session=$sessionId (${jsonStr.length} bytes)")
        logger.d(TAG, "WS SEND body: ${jsonStr.take(500)}")
        val ws = webSocket
        if (ws == null) {
            logger.e(TAG, "WS SEND session/follow FAILED: webSocket is null!")
            return
        }
        val sent = ws.send(jsonStr)
        logger.i(TAG, "WS SEND session/follow sent=$sent (ws=${ws.hashCode()})")
        if (!sent) {
            logger.e(TAG, "WS SEND session/follow FAILED! webSocket state unknown. " +
                "The server may not support WebSocket for this endpoint.")
        }
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
        logger.i(TAG, "WS SEND session/prompt rpcId=$rpcId session=$sessionId content=${content.take(50)}")
        val jsonStr = gson.toJson(message)
        logger.d(TAG, "WS SEND body: ${jsonStr.take(500)}")
        val sent = webSocket?.send(jsonStr) ?: false
        logger.i(TAG, "WS SEND session/prompt sent=$sent")
    }

    fun confirmToolCall(sessionId: String, callId: String, approved: Boolean) {
        logger.i(TAG, "WS TOOL CONFIRM: callId=$callId approved=$approved session=$sessionId")
    }

    fun disconnect() {
        logger.i(TAG, "WS DISCONNECT: manual=$isManualDisconnect")
        isManualDisconnect = true
        coroutineScope.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        currentSessionId = null
        onEventCallback = null
    }

    private fun scheduleReconnect() {
        if (isManualDisconnect || reconnectAttempt >= maxReconnectAttempts) {
            logger.w(TAG, "WS RECONNECT skipped: manual=$isManualDisconnect attempts=$reconnectAttempt/$maxReconnectAttempts")
            return
        }
        reconnectAttempt++
        val delayMs = minOf(1000L * (1 shl (reconnectAttempt - 1)), 30_000L)
        logger.i(TAG, "WS RECONNECT scheduled in ${delayMs}ms (attempt $reconnectAttempt/$maxReconnectAttempts)")
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
        logger.i(TAG, "WS PARSE: follow stream has ${events.size()} events")
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
        val time = event.get("time")?.asLong
        logger.d(TAG, "WS EVENT: type=$eventType seq=$seq time=$time")

        when {
            eventType == "assistant/message" || eventType == "user/message" -> {
                val data = event.getAsJsonObject("data")
                if (data == null) {
                    logger.w(TAG, "WS EVENT $eventType: data is null")
                    return
                }
                val role = if (eventType == "assistant/message")
                    MessageRole.ASSISTANT else MessageRole.USER

                logger.d(TAG, "WS EVENT $eventType: data keys=${data.keySet()}")

                // content is a list: [{type:"text", text:"..."}, {type:"reasoning", text:"..."}, ...]
                val contentArray = if (eventType == "assistant/message") {
                    data.getAsJsonObject("message")?.getAsJsonArray("content")
                } else {
                    data.getAsJsonArray("content")
                }

                logger.d(TAG, "WS EVENT $eventType: contentArray size=${contentArray?.size()}")

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
                logger.d(TAG, "WS EVENT $eventType: parsed content=${content.take(80)} toolCalls=${toolCalls.size}")
                if (content.isNotBlank() && content != "null") {
                    val message = Message(
                        id = "msg-$seq",
                        sessionId = sessionId,
                        role = role,
                        content = content,
                        timestamp = event.get("time")?.asLong ?: System.currentTimeMillis(),
                        toolCalls = toolCalls
                    )
                    logger.i(TAG, "WS EMIT MessageReceived: role=$role content=${content.take(60)}")
                    onEvent(WebSocketEvent.MessageReceived(message))
                } else {
                    logger.w(TAG, "WS EVENT $eventType: content empty after parsing")
                }
            }
            eventType.contains("tool") -> {
                val data = event.get("data")
                logger.d(TAG, "WS EVENT $eventType: data=${data?.toString()?.take(200)}")
                if (data?.isJsonObject == true) {
                    val obj = data.asJsonObject
                    val toolCall = ToolCall(
                        id = obj.get("callId")?.asString ?: "tc-$seq",
                        toolName = obj.get("tool")?.asString ?: obj.get("name")?.asString ?: "unknown",
                        args = emptyMap()
                    )
                    logger.i(TAG, "WS EMIT ToolCallReceived: name=${toolCall.toolName} id=${toolCall.id}")
                    onEvent(WebSocketEvent.ToolCallReceived(toolCall))
                }
            }
            eventType.contains("status") || eventType.contains("agent") -> {
                val data = event.get("data")
                val statusStr = data?.asString ?: data?.toString()
                logger.d(TAG, "WS EVENT $eventType: data=$statusStr")
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
                logger.i(TAG, "WS EMIT AgentStatusChanged: $status")
                onEvent(WebSocketEvent.AgentStatusChanged(status))
                onEvent(WebSocketEvent.AgentStatusChanged(status))
            }
            else -> {
                Log.d(TAG, "Unhandled event type: $eventType")
            }
        }
    }
}
