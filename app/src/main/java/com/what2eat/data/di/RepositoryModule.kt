package com.what2eat.data.di

import com.what2eat.data.repository.AppUsageModeRepositoryImpl
import com.what2eat.data.repository.DecisionSessionRepositoryImpl
import com.what2eat.data.repository.FoodCategoryRepositoryImpl
import com.what2eat.data.repository.PersonCategoryPreferenceRepositoryImpl
import com.what2eat.data.repository.PersonProfileRepositoryImpl
import com.what2eat.domain.repository.AppUsageModeRepository
import com.what2eat.domain.repository.DecisionSessionRepository
import com.what2eat.domain.repository.FoodCategoryRepository
import com.what2eat.domain.repository.PersonCategoryPreferenceRepository
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

    @Binds
    @Singleton
    abstract fun bindFoodCategoryRepository(
        impl: FoodCategoryRepositoryImpl
    ): FoodCategoryRepository

    @Binds
    @Singleton
    abstract fun bindPersonCategoryPreferenceRepository(
        impl: PersonCategoryPreferenceRepositoryImpl
    ): PersonCategoryPreferenceRepository

    @Binds
    @Singleton
    abstract fun bindAppUsageModeRepository(
        impl: AppUsageModeRepositoryImpl
    ): AppUsageModeRepository

    @Binds
    @Singleton
    abstract fun bindDecisionSessionRepository(
        impl: DecisionSessionRepositoryImpl
    ): DecisionSessionRepository
}
