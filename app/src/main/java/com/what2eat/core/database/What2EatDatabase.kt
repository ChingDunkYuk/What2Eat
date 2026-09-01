package com.what2eat.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
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
import com.what2eat.core.database.entity.DecisionRecommendationEntity
import com.what2eat.core.database.entity.DecisionSessionEntity
import com.what2eat.core.database.entity.FoodCategoryEntity
import com.what2eat.core.database.entity.PersonCategoryPreferenceEntity
import com.what2eat.core.database.entity.PersonOptionPreferenceEntity
import com.what2eat.core.database.entity.PersonProfileEntity
import com.what2eat.core.database.entity.SavedOptionCollectionEntity
import com.what2eat.core.database.entity.SavedOptionEntity
import com.what2eat.core.database.entity.SavedOptionTagEntity
import com.what2eat.core.database.entity.SessionCategorySelectionEntity
import com.what2eat.core.database.entity.SessionParticipantEntity

/**
 * What2Eat Room 数据库。
 *
 * Stage 2.1: 版本升级到 3，新增 decision_session、session_participant、session_category_selection 表。
 * Stage 2.2: 版本升级到 4，decision_session 新增 selectedCategoryId/rerollCount/finalWeight，
 *            新增 decision_recommendation 表。
 * Stage 4:   版本升级到 5，新增吃饭池 4 表：saved_option、saved_option_collection、
 *            saved_option_tag、person_option_preference。
 * v0.7.8:    版本升级到 6，MIGRATION_5_6 清理旧解析缺陷留下的「地址：/电话：」脏名称。
 */
@Database(
    entities = [
        PersonProfileEntity::class,
        FoodCategoryEntity::class,
        PersonCategoryPreferenceEntity::class,
        DecisionSessionEntity::class,
        SessionParticipantEntity::class,
        SessionCategorySelectionEntity::class,
        DecisionRecommendationEntity::class,
        SavedOptionEntity::class,
        SavedOptionCollectionEntity::class,
        SavedOptionTagEntity::class,
        PersonOptionPreferenceEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class What2EatDatabase : RoomDatabase() {

    abstract fun personProfileDao(): PersonProfileDao
    abstract fun foodCategoryDao(): FoodCategoryDao
    abstract fun personCategoryPreferenceDao(): PersonCategoryPreferenceDao
    abstract fun decisionSessionDao(): DecisionSessionDao
    abstract fun sessionParticipantDao(): SessionParticipantDao
    abstract fun sessionCategorySelectionDao(): SessionCategorySelectionDao
    abstract fun decisionRecommendationDao(): DecisionRecommendationDao
    abstract fun savedOptionDao(): SavedOptionDao
    abstract fun savedOptionCollectionDao(): SavedOptionCollectionDao
    abstract fun savedOptionTagDao(): SavedOptionTagDao
    abstract fun personOptionPreferenceDao(): PersonOptionPreferenceDao

    companion object {
        const val DATABASE_NAME = "what2eat.db"
    }
}
