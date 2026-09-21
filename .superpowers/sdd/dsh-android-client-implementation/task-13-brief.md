# Task 13: Chat Screen

## Objective
Create chat screen with message list, input, and tool call cards.

## Files to Create
- `app/src/main/java/com/dsh/android/ui/chat/ChatViewModel.kt`
- `app/src/main/java/com/dsh/android/ui/chat/ChatScreen.kt`
- `app/src/main/java/com/dsh/android/ui/chat/components/MessageBubble.kt`
- `app/src/main/java/com/dsh/android/ui/chat/components/ToolCallCard.kt`
- `app/src/main/java/com/dsh/android/ui/chat/components/ChatInput.kt`

## Requirements

### 1. ChatViewModel.kt
```kotlin
package com.dsh.android.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsh.android.domain.model.*
import com.dsh.android.domain.repository.DshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val agentStatus: AgentStatus = AgentStatus.IDLE,
    val isLoading: Boolean = false,
    val error: String? = null,
    val inputText: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: DshRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentSessionId: String? = null

    init {
        observeWebSocketEvents()
    }

    fun setSession(sessionId: String) {
        currentSessionId = sessionId
        repository.connectWebSocket(sessionId)
    }

    private fun observeWebSocketEvents() {
        viewModelScope.launch {
            repository.webSocketEvents.collect { event ->
                when (event) {
                    is WebSocketEvent.Connected -> {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                    is WebSocketEvent.Disconnected -> {
                        _uiState.value = _uiState.value.copy(error = "连接断开")
                    }
                    is WebSocketEvent.MessageReceived -> {
                        val messages = _uiState.value.messages + event.message
                        _uiState.value = _uiState.value.copy(messages = messages)
                    }
                    is WebSocketEvent.ToolCallReceived -> {
                        val toolCalls = _uiState.value.toolCalls + event.toolCall
                        _uiState.value = _uiState.value.copy(
                            toolCalls = toolCalls,
                            agentStatus = AgentStatus.WAITING_CONFIRMATION
                        )
                    }
                    is WebSocketEvent.AgentStatusChanged -> {
                        _uiState.value = _uiState.value.copy(agentStatus = event.status)
                    }
                    is WebSocketEvent.Error -> {
                        _uiState.value = _uiState.value.copy(error = event.error.message)
                    }
                }
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || currentSessionId == null) return

        val userMessage = Message(
            id = System.currentTimeMillis().toString(),
            sessionId = currentSessionId!!,
            role = MessageRole.USER,
            content = text,
            timestamp = System.currentTimeMillis()
        )

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            inputText = "",
            agentStatus = AgentStatus.RUNNING
        )

        viewModelScope.launch {
            repository.sendMessage(currentSessionId!!, text)
        }
    }

    fun confirmToolCall(callId: String, approved: Boolean) {
        val toolCalls = _uiState.value.toolCalls.map { tc ->
            if (tc.id == callId) {
                tc.copy(status = if (approved) ToolCallStatus.APPROVED else ToolCallStatus.REJECTED)
            } else {
                tc
            }
        }
        _uiState.value = _uiState.value.copy(
            toolCalls = toolCalls,
            agentStatus = AgentStatus.RUNNING
        )

        viewModelScope.launch {
            repository.confirmToolCall(currentSessionId!!, callId, approved)
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.disconnectWebSocket()
    }
}
```

### 2. MessageBubble.kt
```kotlin
package com.dsh.android.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dsh.android.domain.model.Message
import com.dsh.android.domain.model.MessageRole

@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
```

### 3. ToolCallCard.kt
```kotlin
package com.dsh.android.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dsh.android.domain.model.ToolCall
import com.dsh.android.domain.model.ToolCallStatus

@Composable
fun ToolCallCard(
    toolCall: ToolCall,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🔧 ${toolCall.toolName}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = when (toolCall.status) {
                        ToolCallStatus.PENDING -> "待确认"
                        ToolCallStatus.APPROVED -> "已允许"
                        ToolCallStatus.REJECTED -> "已拒绝"
                        ToolCallStatus.COMPLETED -> "已完成"
                        ToolCallStatus.ERROR -> "错误"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Show tool arguments
            Text(
                text = toolCall.args.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (toolCall.status == ToolCallStatus.PENDING) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("拒绝")
                    }
                    Button(onClick = onApprove) {
                        Text("允许")
                    }
                }
            }
        }
    }
}
```

### 4. ChatInput.kt
```kotlin
package com.dsh.android.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息...") },
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank()
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "发送",
                    tint = if (value.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
```

### 5. ChatScreen.kt
```kotlin
package com.dsh.android.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
```

## Dependencies
- Uses `DshRepository` from Task 6
- Uses domain models from Task 2: `Message`, `ToolCall`, `WebSocketEvent`, `AgentStatus`, `MessageRole`, `ToolCallStatus`
- Uses Compose Material 3
- Uses Hilt Navigation Compose

## Verification
- Verify all files have correct package declarations
- Verify ViewModel handles WebSocket events, messages, and tool calls
- Verify all UI components are correctly implemented

## Commit
```bash
git add app/src/main/java/com/dsh/android/ui/chat/
git commit -m "feat: add chat screen with message bubbles and tool call cards"
```
