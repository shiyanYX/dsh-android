package com.dsh.android.data.remote.model

import com.google.gson.annotations.SerializedName

data class CreateSessionRequest(
    @SerializedName("title") val title: String = "New Session"
)
