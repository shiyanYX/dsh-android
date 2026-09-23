package com.dsh.android.ui.settings

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsh.android.data.local.DshPreferences
import com.dsh.android.domain.repository.DshRepository
import com.dsh.android.util.DshLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

    /**
     * Export ring buffer to public Downloads directory (accessible via file manager)
     */
    fun exportLogBuffer() {
        viewModelScope.launch {
            try {
                val internalFile = logger.exportRingBuffer()
                if (internalFile == null) {
                    _uiState.value = _uiState.value.copy(lastLogMessage = "导出失败: 无法生成日志文件")
                    return@launch
                }

                val savedPath = copyToDownloads(internalFile, "dsh_log_${System.currentTimeMillis()}.txt")
                if (savedPath != null) {
                    _uiState.value = _uiState.value.copy(
                        lastLogMessage = "✅ 日志已保存到: Downloads/dsh_logs/$savedPath"
                    )
                } else {
                    // Fallback: show internal path
                    _uiState.value = _uiState.value.copy(
                        lastLogMessage = "保存到 Downloads 失败，内部路径: ${internalFile.absolutePath}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(lastLogMessage = "导出失败: ${e.message}")
            }
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
                    val savedPath = copyToDownloads(file, file.name)
                    _uiState.value = _uiState.value.copy(
                        lastLogMessage = "✅ 停止记录 (${sizeKB}KB) → Downloads/dsh_logs/$savedPath"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(lastLogMessage = "停止记录失败: ${e.message}")
            }
        }
    }

    /**
     * Copy file to public Downloads/dsh_logs/ directory using MediaStore (API 29+)
     * or direct file copy (older APIs).
     */
    private suspend fun copyToDownloads(sourceFile: File, fileName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Use MediaStore for Android 10+
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download/dsh_logs")
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { output ->
                            sourceFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        fileName
                    } else null
                } else {
                    // Direct file copy for older APIs
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "dsh_logs")
                    dir.mkdirs()
                    val dest = File(dir, fileName)
                    sourceFile.copyTo(dest, overwrite = true)
                    fileName
                }
            } catch (e: Exception) {
                logger.e("SettingsVM", "Copy to Downloads failed", e)
                null
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
