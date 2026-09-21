package com.dsh.android.data.remote.model

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("redirect") val redirect: String? = null,
    @SerializedName("error") val error: String? = null
)
