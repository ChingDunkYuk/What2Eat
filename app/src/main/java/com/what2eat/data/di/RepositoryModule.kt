package com.what2eat.data.di

import com.what2eat.data.repository.PersonProfileRepositoryImpl
import com.what2eat.domain.repository.PersonProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository 绑定模块：将接口绑定到实现。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPersonProfileRepository(
        impl: PersonProfileRepositoryImpl
    ): PersonProfileRepository
}
