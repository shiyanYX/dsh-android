package com.dsh.android.ui.chat

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dsh.android.domain.model.AgentStatus
import com.dsh.android.domain.model.MessageRole
import com.dsh.android.ui.chat.components.ChatInput
import com.dsh.android.ui.chat.components.MessageActionsPopup
import com.dsh.android.ui.chat.components.MessageBubble
import com.dsh.android.ui.chat.components.ToolCallCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(sessionId) {
        viewModel.setSession(sessionId)
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Model selector dropdown
    DropdownMenu(
        expanded = uiState.showModelSelector,
        onDismissRequest = { viewModel.toggleModelSelector() }
    ) {
        uiState.availableModels.forEach { model ->
            DropdownMenuItem(
                text = { Text(model.name) },
                onClick = { viewModel.selectModel(model.id) },
                trailingIcon = {
                    if (model.id == uiState.selectedModel) {
                        Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    }

    // Message actions popup
    if (uiState.showMessageActions && uiState.selectedMessageId != null) {
        val selectedMessage = uiState.messages.find { it.id == uiState.selectedMessageId }
        if (selectedMessage != null) {
            MessageActionsPopup(
                isUserMessage = selectedMessage.role == MessageRole.USER,
                onCopy = {
                    val content = viewModel.copyMessageContent(uiState.selectedMessageId!!)
                    if (content != null) {
                        clipboardManager.setText(AnnotatedString(content))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    }
                },
                onRegenerate = {
                    viewModel.regenerateMessage(uiState.selectedMessageId!!)
                },
                onDelete = {
                    viewModel.deleteMessage(uiState.selectedMessageId!!)
                },
                onDismiss = { viewModel.hideMessageActions() }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("会话", style = MaterialTheme.typography.titleMedium)
                        if (uiState.tokenStats.turns > 0) {
                            Text(
                                text = "轮次: ${uiState.tokenStats.turns} | 步骤: ${uiState.tokenStats.steps} | 首Token: ${uiState.tokenStats.ttft}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // Agent status indicator
                    if (uiState.agentStatus == AgentStatus.RUNNING) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 8.dp)
                        )
                    }
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 8.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            ChatInput(
                value = uiState.inputText,
                onValueChange = viewModel::onInputChange,
                onSend = viewModel::sendMessage,
                selectedModel = uiState.selectedModel,
                availableModels = uiState.availableModels.map { it.id to it.name },
                onModelClick = { viewModel.toggleModelSelector() },
                onPaste = { viewModel.onInputChange(it) },
                onStopAgent = { viewModel.stopAgent() },
                isAgentRunning = uiState.agentStatus == AgentStatus.RUNNING
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("加载历史消息中...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "⚠️",
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { viewModel.setSession(sessionId) }) {
                            Text("重试")
                        }
                    }
                }
            }
            uiState.messages.isEmpty() && uiState.toolCalls.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "💬",
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "暂无消息",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "发送消息开始对话",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // Tool calls
                    items(uiState.toolCalls) { toolCall ->
                        ToolCallCard(
                            toolCall = toolCall,
                            onApprove = { viewModel.confirmToolCall(toolCall.id, true) },
                            onReject = { viewModel.confirmToolCall(toolCall.id, false) }
                        )
                    }

                    // Messages with long-press support
                    items(uiState.messages) { message ->
                        MessageBubble(
                            message = message,
                            onLongClick = { viewModel.showMessageActions(message.id) }
                        )
                    }
                }
            }
        }
    }
}
