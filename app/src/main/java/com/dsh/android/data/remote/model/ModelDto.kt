package com.dsh.android.data.remote.model

import com.dsh.android.domain.model.DshModel
import com.google.gson.annotations.SerializedName

data class ModelDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("status") val status: String?
) {
    fun toDomain() = DshModel(
        id = id,
        name = name,
        isAvailable = status != "unavailable"
    )
}
