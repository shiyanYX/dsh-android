package com.dsh.android.data.remote

import android.util.Log
import com.dsh.android.data.local.DshPreferences
import com.dsh.android.domain.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okio.ByteString
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "DshWebSocketClient"

/**
 * WebSocket client that communicates with DSH via the remote.mux endpoint.
 *
 * Protocol: Stream Multiplexing over WebSocket
 * Send (open):  {"type":"open","streamId":"<uuid>","endpoint":"session/follow","payload":{...}}
 * Send (cancel): {"type":"cancel","streamId":"<uuid>"}
 * Receive:      {"type":"item","streamId":"...","value":{...}}
 *               {"type":"end","streamId":"..."}
 *               {"type":"error","streamId":"...","error":{...}}
 *
 * session/prompt is sent via HTTP RPC (not WebSocket).
 * session/follow is the only WebSocket stream used for real-time events.
 */
@Singleton
class DshWebSocketClient @Inject constructor(
    private val preferences: DshPreferences,
    @Named("plain") private val httpClient: OkHttpClient,
    private val logger: com.dsh.android.util.DshLogger
) {
    // WebSocket needs its own client: readTimeout=0 (infinite), no callTimeout
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
    private val maxReconnectAttempts = 10
    private var coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Active logical streams (streamId → handler)
    private val activeStreams = ConcurrentHashMap<String, StreamHandler>()

    // Follow stream ID for current session
    private var followStreamId: String? = null

    private data class StreamHandler(
        val sessionId: String,
        val onEvent: (WebSocketEvent) -> Unit
    )

    fun connect(sessionId: String, onEvent: (WebSocketEvent) -> Unit) {
        logger.i(TAG, "WS CONNECT called for session=$sessionId (previous=$currentSessionId)")
        // Cancel any active follow stream for previous session
        cancelFollowStream()
        // Close any existing WebSocket first
        webSocket?.close(1000, "New session")
        webSocket = null
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

            logger.i(TAG, "Connecting to $wsUrl for session=$sessionId (attempt $reconnectAttempt)")
            logger.d(TAG, "Cookie: ${cookieHeader.take(80)}...")

            val request = Request.Builder()
                .url(wsUrl)
                .addHeader("Cookie", cookieHeader)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    logger.i(TAG, "WebSocket connected (HTTP ${response.code})")
                    // Don't reset reconnectAttempt here — only reset on successful follow
                    onEvent(WebSocketEvent.Connected(sessionId))
                    // Open follow stream for real-time events
                    openFollowStream(sessionId, onEvent)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    logger.d(TAG, "WS ← BINARY frame: ${bytes.size} bytes")
                    try {
                        val text = bytes.utf8()
                        handleTextMessage(text)
                    } catch (e: Exception) {
                        logger.w(TAG, "WS ← binary not valid UTF-8")
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleTextMessage(text)
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
     * Handle incoming text messages using Stream Multiplexing protocol.
     *
     * Server → Client messages:
     *   {"type":"item","streamId":"...","value":{...}}
     *   {"type":"end","streamId":"..."}
     *   {"type":"error","streamId":"...","error":{"code":"...","message":"...","details":{}}}
     */
    private fun handleTextMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val type = json.get("type")?.asString ?: run {
                logger.w(TAG, "WS ← NULL type! Full message: ${text.take(500)}")
                return
            }
            val streamId = json.get("streamId")?.asString
            logger.d(TAG, "WS ← type=$type streamId=$streamId (${text.length} bytes)")

            when (type) {
                "item" -> {
                    val value = json.getAsJsonObject("value")
                    if (value != null && streamId != null) {
                        handleStreamItem(streamId, value)
                    }
                }
                "end" -> {
                    if (streamId != null) {
                        handleStreamEnd(streamId)
                    }
                }
                "error" -> {
                    if (streamId != null) {
                        val error = json.getAsJsonObject("error")
                        val errCode = error?.get("code")?.asString
                        val errMsg = error?.get("message")?.asString
                        logger.e(TAG, "WS stream error: streamId=$streamId [$errCode] $errMsg")
                        handleStreamError(streamId, errCode, errMsg)
                    }
                }
                "ready" -> {
                    // Event stream ready — from $events endpoint
                    logger.i(TAG, "WS event stream ready: ${text.take(200)}")
                }
                else -> {
                    logger.w(TAG, "WS unknown message type: $type")
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "WS message parse error: ${text.take(200)}", e)
        }
    }

    /**
     * Handle an "item" frame from a logical stream.
     * For the follow stream, the value contains session events.
     */
    private fun handleStreamItem(streamId: String, value: JsonObject) {
        val handler = activeStreams[streamId]
        if (handler == null) {
            logger.d(TAG, "WS item for unknown stream: streamId=$streamId")
            return
        }
        logger.d(TAG, "WS item streamId=$streamId value keys=${value.keySet()}")

        // The value is a session/follow event record
        val eventType = value.get("type")?.asString
        if (eventType == null) {
            logger.d(TAG, "WS item has no type, keys=${value.keySet()}")
            return
        }

        parseFollowEvent(eventType, value, handler.sessionId, handler.onEvent)
    }

    /**
     * Handle "end" frame — stream closed normally.
     */
    private fun handleStreamEnd(streamId: String) {
        logger.i(TAG, "WS stream ended: streamId=$streamId")
        activeStreams.remove(streamId)
        if (streamId == followStreamId) {
            logger.w(TAG, "Follow stream ended unexpectedly")
            followStreamId = null
            // The server closed the follow stream — this shouldn't happen for a live session
            if (!isManualDisconnect) {
                scheduleReconnect()
            }
        }
    }

    /**
     * Handle "error" frame — stream failed.
     */
    private fun handleStreamError(streamId: String, code: String?, message: String?) {
        logger.e(TAG, "WS stream error: streamId=$streamId code=$code message=$message")
        activeStreams.remove(streamId)
        if (streamId == followStreamId) {
            followStreamId = null
            onEventCallback?.invoke(WebSocketEvent.Error(Exception("Stream error: [$code] $message")))
            if (!isManualDisconnect) {
                scheduleReconnect()
            }
        }
    }

    /**
     * Open a follow stream for the given session using Stream Multiplexing protocol.
     *
     * Send: {"type":"open","streamId":"<uuid>","endpoint":"session/follow","payload":{"args":{"request":{...}}}}
     */
    private fun openFollowStream(sessionId: String, onEvent: (WebSocketEvent) -> Unit) {
        val streamId = UUID.randomUUID().toString()
        followStreamId = streamId

        // Register handler
        activeStreams[streamId] = StreamHandler(sessionId, onEvent)

        val message = JsonObject().apply {
            addProperty("type", "open")
            addProperty("streamId", streamId)
            addProperty("endpoint", "session/follow")
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
        logger.i(TAG, "WS OPEN follow streamId=$streamId session=$sessionId (${jsonStr.length} bytes)")
        logger.d(TAG, "WS OPEN body: ${jsonStr.take(500)}")
        val ws = webSocket
        if (ws == null) {
            logger.e(TAG, "WS OPEN follow FAILED: webSocket is null!")
            return
        }
        val sent = ws.send(jsonStr)
        logger.i(TAG, "WS OPEN follow sent=$sent (ws=${ws.hashCode()})")
        if (sent) {
            // Reset reconnect attempt on successful send (server will validate)
            reconnectAttempt = 0
        }
    }

    /**
     * Cancel the active follow stream.
     */
    private fun cancelFollowStream() {
        val streamId = followStreamId ?: return
        followStreamId = null
        activeStreams.remove(streamId)

        val ws = webSocket ?: return

        val message = JsonObject().apply {
            addProperty("type", "cancel")
            addProperty("streamId", streamId)
        }
        val sent = ws.send(gson.toJson(message))
        logger.i(TAG, "WS CANCEL follow streamId=$streamId sent=$sent")
    }

    /**
     * Send session/prompt via HTTP RPC (not WebSocket).
     * This is called from the repository.
     */
    fun sendMessage(sessionId: String, content: String): Boolean {
        // session/prompt is sent via HTTP RPC, not WebSocket
        // This method now returns false to indicate caller should use HTTP RPC
        logger.i(TAG, "WS sendMessage called — but session/prompt should use HTTP RPC")
        return false
    }

    fun confirmToolCall(sessionId: String, callId: String, approved: Boolean) {
        logger.i(TAG, "WS TOOL CONFIRM: callId=$callId approved=$approved session=$sessionId")
    }

    fun disconnect() {
        logger.i(TAG, "WS DISCONNECT: manual=$isManualDisconnect")
        isManualDisconnect = true
        reconnectAttempt = maxReconnectAttempts
        cancelFollowStream()
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
        val delayMs = minOf(2000L * (1 shl (reconnectAttempt - 1)), 60_000L)
        logger.i(TAG, "WS RECONNECT scheduled in ${delayMs}ms (attempt $reconnectAttempt/$maxReconnectAttempts)")
        coroutineScope.launch {
            delay(delayMs)
            performConnect()
        }
    }

    /**
     * Parse a follow event from the session/follow stream.
     * Event types: "assistant/message", "user/message", "turn/start", "step/start", etc.
     */
    private fun parseFollowEvent(
        eventType: String,
        event: JsonObject,
        sessionId: String,
        onEvent: (WebSocketEvent) -> Unit
    ) {
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
            }
            else -> {
                logger.d(TAG, "WS unhandled event type: $eventType")
            }
        }
    }
}
