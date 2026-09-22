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
            val sessionsJson = result.getAsJsonArray("sessions")
                ?: return Result.success(emptyList())

            val sessions = sessionsJson.mapNotNull { element ->
                try {
                    val obj = element.asJsonObject
                    val sessionId = obj.get("sessionId")?.asString ?: return@mapNotNull null
                    val updatedAt = obj.get("updatedAt")?.asLong ?: 0L
                    val running = obj.get("running")?.asBoolean ?: false
                    val projections = obj.getAsJsonObject("projections")
                    val values = projections?.getAsJsonObject("values")
                    val title = values?.get("title")?.asString ?: sessionId

                    // Extract model from modelSelection
                    val modelSelection = values?.getAsJsonObject("modelSelection")
                    val lastUsed = modelSelection?.getAsJsonObject("lastUsed")
                    val model = lastUsed?.get("model")?.asString

                    Session(
                        id = sessionId,
                        title = title,
                        createdAt = updatedAt, // DSH only provides updatedAt
                        updatedAt = updatedAt,
                        model = model
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

    override suspend fun createSession(title: String): Result<Session> {
        return try {
            val args = JsonObject().apply {
                addProperty("path", "")
            }
            val result = rpcClient.call("session", "create", args)

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
            })
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
            val result = rpcClient.call("session", "modelCatalog", JsonObject())
            // The model catalog response structure: {"adapters":[...]}
            val adapters = result.getAsJsonArray("adapters") ?: return Result.success(emptyList())

            val models = mutableListOf<DshModel>()
            for (adapter in adapters) {
                val adapterObj = adapter.asJsonObject
                val adapterId = adapterObj.get("id")?.asString ?: continue
                val modelsArray = adapterObj.getAsJsonArray("models") ?: continue

                for (modelElement in modelsArray) {
                    val modelObj = modelElement.asJsonObject
                    val modelId = modelObj.get("id")?.asString ?: continue
                    val displayName = modelObj.get("displayName")?.asString
                        ?: modelObj.get("name")?.asString
                        ?: modelId

                    models.add(DshModel(
                        id = "$adapterId/$modelId",
                        name = displayName,
                        isAvailable = true
                    ))
                }
            }

            Log.d(TAG, "Got ${models.size} models from ${adapters.size()} adapters")
            Result.success(models)
        } catch (e: Exception) {
            Log.e(TAG, "getModels failed", e)
            Result.failure(Exception("获取模型列表失败：${e.message}"))
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
