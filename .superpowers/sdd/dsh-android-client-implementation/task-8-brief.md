# Task 8: Hilt Dependency Injection

## Objective
Create Hilt modules for dependency injection setup.

## Files to Create
- `app/src/main/java/com/dsh/android/di/NetworkModule.kt`
- `app/src/main/java/com/dsh/android/di/AppModule.kt`

## Requirements

### 1. NetworkModule.kt
```kotlin
package com.dsh.android.di

import com.dsh.android.data.local.DshPreferences
import com.dsh.android.data.remote.AuthInterceptor
import com.dsh.android.data.remote.DshApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://localhost/") // Base URL will be overridden per request
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideDshApi(retrofit: Retrofit): DshApi {
        return retrofit.create(DshApi::class.java)
    }
}
```

### 2. AppModule.kt
```kotlin
package com.dsh.android.di

import com.dsh.android.data.repository.DshRepositoryImpl
import com.dsh.android.domain.repository.DshRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindDshRepository(impl: DshRepositoryImpl): DshRepository
}
```

## Dependencies
- Uses `DshApi` from Task 4
- Uses `AuthInterceptor` from Task 4
- Uses `DshPreferences` from Task 5
- Uses `DshRepositoryImpl` from Task 6
- Uses `DshRepository` interface from Task 6
- Uses Hilt annotations

## Verification
- Verify both files have correct package declarations
- Verify NetworkModule provides OkHttpClient, Retrofit, and DshApi
- Verify AppModule binds DshRepository interface to DshRepositoryImpl

## Commit
```bash
git add app/src/main/java/com/dsh/android/di/
git commit -m "feat: add Hilt dependency injection modules"
```
