# Task 1: Project Scaffolding

## Objective
Create the Android project structure with all necessary configuration files and dependencies.

## Files to Create
- `build.gradle.kts` (project-level)
- `settings.gradle.kts`
- `gradle.properties`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/dsh/android/DshApplication.kt`

## Requirements

### 1. Project-level build.gradle.kts
```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
}
```

### 2. settings.gradle.kts
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "dsh-android"
include(":app")
```

### 3. gradle.properties
```properties
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

### 4. app/build.gradle.kts
- Android application plugin
- Kotlin Android plugin
- Hilt plugin
- Compose BOM 2024.02.00
- Material 3
- Navigation Compose
- OkHttp + Retrofit
- Hilt + Hilt Navigation Compose
- DataStore Preferences
- Kotlin Coroutines
- Min SDK 26, Target SDK 34
- Java 17 compatibility

### 5. AndroidManifest.xml
- INTERNET permission
- ACCESS_NETWORK_STATE permission
- Application class reference
- MainActivity with LAUNCHER intent filter

### 6. DshApplication.kt
- @HiltAndroidApp annotation
- Extends Application class

## Verification
Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

## Commit
```bash
git add -A
git commit -m "feat: scaffold Android project with Compose, Hilt, OkHttp"
```
