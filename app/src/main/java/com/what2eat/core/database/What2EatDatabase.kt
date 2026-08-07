package com.what2eat.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.what2eat.core.database.dao.DecisionSessionDao
import com.what2eat.core.database.dao.FoodCategoryDao
import com.what2eat.core.database.dao.PersonCategoryPreferenceDao
import com.what2eat.core.database.dao.PersonProfileDao
import com.what2eat.core.database.dao.SessionCategorySelectionDao
import com.what2eat.core.database.dao.SessionParticipantDao
import com.what2eat.core.database.entity.DecisionSessionEntity
import com.what2eat.core.database.entity.FoodCategoryEntity
import com.what2eat.core.database.entity.PersonCategoryPreferenceEntity
import com.what2eat.core.database.entity.PersonProfileEntity
import com.what2eat.core.database.entity.SessionCategorySelectionEntity
import com.what2eat.core.database.entity.SessionParticipantEntity

/**
 * What2Eat Room 数据库。
 *
 * Stage 2.1: 版本升级到 3，新增 decision_session、session_participant、session_category_selection 表。
 */
@Database(
    entities = [
        PersonProfileEntity::class,
        FoodCategoryEntity::class,
        PersonCategoryPreferenceEntity::class,
        DecisionSessionEntity::class,
        SessionParticipantEntity::class,
        SessionCategorySelectionEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class What2EatDatabase : RoomDatabase() {

    abstract fun personProfileDao(): PersonProfileDao
    abstract fun foodCategoryDao(): FoodCategoryDao
    abstract fun personCategoryPreferenceDao(): PersonCategoryPreferenceDao
    abstract fun decisionSessionDao(): DecisionSessionDao
    abstract fun sessionParticipantDao(): SessionParticipantDao
    abstract fun sessionCategorySelectionDao(): SessionCategorySelectionDao

    companion object {
        const val DATABASE_NAME = "what2eat.db"
    }
}
