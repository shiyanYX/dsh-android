package com.dsh.android.data.repository

import android.util.Log
import com.dsh.android.data.local.DshPreferences
import com.dsh.android.data.remote.DshRpcClient
import com.dsh.android.data.remote.DshWebSocketClient
import com.dsh.android.domain.model.*
import com.dsh.android.domain.repository.DshRepository
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DshRepository"

@Singleton
class DshRepositoryImpl @Inject constructor(
    private val rpcClient: DshRpcClient,
    private val preferences: DshPreferences,
    private val wsClient: DshWebSocketClient
) : DshRepository {

    private val _webSocketEvents = MutableSharedFlow<WebSocketEvent>(replay = 0)

    override val isConnected: Flow<Boolean> = preferences.sessionToken.map { it != null }
    override val webSocketEvents: Flow<WebSocketEvent> = _webSocketEvents

    override suspend fun login(serverAddress: String, username: String, password: String): Result<Unit> {
        return try {
            preferences.saveServerAddress(serverAddress)
            Log.d(TAG, "Logging in to $serverAddress as $username")

            val result = rpcClient.authenticate(serverAddress, username, password)
            if (result.isSuccess) {
                preferences.saveCredentials(username, password, true)
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Login exception", e)
            Result.failure(Exception("连接失败：${e.localizedMessage ?: "网络错误"}"))
        }
    }

    override suspend fun logout() {
        preferences.clearSession()
        wsClient.disconnect()
    }

    override suspend fun getSessions(): Result<List<Session>> {
        return try {
            val result = rpcClient.call("session", "list", JsonObject())
            // Response: {items: [{sessionId, updatedAt, running, projections: {values: {title, ...}}}, ...]}
            val sessionsJson = result.getAsJsonArray("items")
                ?: result.getAsJsonArray("sessions")
                ?: return Result.success(emptyList())

            val sessions = sessionsJson.mapNotNull { element ->
                try {
                    val obj = element.asJsonObject
                    val sessionId = obj.get("sessionId")?.asString ?: return@mapNotNull null
                    val updatedAt = obj.get("updatedAt")?.asLong ?: 0L
                    val running = obj.get("running")?.asBoolean ?: false
                    val cwd = obj.get("cwd")?.asString ?: ""
                    val projections = obj.getAsJsonObject("projections")
                    val values = projections?.getAsJsonObject("values")

                    // Handle title: may be missing, null (JsonNull), or a real string
                    val titleElement = values?.get("title")
                    val title = when {
                        titleElement == null -> sessionId
                        titleElement.isJsonNull -> sessionId
                        else -> titleElement.asString.ifBlank { sessionId }
                    }

                    // Extract model from modelSelection
                    val modelSelection = values?.getAsJsonObject("modelSelection")
                    val lastUsed = modelSelection?.getAsJsonObject("lastUsed")
                    val model = lastUsed?.get("model")?.asString

                    // Extract subagent info (handle JsonNull safely)
                    val subagentElement = values?.get("subagent")
                    val subagent = if (subagentElement != null && !subagentElement.isJsonNull)
                        subagentElement.asJsonObject else null
                    val isSubagent = subagent != null
                    val subagentLabel = subagent?.get("label")?.asString
                    val subagentMode = subagent?.get("mode")?.asString

                    // Extract turn count
                    val sessionStats = values?.getAsJsonObject("sessionStats")
                    val turnCount = sessionStats?.get("turns")?.asInt ?: 0

                    Session(
                        id = sessionId,
                        title = title,
                        createdAt = updatedAt,
                        updatedAt = updatedAt,
                        model = model,
                        cwd = cwd,
                        running = running,
                        isSubagent = isSubagent,
                        subagentLabel = subagentLabel,
                        subagentMode = subagentMode,
                        turnCount = turnCount
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse session: ${e.message}")
                    null
                }
            }

            Log.d(TAG, "Got ${sessions.size} sessions")
            Result.success(sessions)
        } catch (e: Exception) {
            Log.e(TAG, "getSessions failed", e)
            Result.failure(Exception("获取会话列表失败：${e.message}"))
        }
    }

    override suspend fun createSession(title: String, cwd: String): Result<Session> {
        return try {
            val args = JsonObject().apply {
                if (cwd.isNotBlank()) {
                    addProperty("cwd", cwd)
                }
            }
            Log.d(TAG, "createSession: cwd=$cwd")
            val result = rpcClient.call("session", "create", args, wireKey = "request")

            val sessionId = result.get("sessionId")?.asString
                ?: throw Exception("No sessionId in response")

            val session = Session(
                id = sessionId,
                title = title.ifBlank { sessionId },
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                model = null
            )
            Result.success(session)
        } catch (e: Exception) {
            Log.e(TAG, "createSession failed", e)
            Result.failure(Exception("创建会话失败：${e.message}"))
        }
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> {
        return try {
            rpcClient.call("session", "cancel", JsonObject().apply {
                addProperty("sessionId", sessionId)
            }, wireKey = "request")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteSession failed", e)
            Result.failure(Exception("删除会话失败：${e.message}"))
        }
    }

    override suspend fun searchSessions(query: String): Result<List<Session>> {
        // DSH doesn't have a dedicated search endpoint; filter locally
        return try {
            val allSessions = getSessions().getOrElse { emptyList() }
            val filtered = if (query.isBlank()) allSessions
            else allSessions.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.id.contains(query, ignoreCase = true)
            }
            Result.success(filtered)
        } catch (e: Exception) {
            Result.failure(Exception("搜索会话失败：${e.message}"))
        }
    }

    override suspend fun getModels(): Result<List<DshModel>> {
        return try {
            // modelCatalog has no parameters (parameters: [] in descriptor)
            val result = rpcClient.call("session", "modelCatalog", JsonObject())
            // Response: {groups: [{id, name, models: [{id, name}]}], default: {provider, model}}
            val groups = result.getAsJsonArray("groups") ?: return Result.success(emptyList())

            val models = mutableListOf<DshModel>()
            for (group in groups) {
                val groupObj = group.asJsonObject
                val groupId = groupObj.get("id")?.asString ?: continue
                val groupName = groupObj.get("name")?.asString ?: groupId
                val modelsArray = groupObj.getAsJsonArray("models") ?: continue

                for (modelElement in modelsArray) {
                    val modelObj = modelElement.asJsonObject
                    val modelId = modelObj.get("id")?.asString ?: continue
                    val displayName = modelObj.get("name")?.asString ?: modelId

                    models.add(DshModel(
                        id = "$groupId/$modelId",
                        name = "$displayName ($groupName)",
                        isAvailable = true
                    ))
                }
            }

            Log.d(TAG, "Got ${models.size} models from ${groups.size()} groups")
            Result.success(models)
        } catch (e: Exception) {
            Log.e(TAG, "getModels failed", e)
            Result.failure(Exception("获取模型列表失败：${e.message}"))
        }
    }

    override suspend fun getSessionHistory(sessionId: String, maxMessages: Int): Result<List<Message>> {
        return try {
            // Step 1: Get throughSeq from session list
            Log.d(TAG, "getSessionHistory: fetching session list for $sessionId")
            val listResult = rpcClient.call("session", "list", JsonObject())
            val items = listResult.getAsJsonArray("items")
                ?: return Result.failure(Exception("会话列表为空"))
            var throughSeq = 0
            for (item in items) {
                val obj = item.asJsonObject
                if (obj.get("sessionId")?.asString == sessionId) {
                    throughSeq = obj.getAsJsonObject("projections")
                        ?.get("asOfSeq")?.asInt ?: 0
                    Log.d(TAG, "getSessionHistory: found session, throughSeq=$throughSeq")
                    break
                }
            }
            if (throughSeq == 0) {
                Log.w(TAG, "getSessionHistory: session $sessionId not found or asOfSeq=0")
                return Result.failure(Exception("会话未找到"))
            }

            // Step 2: Load page of events via HTTP (session/page is non-streaming)
            val args = JsonObject().apply {
                add("address", JsonObject().apply {
                    addProperty("kind", "session")
                    addProperty("sessionId", sessionId)
                })
                addProperty("throughSeq", throughSeq)
                addProperty("maxMessages", maxMessages)
            }
            Log.d(TAG, "getSessionHistory: calling session/page with maxMessages=$maxMessages")
            val result = rpcClient.call("session", "page", args, wireKey = "request")
            val records = result.getAsJsonArray("records")
                ?: return Result.failure(Exception("消息记录为空"))

            Log.d(TAG, "getSessionHistory: got ${records.size()} records")

            val messages = mutableListOf<Message>()
            var currentAssistantContent = StringBuilder()
            var currentAssistantId = ""
            var currentToolCalls = mutableListOf<ToolCall>()

            for (record in records) {
                val recObj = record.asJsonObject
                val event = recObj.getAsJsonObject("event") ?: continue
                val eventType = event.get("type")?.asString ?: continue
                val data = event.getAsJsonObject("data")
                val seq = event.get("seq")?.asLong ?: 0
                val time = event.get("time")?.asLong ?: System.currentTimeMillis()

                when (eventType) {
                    "assistant/message" -> {
                        // Flush previous assistant message if any
                        if (currentAssistantContent.isNotEmpty()) {
                            messages.add(Message(
                                id = currentAssistantId,
                                sessionId = sessionId,
                                role = MessageRole.ASSISTANT,
                                content = currentAssistantContent.toString().trim(),
                                timestamp = time,
                                toolCalls = currentToolCalls.toList()
                            ))
                            currentAssistantContent = StringBuilder()
                            currentToolCalls = mutableListOf()
                        }
                        currentAssistantId = "msg-$seq"
                        // content is a list: [{type:"reasoning", text:"..."}, {type:"text", text:"..."}, {type:"tool-call", ...}]
                        val msgObj = data?.getAsJsonObject("message")
                        val contentArray = msgObj?.getAsJsonArray("content")
                        if (contentArray != null) {
                            for (item in contentArray) {
                                val itemObj = item.asJsonObject
                                val itemType = itemObj.get("type")?.asString ?: ""
                                when (itemType) {
                                    "reasoning", "text" -> {
                                        val text = itemObj.get("text")?.asString ?: ""
                                        if (text.isNotBlank()) {
                                            if (currentAssistantContent.isNotEmpty()) currentAssistantContent.append("\n")
                                            currentAssistantContent.append(text)
                                        }
                                    }
                                    "tool-call" -> {
                                        val toolCall = ToolCall(
                                            id = itemObj.get("id")?.asString ?: "tc-$seq",
                                            toolName = itemObj.get("name")?.asString ?: "unknown",
                                            args = emptyMap()
                                        )
                                        currentToolCalls.add(toolCall)
                                    }
                                }
                            }
                        } else {
                            // Fallback: content might be a string (older format)
                            val contentStr = msgObj?.get("content")?.asString
                                ?: data?.get("content")?.asString
                                ?: ""
                            if (contentStr.isNotBlank()) {
                                currentAssistantContent.append(contentStr)
                            }
                        }
                    }
                    "user/message" -> {
                        // Flush previous assistant message
                        if (currentAssistantContent.isNotEmpty()) {
                            messages.add(Message(
                                id = currentAssistantId,
                                sessionId = sessionId,
                                role = MessageRole.ASSISTANT,
                                content = currentAssistantContent.toString().trim(),
                                timestamp = time,
                                toolCalls = currentToolCalls.toList()
                            ))
                            currentAssistantContent = StringBuilder()
                            currentToolCalls = mutableListOf()
                        }
                        // user/message: content is at data.content (list), NOT data.message.content
                        val contentArray = data?.getAsJsonArray("content")
                        val textParts = mutableListOf<String>()
                        if (contentArray != null) {
                            for (item in contentArray) {
                                val itemObj = item.asJsonObject
                                val text = itemObj.get("text")?.asString ?: ""
                                if (text.isNotBlank()) textParts.add(text)
                            }
                        } else {
                            // Fallback: content might be a string
                            val contentStr = data?.get("content")?.asString ?: ""
                            if (contentStr.isNotBlank()) textParts.add(contentStr)
                        }
                        val content = textParts.joinToString("\n")
                        if (content.isNotBlank()) {
                            messages.add(Message(
                                id = "msg-$seq",
                                sessionId = sessionId,
                                role = MessageRole.USER,
                                content = content,
                                timestamp = time
                            ))
                        }
                    }
                    "tool/call" -> {
                        // tool/call events from the step-level (separate from message content)
                        val toolCall = ToolCall(
                            id = data?.get("callId")?.asString ?: "tc-$seq",
                            toolName = data?.get("name")?.asString ?: "unknown",
                            args = emptyMap()
                        )
                        currentToolCalls.add(toolCall)
                    }
                    "step/end" -> {
                        // Step end signals end of a tool sequence
                    }
                }
            }

            // Flush any remaining assistant message
            if (currentAssistantContent.isNotEmpty()) {
                messages.add(Message(
                    id = currentAssistantId,
                    sessionId = sessionId,
                    role = MessageRole.ASSISTANT,
                    content = currentAssistantContent.toString().trim(),
                    timestamp = System.currentTimeMillis(),
                    toolCalls = currentToolCalls.toList()
                ))
            }

            Log.d(TAG, "Loaded ${messages.size} messages from session $sessionId")
            Result.success(messages)
        } catch (e: Exception) {
            Log.e(TAG, "getSessionHistory failed", e)
            Result.failure(Exception("加载聊天历史失败：${e.message}"))
        }
    }

    override suspend fun sendMessage(sessionId: String, content: String): Result<Unit> {
        return try {
            wsClient.sendMessage(sessionId, content)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("发送消息失败：${e.message}"))
        }
    }

    override suspend fun confirmToolCall(sessionId: String, callId: String, approved: Boolean): Result<Unit> {
        return try {
            wsClient.confirmToolCall(sessionId, callId, approved)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("工具确认失败：${e.message}"))
        }
    }

    override fun connectWebSocket(sessionId: String) {
        wsClient.connect(sessionId) { event ->
            _webSocketEvents.tryEmit(event)
        }
    }

    override fun disconnectWebSocket() {
        wsClient.disconnect()
    }
}
