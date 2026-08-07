package com.what2eat.core.database.di

import android.content.Context
import androidx.room.Room
import com.what2eat.core.database.MIGRATION_1_2
import com.what2eat.core.database.What2EatDatabase
import com.what2eat.core.database.dao.FoodCategoryDao
import com.what2eat.core.database.dao.PersonCategoryPreferenceDao
import com.what2eat.core.database.dao.PersonProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据库 Hilt 模块，提供 Database 和 DAO 的单例。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideWhat2EatDatabase(
        @ApplicationContext context: Context
    ): What2EatDatabase {
        return Room.databaseBuilder(
            context,
            What2EatDatabase::class.java,
            What2EatDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    fun providePersonProfileDao(
        database: What2EatDatabase
    ): PersonProfileDao {
        return database.personProfileDao()
    }

    @Provides
    fun provideFoodCategoryDao(
        database: What2EatDatabase
    ): FoodCategoryDao {
        return database.foodCategoryDao()
    }

    @Provides
    fun providePersonCategoryPreferenceDao(
        database: What2EatDatabase
    ): PersonCategoryPreferenceDao {
        return database.personCategoryPreferenceDao()
    }
}
