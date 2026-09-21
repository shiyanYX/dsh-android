package com.dsh.android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dsh_settings")

@Singleton
class DshPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val SERVER_ADDRESS = stringPreferencesKey("server_address")
        private val USERNAME = stringPreferencesKey("username")
        private val SESSION_TOKEN = stringPreferencesKey("session_token")
        private val REMEMBER_PASSWORD = booleanPreferencesKey("remember_password")
        private val PASSWORD = stringPreferencesKey("password")
    }

    val serverAddress: Flow<String?> = context.dataStore.data.map { it[SERVER_ADDRESS] }
    val username: Flow<String?> = context.dataStore.data.map { it[USERNAME] }
    val sessionToken: Flow<String?> = context.dataStore.data.map { it[SESSION_TOKEN] }
    val rememberPassword: Flow<Boolean> = context.dataStore.data.map { it[REMEMBER_PASSWORD] ?: false }
    val password: Flow<String?> = context.dataStore.data.map { it[PASSWORD] }

    suspend fun saveServerAddress(address: String) {
        context.dataStore.edit { it[SERVER_ADDRESS] = address }
    }

    suspend fun saveCredentials(username: String, password: String, remember: Boolean) {
        context.dataStore.edit {
            it[USERNAME] = username
            it[REMEMBER_PASSWORD] = remember
            if (remember) {
                it[PASSWORD] = password
            } else {
                it.remove(PASSWORD)
            }
        }
    }

    suspend fun saveSessionToken(token: String) {
        context.dataStore.edit { it[SESSION_TOKEN] = token }
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.remove(SESSION_TOKEN) }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
