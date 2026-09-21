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
