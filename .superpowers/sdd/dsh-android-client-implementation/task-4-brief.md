# Task 4: Network Layer - Retrofit API

## Objective
Create Retrofit API interface and authentication interceptor for network communication.

## Files to Create
- `app/src/main/java/com/dsh/android/data/remote/DshApi.kt`
- `app/src/main/java/com/dsh/android/data/remote/AuthInterceptor.kt`

## Requirements

### 1. AuthInterceptor.kt
```kotlin
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
```

### 2. DshApi.kt
```kotlin
package com.dsh.android.data.remote

import com.dsh.android.data.remote.model.*
import retrofit2.http.*

interface DshApi {

    @POST("dsh-webui-auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/sessions")
    suspend fun getSessions(): List<SessionDto>

    @POST("api/sessions")
    suspend fun createSession(@Body request: CreateSessionRequest): SessionDto

    @DELETE("api/sessions/{id}")
    suspend fun deleteSession(@Path("id") sessionId: String)

    @GET("api/sessions/search")
    suspend fun searchSessions(@Query("q") query: String): List<SessionDto>

    @GET("api/models")
    suspend fun getModels(): List<ModelDto>
}
```

## Dependencies
- Uses API models from Task 3: `LoginRequest`, `LoginResponse`, `SessionDto`, `ModelDto`, `CreateSessionRequest`
- Uses `DshPreferences` from Task 5 (will be created later)
- Uses OkHttp Interceptor interface
- Uses Hilt annotations for dependency injection

## Verification
- Verify all files have correct package declarations
- Verify API endpoints match the DSH server API specification
- Verify AuthInterceptor correctly adds session token to requests

## Commit
```bash
git add app/src/main/java/com/dsh/android/data/remote/DshApi.kt \
        app/src/main/java/com/dsh/android/data/remote/AuthInterceptor.kt
git commit -m "feat: add Retrofit API interface and auth interceptor"
```
