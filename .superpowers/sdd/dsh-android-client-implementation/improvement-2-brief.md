# Improvement 2: Encrypted Storage for Credentials

## Objective
Replace plaintext DataStore storage with EncryptedSharedPreferences for sensitive data.

## Files to Modify
- `app/build.gradle.kts` (add security-crypto dependency)
- `app/src/main/java/com/dsh/android/data/local/DshPreferences.kt`

## Requirements

### 1. Add security-crypto dependency
```kotlin
// app/build.gradle.kts
dependencies {
    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
```

### 2. Update DshPreferences.kt
```kotlin
package com.dsh.android.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DshPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            "dsh_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val prefs by lazy {
        context.getSharedPreferences("dsh_settings", Context.MODE_PRIVATE)
    }

    // Server address (not sensitive - can stay in regular prefs)
    val serverAddress: Flow<String?> = flow { emit(prefs.getString("server_address", null)) }
    val username: Flow<String?> = flow { emit(prefs.getString("username", null)) }
    val rememberPassword: Flow<Boolean> = flow { emit(prefs.getBoolean("remember_password", false)) }

    // Session token and password (sensitive - use encrypted prefs)
    val sessionToken: Flow<String?> = flow { emit(encryptedPrefs.getString("session_token", null)) }
    val password: Flow<String?> = flow { emit(encryptedPrefs.getString("password", null)) }

    suspend fun saveServerAddress(address: String) {
        prefs.edit().putString("server_address", address).apply()
    }

    suspend fun saveCredentials(username: String, password: String, remember: Boolean) {
        prefs.edit().putString("username", username).apply()
        prefs.edit().putBoolean("remember_password", remember).apply()
        if (remember) {
            encryptedPrefs.edit().putString("password", password).apply()
        } else {
            encryptedPrefs.edit().remove("password").apply()
        }
    }

    suspend fun saveSessionToken(token: String) {
        encryptedPrefs.edit().putString("session_token", token).apply()
    }

    suspend fun clearSession() {
        encryptedPrefs.edit().remove("session_token").apply()
    }

    suspend fun clearAll() {
        prefs.edit().clear().apply()
        encryptedPrefs.edit().clear().apply()
    }
}
```

## Dependencies
- Uses `androidx.security:security-crypto:1.1.0-alpha06`

## Verification
- Verify encrypted prefs are used for session token and password
- Verify regular prefs are used for non-sensitive data

## Commit
```bash
git add app/build.gradle.kts \
        app/src/main/java/com/dsh/android/data/local/DshPreferences.kt
git commit -m "fix: use EncryptedSharedPreferences for sensitive data"
```
