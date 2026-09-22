package com.dsh.android.data.remote

import android.util.Log
import com.dsh.android.data.local.DshPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthInterceptor"

@Singleton
class AuthInterceptor @Inject constructor(
    private val preferences: DshPreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath

        // Skip auth for login/setup endpoints
        if (path.contains("dsh-webui-auth")) {
            return chain.proceed(original)
        }

        val sessionToken = runBlocking { preferences.sessionToken.first() }
        val coreCookie = runBlocking { preferences.coreCookie.first() }

        return if (sessionToken != null) {
            val cookieHeader = buildString {
                append("dsh_wua_session=$sessionToken")
                if (!coreCookie.isNullOrBlank()) {
                    append("; $coreCookie")
                }
            }
            val request = original.newBuilder()
                .addHeader("Cookie", cookieHeader)
                .build()
            Log.d(TAG, "Request: ${original.method} $path with cookies")
            chain.proceed(request)
        } else {
            Log.w(TAG, "Request: ${original.method} $path WITHOUT session token!")
            chain.proceed(original)
        }
    }
}
