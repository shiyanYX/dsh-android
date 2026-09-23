package com.dsh.android.ui.connection

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsh.android.data.local.DshPreferences
import com.dsh.android.domain.repository.DshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionUiState(
    val serverAddress: String = "",
    val protocol: String = "HTTPS",
    val port: String = "16666",
    val path: String = "",
    val username: String = "",
    val password: String = "",
    val rememberPassword: Boolean = true,
    val isLoading: Boolean = false,
    val isAutoLogging: Boolean = false,
    val error: String? = null,
    val isConnected: Boolean = false
)

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val repository: DshRepository,
    private val preferences: DshPreferences,
    private val logger: com.dsh.android.util.DshLogger
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    init {
        // Try auto-login with saved credentials
        tryAutoLogin()
    }

    private fun tryAutoLogin() {
        viewModelScope.launch {
            val serverAddress = preferences.serverAddress.first()
            val username = preferences.username.first()
            val password = preferences.password.first()

            if (!serverAddress.isNullOrBlank() && !username.isNullOrBlank() && !password.isNullOrBlank()) {
                logger.i(TAG, "AUTO-LOGIN: found credentials for $username@$serverAddress")
                logger.d(TAG, "AUTO-LOGIN: server=$serverAddress user=$username pwd=${"*".repeat(password.length)}")
                _uiState.value = _uiState.value.copy(isAutoLogging = true)
                logger.i(TAG, "AUTO-LOGIN: calling repository.login()...")
                val result = repository.login(serverAddress, username, password)
                result.fold(
                    onSuccess = {
                        logger.i(TAG, "AUTO-LOGIN: SUCCESS")
                        _uiState.value = _uiState.value.copy(isAutoLogging = false, isConnected = true)
                    },
                    onFailure = { e ->
                        logger.e(TAG, "AUTO-LOGIN: FAILED - ${e.message}")
                        // Fill in saved data for manual retry
                        _uiState.value = _uiState.value.copy(
                            isAutoLogging = false,
                            serverAddress = serverAddress,
                            username = username,
                            password = password,
                            error = "自动登录失败，请手动连接"
                        )
                    }
                )
            } else {
                logger.i(TAG, "AUTO-LOGIN: no saved credentials (server=${serverAddress ?: "null"} user=${username ?: "null"} pwd=${if (password != null) "set" else "null"})")
                // No saved credentials, try to fill in what we have
                if (!serverAddress.isNullOrBlank()) {
                    parseServerAddress(serverAddress)
                }
                if (!username.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(username = username)
                }
            }
        }
    }

    private fun parseServerAddress(address: String) {
        val uri = try { android.net.Uri.parse(address) } catch (_: Exception) { return }
        val scheme = uri.scheme?.uppercase() ?: "HTTPS"
        val host = uri.host ?: ""
        val port = uri.port.toString().let { if (it == "-1") "16666" else it }
        val path = uri.path?.trimEnd('/')?.removePrefix("/") ?: ""
        _uiState.value = _uiState.value.copy(
            serverAddress = host,
            protocol = scheme,
            port = port,
            path = path
        )
    }

    fun onServerAddressChange(address: String) {
        _uiState.value = _uiState.value.copy(serverAddress = address)
    }

    fun onProtocolChange(protocol: String) {
        _uiState.value = _uiState.value.copy(protocol = protocol)
    }

    fun onPortChange(port: String) {
        val filtered = port.filter { it.isDigit() }
        _uiState.value = _uiState.value.copy(port = filtered)
    }

    fun onPathChange(path: String) {
        _uiState.value = _uiState.value.copy(path = path)
    }

    fun onUsernameChange(username: String) {
        _uiState.value = _uiState.value.copy(username = username)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun onRememberPasswordChange(remember: Boolean) {
        _uiState.value = _uiState.value.copy(rememberPassword = remember)
    }

    fun connect() {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "请填写用户名和密码")
            return
        }

        val host = state.serverAddress.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        if (host.isBlank()) {
            _uiState.value = state.copy(error = "请填写服务器地址")
            return
        }

        val scheme = state.protocol.lowercase()
        val port = state.port.ifBlank { if (scheme == "https") "443" else "80" }
        val basePath = state.path.trim().trimEnd('/')

        val serverAddress = buildString {
            append("$scheme://$host:$port")
            if (basePath.isNotBlank()) append("/$basePath")
        }

        logger.i(TAG, "MANUAL CONNECT: server=$serverAddress user=${state.username}")
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            logger.i(TAG, "MANUAL CONNECT: calling repository.login()...")
            val result = repository.login(serverAddress, state.username, state.password)
            result.fold(
                onSuccess = {
                    // Save credentials for auto-login
                    preferences.saveCredentials(state.username, state.password, state.rememberPassword)
                    _uiState.value = state.copy(isLoading = false, isConnected = true)
                },
                onFailure = { e ->
                    _uiState.value = state.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    companion object {
        private const val TAG = "ConnectionVM"
    }
}
