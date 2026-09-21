package com.dsh.android.data.repository

import android.net.Uri
import com.dsh.android.data.local.DshPreferences
import com.dsh.android.data.remote.DshApi
import com.dsh.android.data.remote.DshWebSocketClient
import com.dsh.android.data.remote.model.CreateSessionRequest
import com.dsh.android.data.remote.model.LoginRequest
import com.dsh.android.domain.model.*
import com.dsh.android.domain.repository.DshRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DshRepositoryImpl @Inject constructor(
    private val api: DshApi,
    private val preferences: DshPreferences,
    private val wsClient: DshWebSocketClient
) : DshRepository {

    private val _webSocketEvents = MutableSharedFlow<WebSocketEvent>(replay = 0)

    override val isConnected: Flow<Boolean> = preferences.sessionToken.map { it != null }
    override val webSocketEvents: Flow<WebSocketEvent> = _webSocketEvents

    override suspend fun login(serverAddress: String, username: String, password: String): Result<Unit> {
        return try {
            preferences.saveServerAddress(serverAddress)
            val response = api.login(LoginRequest(username, password))
            if (response.ok) {
                // Extract token from redirect URL (format: "/?token=xxx")
                val token = response.redirect?.let { redirect ->
                    Uri.parse(redirect).getQueryParameter("token")
                }
                if (token.isNullOrBlank()) {
                    return Result.failure(Exception("No token received from server"))
                }
                preferences.saveSessionToken(token)
                preferences.saveCredentials(username, password, true)
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.error ?: "Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        preferences.clearSession()
        wsClient.disconnect()
    }

    override suspend fun getSessions(): Result<List<Session>> {
        return try {
            val sessions = api.getSessions().map { it.toDomain() }
            Result.success(sessions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createSession(title: String): Result<Session> {
        return try {
            val session = api.createSession(CreateSessionRequest(title)).toDomain()
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> {
        return try {
            api.deleteSession(sessionId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchSessions(query: String): Result<List<Session>> {
        return try {
            val sessions = api.searchSessions(query).map { it.toDomain() }
            Result.success(sessions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getModels(): Result<List<DshModel>> {
        return try {
            val models = api.getModels().map { it.toDomain() }
            Result.success(models)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendMessage(sessionId: String, content: String): Result<Unit> {
        return try {
            wsClient.sendMessage(sessionId, content)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun confirmToolCall(sessionId: String, callId: String, approved: Boolean): Result<Unit> {
        return try {
            wsClient.confirmToolCall(sessionId, callId, approved)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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
