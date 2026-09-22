package com.dsh.android.data.repository

import android.net.Uri
import android.util.Log
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
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "DshRepository"

/** Map DSH server error codes to user-friendly messages (aligned with web client). */
private fun friendlyError(error: String?): String = when (error) {
    "invalid" -> "用户名或密码错误"
    "rate-limited" -> "尝试次数过多，请一分钟后重试"
    "setup-token-required" -> "初始化令牌缺失或不正确"
    "weak-password" -> "密码强度不足：至少 8 位，需包含大小写字母、数字和特殊符号"
    "username-invalid" -> "用户名需为 3-32 位字母、数字、下划线或连字符"
    "not-configured" -> "凭据尚未配置，请刷新页面后重新创建"
    "already-configured" -> "认证已启用，请使用登录模式"
    else -> "登录失败：${error ?: "未知错误"}"
}

@Singleton
class DshRepositoryImpl @Inject constructor(
    private val api: DshApi,
    private val preferences: DshPreferences,
    private val wsClient: DshWebSocketClient,
    @Named("plain") private val plainHttpClient: OkHttpClient
) : DshRepository {

    private val _webSocketEvents = MutableSharedFlow<WebSocketEvent>(replay = 0)

    override val isConnected: Flow<Boolean> = preferences.sessionToken.map { it != null }
    override val webSocketEvents: Flow<WebSocketEvent> = _webSocketEvents

    override suspend fun login(serverAddress: String, username: String, password: String): Result<Unit> {
        return try {
            preferences.saveServerAddress(serverAddress)
            Log.d(TAG, "Logging in to $serverAddress as $username")

            val response = api.login(LoginRequest(username, password))
            if (response.ok) {
                // Extract redirect URL with launch token (format: "/?token=xxx")
                val redirect = response.redirect
                if (redirect.isNullOrBlank()) {
                    return Result.failure(Exception("服务器未返回重定向地址"))
                }

                val token = Uri.parse(redirect).getQueryParameter("token")
                if (token.isNullOrBlank()) {
                    return Result.failure(Exception("服务器未返回有效令牌"))
                }

                Log.d(TAG, "Login OK, exchanging token for session cookie via redirect: $redirect")

                // Step 2: Follow the redirect to get the dsh_wua_session cookie
                // The web client does: location.href = redirect
                // This triggers the core's token→cookie exchange
                val baseUrl = serverAddress.trimEnd('/')
                val fullRedirectUrl = if (redirect.startsWith("http")) {
                    redirect
                } else {
                    "$baseUrl$redirect"
                }

                try {
                    val cookieRequest = Request.Builder()
                        .url(fullRedirectUrl)
                        .get()
                        .build()
                    val cookieResponse = plainHttpClient.newCall(cookieRequest).execute()
                    val setCookies = cookieResponse.headers("Set-Cookie")
                    Log.d(TAG, "Cookie exchange: ${cookieResponse.code}, Set-Cookie count: ${setCookies.size}")

                    // Extract dsh_wua_session cookie
                    val sessionCookie = setCookies
                        .filter { it.startsWith("dsh_wua_session=") }
                        .map { it.split(";").first().removePrefix("dsh_wua_session=") }
                        .firstOrNull()

                    if (sessionCookie != null) {
                        Log.d(TAG, "Got session cookie: ${sessionCookie.take(20)}...")
                        preferences.saveSessionToken(sessionCookie)
                    } else {
                        // Fallback: use the launch token directly
                        Log.w(TAG, "No Set-Cookie in response, using launch token directly")
                        preferences.saveSessionToken(token)
                    }
                    cookieResponse.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Cookie exchange failed (${e.message}), using launch token")
                    preferences.saveSessionToken(token)
                }

                preferences.saveCredentials(username, password, true)
                Result.success(Unit)
            } else {
                Log.w(TAG, "Login failed: ${response.error}")
                Result.failure(Exception(friendlyError(response.error)))
            }
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
            val sessions = api.getSessions().map { it.toDomain() }
            Result.success(sessions)
        } catch (e: Exception) {
            Log.e(TAG, "getSessions failed", e)
            Result.failure(Exception("获取会话列表失败：${e.localizedMessage}"))
        }
    }

    override suspend fun createSession(title: String): Result<Session> {
        return try {
            val session = api.createSession(CreateSessionRequest(title)).toDomain()
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(Exception("创建会话失败：${e.localizedMessage}"))
        }
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> {
        return try {
            api.deleteSession(sessionId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("删除会话失败：${e.localizedMessage}"))
        }
    }

    override suspend fun searchSessions(query: String): Result<List<Session>> {
        return try {
            val sessions = api.searchSessions(query).map { it.toDomain() }
            Result.success(sessions)
        } catch (e: Exception) {
            Result.failure(Exception("搜索会话失败：${e.localizedMessage}"))
        }
    }

    override suspend fun getModels(): Result<List<DshModel>> {
        return try {
            val models = api.getModels().map { it.toDomain() }
            Result.success(models)
        } catch (e: Exception) {
            Result.failure(Exception("获取模型列表失败：${e.localizedMessage}"))
        }
    }

    override suspend fun sendMessage(sessionId: String, content: String): Result<Unit> {
        return try {
            wsClient.sendMessage(sessionId, content)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("发送消息失败：${e.localizedMessage}"))
        }
    }

    override suspend fun confirmToolCall(sessionId: String, callId: String, approved: Boolean): Result<Unit> {
        return try {
            wsClient.confirmToolCall(sessionId, callId, approved)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("工具确认失败：${e.localizedMessage}"))
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
