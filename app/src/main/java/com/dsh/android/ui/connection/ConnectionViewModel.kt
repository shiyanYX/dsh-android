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
        if (state.serverAddress.isBlank() || state.username.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "请填写所有字段")
            return
        }

        // Validate URL scheme: enforce HTTPS for non-localhost addresses
        val address = state.serverAddress.trim()
        val uri = try {
            android.net.Uri.parse(address)
        } catch (e: Exception) {
            _uiState.value = state.copy(error = "无效的服务器地址")
            return
        }

        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase() ?: ""

        if (scheme != "http" && scheme != "https") {
            _uiState.value = state.copy(error = "服务器地址必须以 http:// 或 https:// 开头")
            return
        }

        val isLocalhost = host == "localhost" || host == "127.0.0.1" || host == "::1"
        if (scheme == "http" && !isLocalhost) {
            _uiState.value = state.copy(
                error = "非本地服务器建议使用 HTTPS，HTTP 连接不安全"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.login(state.serverAddress, state.username, state.password)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isLoading = false, isConnected = true)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }
}
