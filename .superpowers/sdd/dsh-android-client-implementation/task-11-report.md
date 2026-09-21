## Status
DONE

## What was implemented
- `app/src/main/java/com/dsh/android/ui/connection/ConnectionViewModel.kt` — ViewModel with UI state management, form field handlers, and login logic via DshRepository
- `app/src/main/java/com/dsh/android/ui/connection/ConnectionScreen.kt` — Compose screen with server address, username, password fields, remember password checkbox, connect button with loading state, and error display

## Verification
- File locations verified: YES
- Package declarations correct: YES (`com.dsh.android.ui.connection`)
- ViewModel handles UI state: YES (MutableStateFlow with ConnectionUiState data class)
- Screen has all UI elements: YES (Scaffold, TopAppBar, 3 OutlinedTextFields, Checkbox, Button with loading indicator, error text)

## Commits
- `ba6f5d2` — `feat: add connection screen with login form`

## Concerns (if any)
- None. Files match the brief specification exactly.
