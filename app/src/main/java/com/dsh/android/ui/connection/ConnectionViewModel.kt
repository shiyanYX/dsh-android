package com.dsh.android.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsh.android.domain.repository.DshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionUiState(
    val serverAddress: String = "",
    val protocol: String = "HTTPS",
    val port: String = "16666",
    val path: String = "",
    val username: String = "",
    val password: String = "",
    val rememberPassword: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isConnected: Boolean = false
)

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val repository: DshRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    fun onServerAddressChange(address: String) {
        _uiState.value = _uiState.value.copy(serverAddress = address)
    }

    fun onProtocolChange(protocol: String) {
        _uiState.value = _uiState.value.copy(protocol = protocol)
    }

    fun onPortChange(port: String) {
        // Only allow digits
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

        // Build server address from components
        val host = state.serverAddress.trim().removePrefix("http://").removePrefix("https://")
            .trimEnd('/')
        if (host.isBlank()) {
            _uiState.value = state.copy(error = "请填写服务器地址")
            return
        }

        val scheme = state.protocol.lowercase()
        val port = state.port.ifBlank { if (scheme == "https") "443" else "80" }
        val basePath = state.path.trim().trimEnd('/')

        val serverAddress = buildString {
            append("$scheme://$host:$port")
            if (basePath.isNotBlank()) {
                append("/$basePath")
            }
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            val result = repository.login(serverAddress, state.username, state.password)
            result.fold(
                onSuccess = {
                    _uiState.value = state.copy(isLoading = false, isConnected = true)
                },
                onFailure = { e ->
                    _uiState.value = state.copy(isLoading = false, error = e.message)
                }
            )
        }
    }
}
