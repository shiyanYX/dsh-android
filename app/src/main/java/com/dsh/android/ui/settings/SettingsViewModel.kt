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

enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class FontSize(val label: String) { SMALL("小"), MEDIUM("中"), LARGE("大") }

data class SettingsUiState(
    val serverAddress: String = "",
    val username: String = "",
    val isConnected: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontSize: FontSize = FontSize.MEDIUM
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
            val themeModeOrdinal = preferences.getThemeMode()
            val fontSizeOrdinal = preferences.getFontSize()
            _uiState.value = SettingsUiState(
                serverAddress = serverAddress,
                username = username,
                isConnected = isConnected,
                themeMode = ThemeMode.entries.getOrElse(themeModeOrdinal) { ThemeMode.SYSTEM },
                fontSize = FontSize.entries.getOrElse(fontSizeOrdinal) { FontSize.MEDIUM }
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferences.saveThemeMode(mode.ordinal)
            _uiState.value = _uiState.value.copy(themeMode = mode)
        }
    }

    fun setFontSize(size: FontSize) {
        viewModelScope.launch {
            preferences.saveFontSize(size.ordinal)
            _uiState.value = _uiState.value.copy(fontSize = size)
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.value = _uiState.value.copy(isConnected = false)
        }
    }
}
