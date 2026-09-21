# Improvement: HTTP Logging Level Control

## Objective
Make HTTP logging level configurable and disable body logging in release builds.

## Files to Modify
- `app/src/main/java/com/dsh/android/di/NetworkModule.kt`
- `app/build.gradle.kts` (add BuildConfig field)

## Requirements

### 1. Add BuildConfig field for debug mode
```kotlin
// app/build.gradle.kts
android {
    defaultConfig {
        buildConfigField("boolean", "IS_DEBUG", "${project.findProperty("isDebug") ?: "false"}")
    }
}
```

### 2. Update NetworkModule.kt
```kotlin
@Provides
@Singleton
fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
    val builder = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)

    if (BuildConfig.IS_DEBUG) {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        builder.addInterceptor(logging)
    }

    return builder.build()
}
```

## Verification
- Verify logging is only added in debug builds
- Verify release builds don't log request/response bodies

## Commit
```bash
git add app/src/main/java/com/dsh/android/di/NetworkModule.kt \
        app/build.gradle.kts
git commit -m "fix: make HTTP logging debug-only"
```
