# Task 6: Repository Interface & Implementation

## Objective
Create repository interface and implementation for data access layer.

## Files to Create
- `app/src/main/java/com/dsh/android/domain/repository/DshRepository.kt`
- `app/src/main/java/com/dsh/android/data/repository/DshRepositoryImpl.kt`

## Requirements

### 1. DshRepository.kt (Interface)
```kotlin
package com.dsh.android.domain.repository

import com.dsh.android.domain.model.*
import kotlinx.coroutines.flow.Flow

interface DshRepository {
    val isConnected: Flow<Boolean>
    val webSocketEvents: Flow<WebSocketEvent>

    suspend fun login(serverAddress: String, username: String, password: String): Result<Unit>
    suspend fun logout()
    suspend fun getSessions(): Result<List<Session>>
    suspend fun createSession(title: String): Result<Session>
    suspend fun deleteSession(sessionId: String): Result<Unit>
    suspend fun searchSessions(query: String): Result<List<Session>>
    suspend fun getModels(): Result<List<DshModel>>
    suspend fun sendMessage(sessionId: String, content: String): Result<Unit>
    suspend fun confirmToolCall(sessionId: String, callId: String, approved: Boolean): Result<Unit>
    fun connectWebSocket(sessionId: String)
    fun disconnectWebSocket()
}
```

### 2. DshRepositoryImpl.kt (Implementation)
```kotlin
package com.dsh.android.data.repository

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
                preferences.saveSessionToken("authenticated")
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
```

## Dependencies
- Uses `DshApi` from Task 4
- Uses `DshPreferences` from Task 5
- Uses `DshWebSocketClient` from Task 7 (will be created later)
- Uses domain models from Task 2
- Uses API models from Task 3

## Verification
- Verify both files have correct package declarations
- Verify DshRepository interface defines all required methods
- Verify DshRepositoryImpl implements all interface methods
- Verify dependency injection with @Inject constructor

## Commit
```bash
git add app/src/main/java/com/dsh/android/domain/repository/ \
        app/src/main/java/com/dsh/android/data/repository/
git commit -m "feat: add repository interface and implementation"
```
