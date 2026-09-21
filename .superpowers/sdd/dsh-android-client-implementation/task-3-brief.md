# Task 3: Network Layer - API Models

## Objective
Create API DTO (Data Transfer Object) models for network serialization.

## Files to Create
- `app/src/main/java/com/dsh/android/data/remote/model/LoginRequest.kt`
- `app/src/main/java/com/dsh/android/data/remote/model/LoginResponse.kt`
- `app/src/main/java/com/dsh/android/data/remote/model/SessionDto.kt`
- `app/src/main/java/com/dsh/android/data/remote/model/ModelDto.kt`
- `app/src/main/java/com/dsh/android/data/remote/model/CreateSessionRequest.kt`

## Requirements

### 1. LoginRequest.kt
```kotlin
package com.dsh.android.data.remote.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)
```

### 2. LoginResponse.kt
```kotlin
package com.dsh.android.data.remote.model

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("redirect") val redirect: String? = null,
    @SerializedName("error") val error: String? = null
)
```

### 3. SessionDto.kt
```kotlin
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
```

### 4. ModelDto.kt
```kotlin
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
```

### 5. CreateSessionRequest.kt
```kotlin
package com.dsh.android.data.remote.model

import com.google.gson.annotations.SerializedName

data class CreateSessionRequest(
    @SerializedName("title") val title: String = "New Session"
)
```

## Dependencies
- Uses domain models from Task 2: `Session`, `DshModel`
- Uses Gson annotations for JSON serialization

## Verification
- Verify all files have correct package declarations
- Verify all DTO classes have `toDomain()` conversion methods where needed

## Commit
```bash
git add app/src/main/java/com/dsh/android/data/remote/model/
git commit -m "feat: add API DTO models with Gson serialization"
```
