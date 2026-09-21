## Status
DONE

## What was implemented
- `app/src/main/java/com/dsh/android/domain/model/Session.kt` — Session data class (id, title, timestamps, model)
- `app/src/main/java/com/dsh/android/domain/model/Message.kt` — Message data class + MessageRole enum (USER, ASSISTANT, SYSTEM)
- `app/src/main/java/com/dsh/android/domain/model/ToolCall.kt` — ToolCall data class + ToolCallStatus enum (PENDING, APPROVED, REJECTED, COMPLETED, ERROR)
- `app/src/main/java/com/dsh/android/domain/model/DshModel.kt` — DshModel data class (id, name, isAvailable)
- `app/src/main/java/com/dsh/android/domain/model/WebSocketEvent.kt` — WebSocketEvent sealed class + AgentStatus enum (IDLE, RUNNING, WAITING_CONFIRMATION)

## Verification
- File locations verified: YES
- Package declarations correct: YES

## Commits
- Hash: `ff41b3e`
- Message: `feat: add domain models for Session, Message, ToolCall, Model`

## Concerns (if any)
- None. All 5 files created with exact content from the brief.
