package com.dsh.android.data.remote.model

import com.dsh.android.domain.model.Session
import com.google.gson.annotations.SerializedName

data class SessionDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("updatedAt") val updatedAt: Long,
    @SerializedName("model") val model: String?
) {
    fun toDomain() = Session(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        model = model
    )
}
