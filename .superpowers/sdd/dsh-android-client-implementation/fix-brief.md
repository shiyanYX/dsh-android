# Fix: Blocking Issues from Final Code Review

## Issues to Fix

### 1. Fix Retrofit Base URL Configuration
- Problem: Retrofit uses fixed `http://localhost/` base URL
- Solution: Use OkHttp interceptor to rewrite URLs dynamically based on server address from preferences

### 2. Fix Login Token Extraction
- Problem: Login token hardcoded as `"authenticated"` instead of parsed from redirect URL
- Solution: Parse token from `response.redirect` URL (format: `/?token=xxx`)

### 3. Add HTTPS Validation
- Problem: No HTTPS enforcement for server addresses
- Solution: Validate URL scheme and warn/block HTTP connections for non-localhost

### 4. Enable Release Build Minification
- Problem: `isMinifyEnabled = false` for release builds
- Solution: Enable R8 minification and add ProGuard rules

## Files to Modify
- `app/src/main/java/com/dsh/android/di/NetworkModule.kt`
- `app/src/main/java/com/dsh/android/data/repository/DshRepositoryImpl.kt`
- `app/src/main/java/com/dsh/android/ui/connection/ConnectionViewModel.kt`
- `app/build.gradle.kts`
- `app/proguard-rules.pro`

## Verification
- Verify all fixes are applied correctly
- Verify no new issues introduced

## Commit
```bash
git add -A
git commit -m "fix: address blocking issues from final code review

- Fix Retrofit base URL with dynamic URL interceptor
- Parse login token from redirect URL
- Add HTTPS validation for server addresses
- Enable release build minification with ProGuard rules"
```
