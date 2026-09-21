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
