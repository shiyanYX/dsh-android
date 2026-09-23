package com.dsh.android.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // SAF file picker for log export
    val logExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val content = uiState.pendingLogContent
            if (content != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(content.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "✅ 日志已保存", Toast.LENGTH_SHORT).show()
                    viewModel.clearPendingLogContent()
                } catch (e: Exception) {
                    Toast.makeText(context, "❌ 保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(uiState.isConnected) {
        if (!uiState.isConnected) {
            onLogout()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Server Settings
            Text(
                text = "服务器设置",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("地址: ${uiState.serverAddress}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("用户名: ${uiState.username}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "状态: ${if (uiState.isConnected) "已连接" else "未连接"}",
                        color = if (uiState.isConnected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Appearance Settings
            Text(
                text = "外观设置",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Theme Mode
                    Text("主题模式", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = uiState.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.themeMode == mode,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (mode) {
                                    ThemeMode.LIGHT -> "浅色模式"
                                    ThemeMode.DARK -> "深色模式"
                                    ThemeMode.SYSTEM -> "跟随系统"
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Font Size
                    Text("字体大小", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    FontSize.entries.forEach { size ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = uiState.fontSize == size,
                                    onClick = { viewModel.setFontSize(size) },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.fontSize == size,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = size.label)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Logging Settings
            Text(
                text = "日志",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Ring buffer info
                    Text("环形缓冲: 最近 ${uiState.logCount} 条日志")
                    Spacer(modifier = Modifier.height(8.dp))

                    // Export ring buffer button
                    OutlinedButton(
                        onClick = { viewModel.exportLogBuffer() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📋 准备日志")
                    }

                    if (uiState.pendingLogContent != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val logSize = uiState.pendingLogContent!!.length
                        val logLines = uiState.pendingLogContent!!.lines().size
                        Text(
                            text = "已准备: $logLines 行, ${logSize} 字符",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // SAF: let user choose save location
                        Button(
                            onClick = {
                                logExportLauncher.launch("dsh_log_${System.currentTimeMillis()}.txt")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("💾 选择位置保存日志文件")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // File logging toggle
                    Text(
                        text = if (uiState.isFileLogging) "正在记录日志到文件..." else "持续日志记录",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.isFileLogging) {
                        Text(
                            text = "文件: ${uiState.currentLogFile ?: "未知"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.stopFileLogging() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("停止记录")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startFileLogging() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("开始记录日志到文件")
                        }
                    }

                    if (uiState.lastLogMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.lastLogMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.lastLogMessage!!.contains("失败") || uiState.lastLogMessage!!.contains("❌")) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About
            Text(
                text = "关于",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("版本: 1.0.0")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("DSH Android Remote Client")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Logout button
            Button(
                onClick = viewModel::logout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.Default.Logout,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("退出登录")
            }
        }
    }
}
