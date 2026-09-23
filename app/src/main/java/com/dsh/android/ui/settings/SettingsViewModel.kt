package com.dsh.android.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsh.android.data.local.DshPreferences
import com.dsh.android.domain.repository.DshRepository
import com.dsh.android.util.DshLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val fontSize: FontSize = FontSize.MEDIUM,
    val logCount: Int = 0,
    val isFileLogging: Boolean = false,
    val currentLogFile: String? = null,
    val lastLogMessage: String? = null,
    val pendingLogContent: String? = null // log content ready to copy
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: DshRepository,
    private val preferences: DshPreferences,
    private val logger: DshLogger,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        observeLogger()
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
                fontSize = FontSize.entries.getOrElse(fontSizeOrdinal) { FontSize.MEDIUM },
                logCount = logger.logCount.value,
                isFileLogging = logger.isFileLogging.value
            )
        }
    }

    private fun observeLogger() {
        viewModelScope.launch {
            logger.logCount.collect { count ->
                _uiState.value = _uiState.value.copy(logCount = count)
            }
        }
        viewModelScope.launch {
            logger.isFileLogging.collect { isLogging ->
                _uiState.value = _uiState.value.copy(
                    isFileLogging = isLogging,
                    currentLogFile = logger.getLogFile()?.absolutePath
                )
            }
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

    /**
     * Export ring buffer - copies log content to clipboard via UI state
     */
    fun exportLogBuffer() {
        try {
            val content = logger.getRecentLogsFormatted()
            if (content.isNotBlank()) {
                _uiState.value = _uiState.value.copy(
                    lastLogMessage = "✅ 日志已准备（${content.length} 字符）",
                    pendingLogContent = content
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    lastLogMessage = "❌ 日志为空"
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                lastLogMessage = "❌ 导出失败: ${e.message}"
            )
        }
    }

    fun startFileLogging() {
        try {
            val file = logger.startFileLogging()
            if (file != null) {
                _uiState.value = _uiState.value.copy(
                    lastLogMessage = "🔴 正在记录: ${file.name}"
                )
            } else {
                _uiState.value = _uiState.value.copy(lastLogMessage = "开始记录失败")
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(lastLogMessage = "开始记录失败: ${e.message}")
        }
    }

    fun stopFileLogging() {
        viewModelScope.launch {
            try {
                val file = logger.stopFileLogging()
                if (file != null) {
                    val sizeKB = file.length() / 1024
                    _uiState.value = _uiState.value.copy(
                        lastLogMessage = "✅ 停止记录 (${sizeKB}KB): ${file.absolutePath}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(lastLogMessage = "停止记录失败: ${e.message}")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.value = _uiState.value.copy(isConnected = false)
        }
    }
}
