package com.dsh.android.ui.connection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    onConnected: () -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected) {
            onConnected()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("服务器") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Server address
            OutlinedTextField(
                value = uiState.serverAddress,
                onValueChange = viewModel::onServerAddressChange,
                label = { Text("服务器地址") },
                placeholder = { Text("dsh.example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            // Protocol + Port row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Protocol dropdown
                var protocolExpanded by remember { mutableStateOf(false) }
                val protocols = listOf("HTTPS", "HTTP")
                val protocolIcons = listOf("🔒", "⚠️")

                ExposedDropdownMenuBox(
                    expanded = protocolExpanded,
                    onExpandedChange = { protocolExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = uiState.protocol,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("协议") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = protocolExpanded,
                        onDismissRequest = { protocolExpanded = false }
                    ) {
                        protocols.forEach { protocol ->
                            DropdownMenuItem(
                                text = { Text(protocol) },
                                onClick = {
                                    viewModel.onProtocolChange(protocol)
                                    protocolExpanded = false
                                }
                            )
                        }
                    }
                }

                // Port field
                OutlinedTextField(
                    value = uiState.port,
                    onValueChange = viewModel::onPortChange,
                    label = { Text("端口") },
                    placeholder = { Text("443") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            // Path (optional)
            OutlinedTextField(
                value = uiState.path,
                onValueChange = viewModel::onPathChange,
                label = { Text("路径(可选,无则留空)") },
                placeholder = { Text("") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Username
            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text("用户名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Password
            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("密码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            // Remember password
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = uiState.rememberPassword,
                    onCheckedChange = viewModel::onRememberPasswordChange
                )
                Text("记住密码")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Error message
            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Connect button
            Button(
                onClick = viewModel::connect,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("连接中...")
                } else {
                    Text("连接")
                }
            }

            // Quick connect info
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "支持的格式：\n• 域名:端口 (如 dsh.example.com:16666)\n• IP:端口 (如 192.168.1.100:3080)\n• 完整 URL (如 https://dsh.example.com:16666)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
