package com.what2eat.core.database.di

import android.content.Context
import androidx.room.Room
import com.what2eat.core.database.MIGRATION_1_2
import com.what2eat.core.database.MIGRATION_2_3
import com.what2eat.core.database.MIGRATION_3_4
import com.what2eat.core.database.MIGRATION_4_5
import com.what2eat.core.database.MIGRATION_5_6
import com.what2eat.core.database.What2EatDatabase
import com.what2eat.core.database.dao.DecisionRecommendationDao
import com.what2eat.core.database.dao.DecisionSessionDao
import com.what2eat.core.database.dao.FoodCategoryDao
import com.what2eat.core.database.dao.PersonCategoryPreferenceDao
import com.what2eat.core.database.dao.PersonOptionPreferenceDao
import com.what2eat.core.database.dao.PersonProfileDao
import com.what2eat.core.database.dao.SavedOptionCollectionDao
import com.what2eat.core.database.dao.SavedOptionDao
import com.what2eat.core.database.dao.SavedOptionTagDao
import com.what2eat.core.database.dao.SessionCategorySelectionDao
import com.what2eat.core.database.dao.SessionParticipantDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
    }

    @Provides
    fun providePersonProfileDao(database: What2EatDatabase): PersonProfileDao =
        database.personProfileDao()

    @Provides
    fun provideFoodCategoryDao(database: What2EatDatabase): FoodCategoryDao =
        database.foodCategoryDao()

    @Provides
    fun providePersonCategoryPreferenceDao(database: What2EatDatabase): PersonCategoryPreferenceDao =
        database.personCategoryPreferenceDao()

    @Provides
    fun provideDecisionSessionDao(database: What2EatDatabase): DecisionSessionDao =
        database.decisionSessionDao()

    @Provides
    fun provideSessionParticipantDao(database: What2EatDatabase): SessionParticipantDao =
        database.sessionParticipantDao()

    @Provides
    fun provideSessionCategorySelectionDao(database: What2EatDatabase): SessionCategorySelectionDao =
        database.sessionCategorySelectionDao()

    @Provides
    fun provideDecisionRecommendationDao(database: What2EatDatabase): DecisionRecommendationDao =
        database.decisionRecommendationDao()

    @Provides
    fun provideSavedOptionDao(database: What2EatDatabase): SavedOptionDao =
        database.savedOptionDao()

    @Provides
    fun provideSavedOptionCollectionDao(database: What2EatDatabase): SavedOptionCollectionDao =
        database.savedOptionCollectionDao()

    @Provides
    fun provideSavedOptionTagDao(database: What2EatDatabase): SavedOptionTagDao =
        database.savedOptionTagDao()

    @Provides
    fun providePersonOptionPreferenceDao(database: What2EatDatabase): PersonOptionPreferenceDao =
        database.personOptionPreferenceDao()
}
