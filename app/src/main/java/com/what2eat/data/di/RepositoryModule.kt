package com.what2eat.data.di

import com.what2eat.data.repository.AppUsageModeRepositoryImpl
import com.what2eat.data.repository.DecisionSessionRepositoryImpl
import com.what2eat.data.repository.FoodCategoryRepositoryImpl
import com.what2eat.data.repository.PersonCategoryPreferenceRepositoryImpl
import com.what2eat.data.repository.PersonProfileRepositoryImpl
import com.what2eat.data.repository.SavedOptionRepositoryImpl
import com.what2eat.data.search.AndroidPlatformSearchLauncher
import com.what2eat.data.search.AndroidSearchLauncher
import com.what2eat.data.share.HttpLinkTitleFetcher
import com.what2eat.data.repository.BackupManagerImpl
import com.what2eat.domain.share.LinkTitleFetcher
import com.what2eat.domain.engine.DecisionEngine
import com.what2eat.domain.engine.DefaultDecisionEngine
import com.what2eat.domain.repository.AppUsageModeRepository
import com.what2eat.domain.repository.BackupManager
import com.what2eat.domain.repository.DecisionSessionRepository
import com.what2eat.domain.repository.FoodCategoryRepository
import com.what2eat.domain.repository.PersonCategoryPreferenceRepository
import com.what2eat.domain.repository.PersonProfileRepository
import com.what2eat.domain.repository.SavedOptionRepository
import com.what2eat.domain.search.PlatformSearchLauncher
import com.what2eat.domain.search.SearchLauncher
import dagger.Binds
import dagger.Module
import dagger.Provides
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

    @Binds
    @Singleton
    abstract fun bindSearchLauncher(
        impl: AndroidSearchLauncher
    ): SearchLauncher

    @Binds
    @Singleton
    abstract fun bindPlatformSearchLauncher(
        impl: AndroidPlatformSearchLauncher
    ): PlatformSearchLauncher

    @Binds
    @Singleton
    abstract fun bindSavedOptionRepository(
        impl: SavedOptionRepositoryImpl
    ): SavedOptionRepository

    @Binds
    @Singleton
    abstract fun bindLinkTitleFetcher(
        impl: HttpLinkTitleFetcher
    ): LinkTitleFetcher

    @Binds
    @Singleton
    abstract fun bindBackupManager(
        impl: BackupManagerImpl
    ): BackupManager

    companion object {
        @Provides
        @Singleton
        fun provideDecisionEngine(): DecisionEngine = DefaultDecisionEngine()
    }
}
