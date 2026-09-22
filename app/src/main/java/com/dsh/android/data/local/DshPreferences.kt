package com.dsh.android.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.dsh.android.domain.model.Favorite
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
    private val gson = Gson()

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

    // Favorites
    val favorites: Flow<List<Favorite>> = flow {
        val json = prefs.getString("favorites", "[]") ?: "[]"
        val type = object : TypeToken<List<Favorite>>() {}.type
        emit(gson.fromJson(json, type))
    }

    // Theme & Font
    suspend fun getThemeMode(): Int = prefs.getInt("theme_mode", 2) // default SYSTEM
    suspend fun saveThemeMode(mode: Int) { prefs.edit().putInt("theme_mode", mode).apply() }
    suspend fun getFontSize(): Int = prefs.getInt("font_size", 1) // default MEDIUM
    suspend fun saveFontSize(size: Int) { prefs.edit().putInt("font_size", size).apply() }

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

    // Favorites operations
    suspend fun addFavorite(favorite: Favorite) {
        val current = favoritesList()
        if (current.none { it.sessionId == favorite.sessionId && it.serverAddress == favorite.serverAddress }) {
            val updated = current + favorite
            saveFavorites(updated)
        }
    }

    suspend fun removeFavorite(sessionId: String, serverAddress: String) {
        val current = favoritesList()
        val updated = current.filter {
            !(it.sessionId == sessionId && it.serverAddress == serverAddress)
        }
        saveFavorites(updated)
    }

    suspend fun isFavorite(sessionId: String, serverAddress: String): Boolean {
        return favoritesList().any {
            it.sessionId == sessionId && it.serverAddress == serverAddress
        }
    }

    private fun favoritesList(): List<Favorite> {
        val json = prefs.getString("favorites", "[]") ?: "[]"
        val type = object : TypeToken<List<Favorite>>() {}.type
        return gson.fromJson(json, type)
    }

    private fun saveFavorites(favorites: List<Favorite>) {
        val json = gson.toJson(favorites)
        prefs.edit().putString("favorites", json).apply()
    }
}
