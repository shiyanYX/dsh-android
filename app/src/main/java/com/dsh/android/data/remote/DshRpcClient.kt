package com.dsh.android.data.remote

import android.util.Log
import com.dsh.android.data.local.DshPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "DshRpcClient"

/**
 * DSH RPC client implementing the Host API protocol.
 *
 * Protocol: POST /api/{namespace}/{method}
 * Request:  {"type":"client-request","rpcId":"<uuid>","method":"{ns}/{method}","payload":{"args":{"_request":{...}}}}
 * Response: {"type":"server-response","rpcId":"<uuid>","result":{"ok":true,"value":{...}}}
 *
 * Requires TWO cookies:
 * - dsh_wua_session  (from login response Set-Cookie)
 * - dsh-auth-*       (from core token->cookie exchange on GET /?token=xxx)
 */
@Singleton
class DshRpcClient @Inject constructor(
    private val preferences: DshPreferences,
    @Named("plain") private val httpClient: OkHttpClient,
    private val logger: com.dsh.android.util.DshLogger
) {
    private val gson = Gson()
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    /**
     * Two-step authentication:
     * 1. POST /dsh-webui-auth/login -> gets dsh_wua_session cookie
     * 2. GET /?token=xxx -> gets dsh-auth-* cookie (core Connection auth)
     */
    suspend fun authenticate(serverAddress: String, username: String, password: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = serverAddress.trimEnd('/')

                // Step 1: Login
                Log.d(TAG, "Step 1: Login to $baseUrl")
                val loginBody = gson.toJson(mapOf("username" to username, "password" to password))
                val loginRequest = Request.Builder()
                    .url("$baseUrl/dsh-webui-auth/login")
                    .post(loginBody.toRequestBody(JSON_MEDIA))
                    .build()

                val loginResponse = httpClient.newCall(loginRequest).execute()
                val loginResponseBody = loginResponse.body?.string() ?: ""
                loginResponse.close()

                val loginJson = JsonParser.parseString(loginResponseBody).asJsonObject
                val isOk = loginJson.get("ok")?.asBoolean ?: false
                if (!isOk) {
                    val error = loginJson.get("error")?.asString
                    return@withContext Result.failure(Exception(friendlyError(error)))
                }

                // Extract dsh_wua_session from Set-Cookie header
                val wuaSession = loginResponse.header("Set-Cookie")
                    ?.split(";")
                    ?.firstOrNull { it.trim().startsWith("dsh_wua_session=") }
                    ?.substringAfter("dsh_wua_session=")
                    ?.trim()

                if (wuaSession.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("登录响应未包含会话 cookie"))
                }
                Log.d(TAG, "Got dsh_wua_session: ${wuaSession.take(20)}...")

                // Step 2: Token exchange
                val redirect = loginJson.get("redirect")?.asString
                    ?: return@withContext Result.failure(Exception("服务器未返回重定向地址"))

                val fullRedirectUrl = if (redirect.startsWith("http")) redirect else "$baseUrl$redirect"
                Log.d(TAG, "Step 2: Token exchange via $fullRedirectUrl")

                val exchangeRequest = Request.Builder()
                    .url(fullRedirectUrl)
                    .addHeader("Cookie", "dsh_wua_session=$wuaSession")
                    .get()
                    .build()

                val exchangeResponse = httpClient.newCall(exchangeRequest).execute()
                val allSetCookies = exchangeResponse.headers("Set-Cookie")
                Log.d(TAG, "Token exchange: ${exchangeResponse.code}, Set-Cookie count: ${allSetCookies.size}")
                for (c in allSetCookies) {
                    Log.d(TAG, "  Set-Cookie: ${c.take(80)}...")
                }

                // Extract dsh-auth-* cookie
                val coreAuthCookie = allSetCookies
                    .firstOrNull { it.startsWith("dsh-auth-") }
                    ?.split(";")
                    ?.firstOrNull()
                    ?.let { cookie ->
                        val eq = cookie.indexOf('=')
                        if (eq > 0) cookie.substring(0, eq) + "=" + cookie.substring(eq + 1)
                        else null
                    }

                exchangeResponse.close()

                // Follow the 303 redirect to / to complete the exchange
                val followRequest = Request.Builder()
                    .url(baseUrl + "/")
                    .addHeader("Cookie", "dsh_wua_session=$wuaSession" +
                        if (coreAuthCookie != null) "; $coreAuthCookie" else "")
                    .get()
                    .build()
                val followResponse = httpClient.newCall(followRequest).execute()
                followResponse.close()

                // Store both cookies
                preferences.saveSessionToken(wuaSession)
                if (coreAuthCookie != null) {
                    Log.d(TAG, "Got core auth cookie: ${coreAuthCookie.take(50)}...")
                    preferences.saveCoreCookie(coreAuthCookie)
                } else {
                    Log.w(TAG, "No dsh-auth-* cookie found in token exchange response")
                }

                Log.d(TAG, "Authentication complete")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Authentication failed", e)
                Result.failure(Exception("连接失败：${e.localizedMessage ?: "网络错误"}"))
            }
        }
    }

    /**
     * Make an RPC call to the DSH server.
     * @param wireKey the argument wrapper key: "_request" for most endpoints, "request" for prompt/follow/rename
     */
    suspend fun call(namespace: String, method: String, args: JsonObject = JsonObject(), wireKey: String = "_request"): JsonObject {
        return withContext(Dispatchers.IO) {
            val serverAddress = preferences.serverAddress.first()
                ?: throw IllegalStateException("Not connected to server")
            val wuaSession = preferences.sessionToken.first()
                ?: throw IllegalStateException("Not authenticated")
            val coreCookie = preferences.coreCookie.first()
            executeCallRaw(serverAddress, wuaSession, coreCookie, namespace, method, args, wireKey)
        }
    }

    /**
     * Call an RPC endpoint and return the raw server response JSON.
     */
    suspend fun callRaw(namespace: String, method: String, args: JsonObject = JsonObject(), wireKey: String = "_request"): JsonObject {
        return withContext(Dispatchers.IO) {
            val serverAddress = preferences.serverAddress.first()
                ?: throw IllegalStateException("Not connected to server")
            val wuaSession = preferences.sessionToken.first()
                ?: throw IllegalStateException("Not authenticated")
            val coreCookie = preferences.coreCookie.first()
            executeCallRaw(serverAddress, wuaSession, coreCookie, namespace, method, args, wireKey)
        }
    }

    private fun executeCallRaw(
        serverAddress: String,
        wuaSession: String,
        coreCookie: String?,
        namespace: String,
        method: String,
        args: JsonObject = JsonObject(),
        wireKey: String = "_request"
    ): JsonObject {
        val rpcId = UUID.randomUUID().toString()
        val endpoint = "$namespace/$method"

        val payload = JsonObject().apply {
            add("args", JsonObject().apply {
                add(wireKey, args)
            })
        }
        val body = JsonObject().apply {
            addProperty("type", "client-request")
            addProperty("rpcId", rpcId)
            addProperty("method", endpoint)
            add("payload", payload)
        }

        val cookieHeader = buildString {
            append("dsh_wua_session=$wuaSession")
            if (!coreCookie.isNullOrBlank()) {
                append("; $coreCookie")
            }
        }

        val url = "${serverAddress.trimEnd('/')}/api/$endpoint"
        val request = Request.Builder()
            .url(url)
            .post(gson.toJson(body).toRequestBody(JSON_MEDIA))
            .addHeader("Cookie", cookieHeader)
            .build()

        logger.i(TAG, "RPC POST $endpoint rpcId=$rpcId wireKey=$wireKey")
        logger.d(TAG, "Request body: ${gson.toJson(body).take(1000)}")
        logger.d(TAG, "Cookie header: ${cookieHeader.take(80)}...")

        val startTime = System.currentTimeMillis()
        val response = httpClient.newCall(request).execute()
        val durationMs = System.currentTimeMillis() - startTime
        val httpCode = response.code
        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        response.close()

        logger.d(TAG, "Response HTTP $httpCode (${durationMs}ms, ${responseBody.length} bytes)")
        logger.d(TAG, "Response body: ${responseBody.take(2000)}")

        val json = JsonParser.parseString(responseBody).asJsonObject

        val result = json.getAsJsonObject("result")
            ?: throw Exception("Invalid RPC response (no 'result' key): ${responseBody.take(500)}")

        if (result.get("ok")?.asBoolean == true) {
            val value = result.getAsJsonObject("value") ?: JsonObject()
            logger.i(TAG, "RPC OK $endpoint (${durationMs}ms)")
            logger.d(TAG, "RPC value keys: ${value.keySet()}")
            return value
        } else {
            val error = result.getAsJsonObject("error")
            val code = error?.get("code")?.asString ?: "unknown"
            val message = error?.get("message")?.asString ?: "Unknown error"
            val details = error?.get("details")?.toString() ?: ""
            logger.e(TAG, "RPC ERROR $endpoint [$code]: $message")
            if (details.isNotBlank()) logger.d(TAG, "Error details: $details")
            throw Exception("RPC error [$code]: $message")
        }
    }

    /** Build cookie header string for WebSocket or other connections. */
    suspend fun buildCookieHeader(): String {
        val wuaSession = preferences.sessionToken.first() ?: ""
        val coreCookie = preferences.coreCookie.first() ?: ""
        return buildString {
            append("dsh_wua_session=$wuaSession")
            if (coreCookie.isNotBlank()) append("; $coreCookie")
        }
    }

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
}
