package com.dsh.android.data.remote

import com.dsh.android.data.local.DshPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor that rewrites request URLs to point at the actual server address
 * stored in [DshPreferences].  The Retrofit instance uses a placeholder base URL
 * ("http://localhost/"), and this interceptor replaces it at request time with the
 * real server address the user configured on the Connection screen.
 */
@Singleton
class DynamicBaseUrlInterceptor @Inject constructor(
    private val preferences: DshPreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Read the current server address synchronously (safe on OkHttp dispatcher thread)
        val serverAddress = runBlocking { preferences.serverAddress.first() }

        if (serverAddress.isNullOrBlank()) {
            return chain.proceed(original)
        }

        val baseUrl = serverAddress.trimEnd('/').toHttpUrlOrNull()
            ?: return chain.proceed(original) // malformed — let it fail downstream

        // Rebuild the request URL against the real server
        val newUrl = original.url.newBuilder()
            .scheme(baseUrl.scheme)
            .host(baseUrl.host)
            .port(baseUrl.port)
            .build()

        val newRequest = original.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}
