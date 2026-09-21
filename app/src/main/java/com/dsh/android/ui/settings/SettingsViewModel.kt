package com.dsh.android.ui.settings

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

data class SettingsUiState(
    val serverAddress: String = "",
    val username: String = "",
    val isConnected: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: DshRepository,
    private val preferences: DshPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val serverAddress = preferences.serverAddress.first() ?: ""
            val username = preferences.username.first() ?: ""
            val isConnected = repository.isConnected.first()
            _uiState.value = SettingsUiState(
                serverAddress = serverAddress,
                username = username,
                isConnected = isConnected
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.value = _uiState.value.copy(isConnected = false)
        }
    }
}
