## Status
DONE

## What was implemented
- `app/src/main/java/com/dsh/android/di/NetworkModule.kt` — Hilt module providing OkHttpClient, Retrofit, and DshApi as singletons
- `app/src/main/java/com/dsh/android/di/AppModule.kt` — Hilt module binding DshRepository interface to DshRepositoryImpl

## Verification
- File locations verified: YES
- Package declarations correct: YES
- NetworkModule provides: OkHttpClient, Retrofit, DshApi
- AppModule binds: DshRepository to DshRepositoryImpl

## Commits
- `733e228` — `feat: add Hilt dependency injection modules`

## Concerns (if any)
- None. Both files match the brief exactly.
