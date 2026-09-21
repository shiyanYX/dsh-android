## Status
DONE

## What was implemented
- `app/src/main/java/com/dsh/android/ui/chat/ChatViewModel.kt` — ViewModel with WebSocket event observation, message sending, tool call confirmation
- `app/src/main/java/com/dsh/android/ui/chat/ChatScreen.kt` — Main chat screen composable with Scaffold, LazyColumn, and auto-scroll
- `app/src/main/java/com/dsh/android/ui/chat/components/MessageBubble.kt` — Message bubble with user/assistant styling and rounded corners
- `app/src/main/java/com/dsh/android/ui/chat/components/ToolCallCard.kt` — Tool call card with status display and approve/reject buttons
- `app/src/main/java/com/dsh/android/ui/chat/components/ChatInput.kt` — Input bar with text field and send button

## Verification
- File locations verified: YES
- Package declarations correct: YES
- ViewModel handles WebSocket events: YES
- UI components implemented: YES

## Commits
- `6e8895d` — `feat: add chat screen with message bubbles and tool call cards`

## Concerns (if any)
- None
