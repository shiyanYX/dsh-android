# Task 2: Domain Models

## Objective
Create domain model data classes used across all layers of the application.

## Files to Create
- `app/src/main/java/com/dsh/android/domain/model/Session.kt`
- `app/src/main/java/com/dsh/android/domain/model/Message.kt`
- `app/src/main/java/com/dsh/android/domain/model/ToolCall.kt`
- `app/src/main/java/com/dsh/android/domain/model/DshModel.kt`
- `app/src/main/java/com/dsh/android/domain/model/WebSocketEvent.kt`

## Requirements

### 1. Session.kt
```kotlin
package com.dsh.android.domain.model

data class Session(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val model: String? = null
)
```

### 2. Message.kt
```kotlin
package com.dsh.android.domain.model

data class Message(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val toolCalls: List<ToolCall> = emptyList()
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}
```

### 3. ToolCall.kt
```kotlin
package com.dsh.android.domain.model

data class ToolCall(
    val id: String,
    val toolName: String,
    val args: Map<String, Any>,
    val status: ToolCallStatus = ToolCallStatus.PENDING
)

enum class ToolCallStatus {
    PENDING,
    APPROVED,
    REJECTED,
    COMPLETED,
    ERROR
}
```

### 4. DshModel.kt
```kotlin
package com.dsh.android.domain.model

data class DshModel(
    val id: String,
    val name: String,
    val isAvailable: Boolean = true
)
```

### 5. WebSocketEvent.kt
```kotlin
package com.dsh.android.domain.model

sealed class WebSocketEvent {
    data class Connected(val sessionId: String) : WebSocketEvent()
    data class Disconnected(val reason: String? = null) : WebSocketEvent()
    data class MessageReceived(val message: Message) : WebSocketEvent()
    data class ToolCallReceived(val toolCall: ToolCall) : WebSocketEvent()
    data class AgentStatusChanged(val status: AgentStatus) : WebSocketEvent()
    data class Error(val error: Throwable) : WebSocketEvent()
}

enum class AgentStatus {
    IDLE,
    RUNNING,
    WAITING_CONFIRMATION
}
```

## Verification
- Verify all files compile correctly (if build environment available)
- Verify all data classes have correct package declarations

## Commit
```bash
git add app/src/main/java/com/dsh/android/domain/
git commit -m "feat: add domain models for Session, Message, ToolCall, Model"
```
