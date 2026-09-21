package com.dsh.android.domain.model

data class Session(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val model: String? = null
)
