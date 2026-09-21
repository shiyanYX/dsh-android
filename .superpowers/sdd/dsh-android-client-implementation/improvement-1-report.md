## Status
DONE

## What was implemented
- Added BuildConfig.IS_DEBUG field in app/build.gradle.kts defaultConfig block
- Enabled buildConfig feature in buildFeatures section (required for AGP 8.0+)
- Imported BuildConfig in NetworkModule.kt
- Modified provideOkHttpClient to conditionally add HttpLoggingInterceptor only when BuildConfig.IS_DEBUG is true
- Preserved existing dynamicBaseUrlInterceptor and authInterceptor functionality

## Verification
- BuildConfig field added: YES
- Logging is debug-only: YES
- Release builds don't log request/response bodies: YES

## Commits
- Commit hash: f1931a6
- Commit message: fix: make HTTP logging debug-only

## Concerns (if any)
- None. Implementation follows the brief exactly and maintains existing functionality.