package com.dsh.android.data.remote

import com.dsh.android.data.local.DshPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val preferences: DshPreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Skip auth for login endpoint
        if (original.url.encodedPath.contains("dsh-webui-auth/login")) {
            return chain.proceed(original)
        }

        val sessionToken = runBlocking { preferences.sessionToken.first() }

        return if (sessionToken != null) {
            val request = original.newBuilder()
                .addHeader("Cookie", "dsh_wua_session=$sessionToken")
                .build()
            chain.proceed(request)
        } else {
            chain.proceed(original)
        }
    }
}
