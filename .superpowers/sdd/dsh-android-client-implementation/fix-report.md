## Status
DONE

## Fixes Applied
- Fix 1: Retrofit base URL — created `DynamicBaseUrlInterceptor` that rewrites request URLs at request time using the server address stored in `DshPreferences`. The interceptor is injected via Hilt and added to OkHttpClient before the AuthInterceptor. The Retrofit instance keeps `http://localhost/` as a placeholder; the interceptor replaces scheme/host/port dynamically.
- Fix 2: Login token extraction — replaced hardcoded `"authenticated"` token with parsing of `response.redirect` URL using `android.net.Uri.parse(redirect).getQueryParameter("token")`. Returns an error if no token is received.
- Fix 3: HTTPS validation — added URL scheme and host validation in `ConnectionViewModel.connect()`. Blocks non-localhost HTTP connections with a warning message, requires `http://` or `https://` scheme, and shows an error for invalid addresses.
- Fix 4: Release minification — set `isMinifyEnabled = true` and `isShrinkResources = true` for the release build type. Added comprehensive ProGuard rules for Retrofit, Gson, OkHttp, Hilt/Dagger, and Coroutines.

## Files Modified
- `app/src/main/java/com/dsh/android/data/remote/DynamicBaseUrlInterceptor.kt` (NEW — dynamic URL rewrite interceptor)
- `app/src/main/java/com/dsh/android/di/NetworkModule.kt` (added `DynamicBaseUrlInterceptor` injection)
- `app/src/main/java/com/dsh/android/data/repository/DshRepositoryImpl.kt` (token extraction from redirect URL)
- `app/src/main/java/com/dsh/android/ui/connection/ConnectionViewModel.kt` (HTTPS validation)
- `app/build.gradle.kts` (enabled `isMinifyEnabled` and `isShrinkResources` for release)
- `app/proguard-rules.pro` (added ProGuard rules for Gson, Retrofit, OkHttp, Hilt, Coroutines)

## Verification
- Fix 1 (Retrofit base URL): VERIFIED — `DynamicBaseUrlInterceptor` reads server address from preferences and rewrites URL scheme/host/port. Interceptor order: dynamic base → auth → logging.
- Fix 2 (Login token extraction): VERIFIED — Token parsed from `response.redirect` via `Uri.parse().getQueryParameter("token")`. Fails with clear error when no token received.
- Fix 3 (HTTPS validation): VERIFIED — Validates URL scheme, blocks non-localhost HTTP with user-facing error message, allows localhost HTTP for development.
- Fix 4 (Release minification): VERIFIED — `isMinifyEnabled = true`, `isShrinkResources = true`, ProGuard rules cover Gson DTOs, Retrofit interfaces, OkHttp, Hilt, and Coroutines.

## Commits
- `a7078b9` — fix: address blocking issues from final code review

## Concerns (if any)
- HTTP logging interceptor still logs at `Level.BODY` unconditionally — credentials and request bodies are logged in release. This is a "Should Fix Before Release" item from the review, not a blocker, but worth addressing before production deployment.
- Plaintext password storage remains in DataStore — also a "Should Fix" item (encrypted storage required by spec).
- WebSocket reconnection with exponential backoff not implemented — deferred to v1.1 as noted in review.
