package com.dsh.android.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dsh.android.domain.model.AgentStatus
import com.dsh.android.ui.chat.components.ChatInput
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("会话") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // Model selector button
                    Row(
                        modifier = Modifier
                            .clickable { viewModel.toggleModelSelector() }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.selectedModel ?: "模型",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "选择模型",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Agent status indicator
                    if (uiState.agentStatus == AgentStatus.RUNNING) {
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
                onSend = viewModel::sendMessage
            )
        }
    ) { padding ->
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

            // Messages
            items(uiState.messages) { message ->
                MessageBubble(message = message)
            }
        }
    }
}
