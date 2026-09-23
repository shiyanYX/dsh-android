package com.dsh.android.ui.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
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
    val lastLogMessage: String? = null
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

    fun exportLogBuffer() {
        viewModelScope.launch {
            try {
                val file = logger.exportRingBuffer()
                if (file != null) {
                    _uiState.value = _uiState.value.copy(
                        lastLogMessage = "已导出: ${file.absolutePath}"
                    )
                    // Try to share the file
                    shareLogFile(file)
                } else {
                    _uiState.value = _uiState.value.copy(
                        lastLogMessage = "导出失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    lastLogMessage = "导出失败: ${e.message}"
                )
            }
        }
    }

    fun startFileLogging() {
        try {
            val file = logger.startFileLogging()
            if (file != null) {
                _uiState.value = _uiState.value.copy(
                    lastLogMessage = "日志记录已开始: ${file.name}"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    lastLogMessage = "开始记录失败"
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                lastLogMessage = "开始记录失败: ${e.message}"
            )
        }
    }

    fun stopFileLogging() {
        try {
            val file = logger.stopFileLogging()
            if (file != null) {
                _uiState.value = _uiState.value.copy(
                    lastLogMessage = "日志记录已停止: ${file.name} (${file.length() / 1024}KB)"
                )
                shareLogFile(file)
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                lastLogMessage = "停止记录失败: ${e.message}"
            )
        }
    }

    private fun shareLogFile(file: java.io.File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "分享日志文件"))
        } catch (e: Exception) {
            logger.w("SettingsVM", "Share failed: ${e.message}")
            Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.value = _uiState.value.copy(isConnected = false)
        }
    }
}
