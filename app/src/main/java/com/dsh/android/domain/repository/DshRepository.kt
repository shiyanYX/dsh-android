package com.dsh.android.domain.repository

import com.dsh.android.domain.model.*
import kotlinx.coroutines.flow.Flow

interface DshRepository {
    val isConnected: Flow<Boolean>
    val webSocketEvents: Flow<WebSocketEvent>

    suspend fun login(serverAddress: String, username: String, password: String): Result<Unit>
    suspend fun logout()
    suspend fun getSessions(): Result<List<Session>>
    suspend fun createSession(title: String, cwd: String = ""): Result<Session>
    suspend fun deleteSession(sessionId: String): Result<Unit>
    suspend fun searchSessions(query: String): Result<List<Session>>
    suspend fun getModels(): Result<List<DshModel>>
    suspend fun getSessionHistory(sessionId: String, maxMessages: Int = 100): Result<List<Message>>
    suspend fun sendMessage(sessionId: String, content: String): Result<Unit>
    suspend fun confirmToolCall(sessionId: String, callId: String, approved: Boolean): Result<Unit>
    fun connectWebSocket(sessionId: String)
    fun disconnectWebSocket()
}
