package com.dsh.android.domain.model

data class Favorite(
    val sessionId: String,
    val serverAddress: String,
    val title: String,
    val model: String?,
    val favoritedAt: Long = System.currentTimeMillis()
)
